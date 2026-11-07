"""Kakao Mobility road routing for MOAGIL."""

from __future__ import annotations

import json
import os
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from domain import utc_now


class RoutingError(RuntimeError):
    """Raised when Kakao Mobility cannot provide a road route."""


KAKAO_ORIGINS_DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/origins/directions"
KAKAO_WAYPOINTS_DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/waypoints/directions"
KAKAO_PROVIDER_VERSION = "kakao-mobility-directions-v1"
KAKAO_DATA_SOURCE = "kakao-mobility-directions"
KAKAO_ATTRIBUTION = "© Kakao Mobility · 카카오내비 실시간 경로"
KAKAO_NOTE = "카카오내비 실시간 교통 기반 자동차 경로"


def routing_provider() -> str:
    """Return the sole supported live road-routing provider."""
    return "kakao"


def _kakao_rest_api_key() -> str:
    value = os.environ.get("MOAGIL_KAKAO_REST_API_KEY", "").strip()
    if not value:
        raise RoutingError("카카오내비 길찾기를 사용하려면 .env에 MOAGIL_KAKAO_REST_API_KEY를 설정해 주세요")
    return value


def _kakao_request_json(url: str, body: dict) -> dict:
    request = Request(
        url,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Authorization": f"KakaoAK {_kakao_rest_api_key()}",
            "User-Agent": "MOAGIL/1.0 (+https://github.com/kimps005/MOAGIL)",
        },
        method="POST",
    )
    try:
        with urlopen(request, timeout=12) as response:
            payload = json.loads(response.read(8 * 1024 * 1024).decode("utf-8"))
    except HTTPError as error:
        if error.code in (401, 403):
            raise RoutingError("카카오내비 인증에 실패했습니다. REST 키와 Kakao Mobility 길찾기 API 사용 설정을 확인해 주세요") from None
        if error.code == 429:
            raise RoutingError("카카오내비 길찾기 사용 한도에 도달했습니다. 한도 초기화 후 다시 시도해 주세요") from None
        if error.code >= 500:
            raise RoutingError("카카오내비 길찾기 서버가 응답하지 않습니다. 잠시 뒤 다시 시도해 주세요") from None
        raise RoutingError("카카오내비가 길찾기 요청을 처리하지 못했습니다") from None
    except (URLError, TimeoutError, OSError, ValueError):
        raise RoutingError("카카오내비 길찾기 서버에 연결할 수 없습니다. 네트워크를 확인하고 다시 시도해 주세요") from None
    if not isinstance(payload, dict):
        raise RoutingError("카카오내비 길찾기 응답을 읽을 수 없습니다")
    return payload


def _kakao_point(point: dict) -> dict:
    return {
        "x": round(float(point["lon"]), 6),
        "y": round(float(point["lat"]), 6),
    }


class KakaoMatrixEvaluator:
    """Fetch a live Kakao time/distance matrix, then compare all stop orders locally."""

    def __init__(self, points: list[dict], avoid_toll: bool = False):
        if len(points) < 2:
            raise RoutingError("실제 경로에는 출발지와 하차 지점이 필요합니다")
        if len(points) > 30:
            raise RoutingError("카카오내비 경로 비교에는 위치를 30개 이하로 지정해 주세요")
        self.points = points
        self.avoid_toll = avoid_toll
        count = len(points)
        self.duration_ms: list[list[int | None]] = [[None] * count for _ in range(count)]
        self.distance_m: list[list[int | None]] = [[None] * count for _ in range(count)]
        for index in range(count):
            self.duration_ms[index][index] = 0
            self.distance_m[index][index] = 0

        # Kakao's multi-origin endpoint returns live time and distance for up
        # to 30 origins at once. One request per destination is much cheaper
        # than querying every possible passenger order separately.
        for destination_index, destination in enumerate(points):
            origins = []
            for origin_index, origin in enumerate(points):
                if origin_index == destination_index:
                    continue
                if self._same_point(origin, destination):
                    self.duration_ms[origin_index][destination_index] = 0
                    self.distance_m[origin_index][destination_index] = 0
                    continue
                origins.append({**_kakao_point(origin), "key": str(origin_index)})
            if not origins:
                continue
            request_body = {
                "origins": origins,
                "destination": _kakao_point(destination),
                "radius": 10000,
                "priority": "TIME",
            }
            if avoid_toll:
                request_body["avoid"] = ["toll"]
            payload = _kakao_request_json(KAKAO_ORIGINS_DIRECTIONS_URL, request_body)
            routes = payload.get("routes")
            if not isinstance(routes, list):
                raise RoutingError("카카오내비가 이동시간표를 반환하지 않았습니다")
            for route in routes:
                if not isinstance(route, dict):
                    continue
                if route.get("result_code") != 0:
                    continue
                try:
                    origin_index = int(route["key"])
                    summary = route["summary"]
                    duration = round(float(summary["duration"]) * 1000)
                    distance = round(float(summary["distance"]))
                except (KeyError, TypeError, ValueError, OverflowError):
                    continue
                if 0 <= origin_index < count and duration >= 0 and distance >= 0:
                    self.duration_ms[origin_index][destination_index] = duration
                    self.distance_m[origin_index][destination_index] = distance

        # Multi-origin search has a 10 km radius limit. Routes outside that
        # area return a non-zero result for the origin, so complete just those
        # matrix cells with the full directions endpoint before scoring orders.
        for origin_index in range(count):
            for destination_index in range(count):
                if origin_index == destination_index or self.duration_ms[origin_index][destination_index] is not None:
                    continue
                route = fetch_kakao_route(
                    [points[origin_index], points[destination_index]],
                    avoid_toll=self.avoid_toll,
                )
                self.duration_ms[origin_index][destination_index] = route["durationMs"]
                self.distance_m[origin_index][destination_index] = route["distanceM"]
        self.fetched_at = utc_now()

    @staticmethod
    def _same_point(left: dict, right: dict) -> bool:
        return (
            round(float(left["lat"]), 6) == round(float(right["lat"]), 6)
            and round(float(left["lon"]), 6) == round(float(right["lon"]), 6)
        )

    def __call__(self, route_points: list[dict]) -> dict:
        indices_by_coordinate: dict[tuple[float, float], list[int]] = {}
        for index, point in enumerate(self.points):
            coordinate = (round(float(point["lat"]), 6), round(float(point["lon"]), 6))
            indices_by_coordinate.setdefault(coordinate, []).append(index)

        route_indices = []
        used_occurrences: dict[tuple[float, float], int] = {}
        for point in route_points:
            coordinate = (round(float(point["lat"]), 6), round(float(point["lon"]), 6))
            candidates = indices_by_coordinate.get(coordinate)
            if not candidates:
                raise RoutingError("카카오내비 이동시간표에서 위치를 확인할 수 없습니다")
            occurrence = used_occurrences.get(coordinate, 0)
            route_indices.append(candidates[min(occurrence, len(candidates) - 1)])
            used_occurrences[coordinate] = occurrence + 1

        leg_times = [self.duration_ms[left][right] for left, right in zip(route_indices, route_indices[1:])]
        leg_distances = [self.distance_m[left][right] for left, right in zip(route_indices, route_indices[1:])]
        if any(value is None for value in leg_times + leg_distances):
            raise RoutingError("일부 위치 사이의 카카오내비 실시간 경로를 찾지 못했습니다. 핀을 도로 가까이 옮겨 주세요")
        return {
            "durationMs": sum(leg_times),
            "distanceM": sum(leg_distances),
            "legDurationsMs": leg_times,
            "tollKrw": None,
            "dataSource": KAKAO_DATA_SOURCE,
            "fetchedAt": self.fetched_at,
        }

    def snapshot(self) -> dict:
        return {
            "points": [
                {"lat": round(float(point["lat"]), 6), "lon": round(float(point["lon"]), 6), "label": point.get("label", "")}
                for point in self.points
            ],
            "durationMs": self.duration_ms,
            "distanceM": self.distance_m,
            "fetchedAt": self.fetched_at,
        }


def fetch_kakao_route(points: list[dict], avoid_toll: bool = False) -> dict:
    """Fetch a full Kakao route with real-time traffic and road geometry."""
    if len(points) < 2:
        raise RoutingError("실제 경로에는 출발지와 하차 지점이 필요합니다")
    if len(points) > 32:
        raise RoutingError("카카오내비 경로 요청은 경유지 30개 이하로 지정해 주세요")
    request_body = {
        "origin": _kakao_point(points[0]),
        "destination": _kakao_point(points[-1]),
        "waypoints": [_kakao_point(point) for point in points[1:-1]],
        "priority": "TIME",
        "car_fuel": "GASOLINE",
        "car_hipass": False,
        "alternatives": False,
        "road_details": False,
        "summary": False,
    }
    if avoid_toll:
        request_body["avoid"] = ["toll"]
    payload = _kakao_request_json(KAKAO_WAYPOINTS_DIRECTIONS_URL, request_body)
    routes = payload.get("routes")
    if not isinstance(routes, list) or not routes or not isinstance(routes[0], dict):
        raise RoutingError("카카오내비가 선택한 순서의 실제 경로를 찾지 못했습니다")
    route = routes[0]
    if route.get("result_code") != 0:
        raise RoutingError("카카오내비에서 선택한 순서의 경로를 찾지 못했습니다. 핀 위치를 확인해 주세요")
    summary = route.get("summary")
    sections = route.get("sections")
    if not isinstance(summary, dict) or not isinstance(sections, list):
        raise RoutingError("카카오내비 경로 응답에 시간·구간 정보가 없습니다")
    try:
        duration_ms = round(float(summary["duration"]) * 1000)
        distance_m = round(float(summary["distance"]))
    except (KeyError, TypeError, ValueError, OverflowError):
        raise RoutingError("카카오내비 경로 응답에서 시간·거리 정보를 읽을 수 없습니다") from None
    geometry: list[list[float]] = []
    leg_durations: list[int] = []
    for section in sections:
        if not isinstance(section, dict):
            continue
        try:
            leg_durations.append(round(float(section["duration"]) * 1000))
        except (KeyError, TypeError, ValueError, OverflowError):
            leg_durations = []
        for road in section.get("roads", []):
            if not isinstance(road, dict):
                continue
            vertexes = road.get("vertexes")
            if not isinstance(vertexes, list):
                continue
            for index in range(0, len(vertexes) - 1, 2):
                try:
                    coordinate = [round(float(vertexes[index]), 6), round(float(vertexes[index + 1]), 6)]
                except (TypeError, ValueError, OverflowError):
                    continue
                if not geometry or coordinate != geometry[-1]:
                    geometry.append(coordinate)
    if duration_ms < 0 or distance_m < 0 or len(geometry) < 2:
        raise RoutingError("카카오내비 경로 지도선을 만들 수 없습니다")
    fare = summary.get("fare")
    toll_krw = fare.get("toll") if isinstance(fare, dict) else None
    if avoid_toll and toll_krw != 0:
        raise RoutingError("카카오내비가 통행료 0원의 무료 도로 경로를 반환하지 않았습니다. 다른 경로를 선택해 주세요")
    result = {
        "durationMs": duration_ms,
        "distanceM": distance_m,
        "tollKrw": toll_krw,
        "dataSource": KAKAO_DATA_SOURCE,
        "fetchedAt": utc_now(),
        "geometry": geometry,
        "snappedWaypoints": [],
    }
    if len(leg_durations) == len(points) - 1 and all(value >= 0 for value in leg_durations):
        result["legDurationsMs"] = leg_durations
    return result
