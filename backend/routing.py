"""Kakao Mobility road routing for MOAGIL."""

from __future__ import annotations

import json
import os
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from domain import haversine_m, utc_now


class RoutingError(RuntimeError):
    """Raised when the configured road-routing service cannot provide a route."""


OSRM_DEFAULT_URL = "https://router.project-osrm.org"
OSRM_PROVIDER_VERSION = "osrm-openstreetmap-v1"
OSRM_DATA_SOURCE = "openstreetmap-osrm"
OSRM_ATTRIBUTION = "© OpenStreetMap contributors · 경로 계산 OSRM"
OSRM_NOTE = "OSM 실제 도로망 기반 차량 경로 · 실시간 교통 미포함"
NAVER_DIRECTIONS_URL = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving"
NAVER_PROVIDER_VERSION = "naver-directions5-trafast-v1"
NAVER_DATA_SOURCE = "naver-directions5"
NAVER_ATTRIBUTION = "© NAVER 지도 · Directions 5"
NAVER_NOTE = "네이버 실제 도로 경로 · 실시간 교통 반영"
KAKAO_ORIGINS_DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/origins/directions"
KAKAO_WAYPOINTS_DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/waypoints/directions"
KAKAO_PROVIDER_VERSION = "kakao-mobility-directions-v1"
KAKAO_DATA_SOURCE = "kakao-mobility-directions"
KAKAO_ATTRIBUTION = "© Kakao Mobility · 카카오내비 실시간 경로"
KAKAO_NOTE = "카카오내비 실시간 교통 기반 자동차 경로"
_REQUEST_INTERVAL_SECONDS = 1.1
_request_lock = threading.Lock()
_last_request_at = 0.0


def routing_provider() -> str:
    # Keep the legacy setting harmless, but always use Kakao Mobility. A
    # missing key should produce Kakao's configuration error, never another map.
    return "kakao"


def _base_url() -> str:
    value = os.environ.get("MOAGIL_OSRM_BASE_URL", OSRM_DEFAULT_URL).strip().rstrip("/")
    if not value.startswith("https://"):
        raise RoutingError("실제 경로 서버 주소는 HTTPS여야 합니다")
    return value


def _request_json(path: str, query: dict[str, str]) -> dict:
    global _last_request_at
    url = _base_url() + path + "?" + urlencode(query)
    request = Request(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "MOAGIL/1.0 (+https://github.com/kimps005/MOAGIL)",
            "Referer": "https://github.com/kimps005/MOAGIL",
        },
    )
    # The public OSRM demo asks consumers not to exceed one request per second.
    # Serializing requests also prevents bursty clicks from hitting that limit.
    with _request_lock:
        wait_for = _REQUEST_INTERVAL_SECONDS - (time.monotonic() - _last_request_at)
        if wait_for > 0:
            time.sleep(wait_for)
        try:
            with urlopen(request, timeout=18) as response:
                payload = json.loads(response.read(8 * 1024 * 1024).decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, OSError, ValueError):
            _last_request_at = time.monotonic()
            raise RoutingError("무료 도로 경로 서버에 연결할 수 없습니다. 잠시 뒤 다시 시도해 주세요") from None
        _last_request_at = time.monotonic()
    if not isinstance(payload, dict) or payload.get("code") != "Ok":
        raise RoutingError("입력 위치를 연결하는 실제 도로 경로를 찾지 못했습니다. 핀을 도로 가까이 옮겨 주세요")
    return payload


def _coordinates(points: list[dict]) -> str:
    if len(points) < 2:
        raise RoutingError("실제 경로에는 출발지와 하차 지점이 필요합니다")
    return ";".join(f"{float(point['lon']):.6f},{float(point['lat']):.6f}" for point in points)


def matrix_evaluator(points: list[dict]):
    """Fetch one road-time matrix, then score every permutation without more API calls."""
    coordinates = _coordinates(points)
    payload = _request_json(
        "/table/v1/driving/" + coordinates,
        {"annotations": "duration,distance"},
    )
    durations = payload.get("durations")
    distances = payload.get("distances")
    count = len(points)
    if (
        not isinstance(durations, list)
        or not isinstance(distances, list)
        or len(durations) != count
        or len(distances) != count
        or any(not isinstance(row, list) or len(row) != count for row in durations + distances)
    ):
        raise RoutingError("실제 도로 경로 서버가 거리표를 올바르게 반환하지 않았습니다")

    indices: dict[tuple[float, float], int] = {}
    for index, point in enumerate(points):
        indices.setdefault((round(float(point["lat"]), 6), round(float(point["lon"]), 6)), index)

    def evaluate(route_points: list[dict]) -> dict:
        try:
            route_indices = [indices[(round(float(point["lat"]), 6), round(float(point["lon"]), 6))] for point in route_points]
            seconds = [durations[left][right] for left, right in zip(route_indices, route_indices[1:])]
            meters = [distances[left][right] for left, right in zip(route_indices, route_indices[1:])]
        except (KeyError, IndexError, TypeError):
            raise RoutingError("거리표에서 위치를 확인할 수 없습니다") from None
        if any(value is None or not isinstance(value, (int, float)) for value in seconds + meters):
            raise RoutingError("일부 위치 사이의 차량 도로 경로를 찾지 못했습니다. 핀을 도로 가까이 옮겨 주세요")
        return {
            "durationMs": round(sum(seconds) * 1000),
            "distanceM": round(sum(meters)),
            "legDurationsMs": [round(value * 1000) for value in seconds],
            "tollKrw": None,
            "dataSource": OSRM_DATA_SOURCE,
            "fetchedAt": utc_now(),
        }

    return evaluate


def fetch_route(points: list[dict]) -> dict:
    payload = _request_json(
        "/route/v1/driving/" + _coordinates(points),
        {"overview": "full", "geometries": "geojson", "steps": "false"},
    )
    routes = payload.get("routes")
    if not isinstance(routes, list) or not routes or not isinstance(routes[0], dict):
        raise RoutingError("선택한 순서의 실제 도로 경로를 찾지 못했습니다")
    route = routes[0]
    geometry = route.get("geometry", {}).get("coordinates")
    if (
        not isinstance(geometry, list)
        or len(geometry) < 2
        or any(not isinstance(point, list) or len(point) < 2 for point in geometry)
    ):
        raise RoutingError("실제 도로 경로의 지도 선을 만들 수 없습니다")
    legs = route.get("legs")
    leg_durations = None
    if isinstance(legs, list) and len(legs) == len(points) - 1:
        try:
            candidate = [round(float(leg["duration"]) * 1000) for leg in legs]
            if all(duration >= 0 for duration in candidate):
                leg_durations = candidate
        except (KeyError, TypeError, ValueError, OverflowError):
            pass
    result = {
        "durationMs": round(float(route["duration"]) * 1000),
        "distanceM": round(float(route["distance"])),
        "tollKrw": None,
        "dataSource": OSRM_DATA_SOURCE,
        "fetchedAt": utc_now(),
        # GeoJSON order is [longitude, latitude].
        "geometry": [[round(float(point[0]), 6), round(float(point[1]), 6)] for point in geometry],
        "snappedWaypoints": [
            {
                "lat": round(float(item["location"][1]), 6),
                "lon": round(float(item["location"][0]), 6),
                "distanceM": round(float(item.get("distance", 0))),
            }
            for item in payload.get("waypoints", [])
            if isinstance(item, dict) and isinstance(item.get("location"), list) and len(item["location"]) >= 2
        ],
    }
    if leg_durations is not None:
        result["legDurationsMs"] = leg_durations
    return result


def _leg_durations_from_path(points: list[dict], path: list[list[float]], duration_ms: int) -> list[int] | None:
    if len(points) < 2 or len(path) < 2 or duration_ms < 0:
        return None
    path_points = [{"lon": coordinate[0], "lat": coordinate[1]} for coordinate in path]
    cumulative = [0.0]
    for left, right in zip(path_points, path_points[1:]):
        cumulative.append(cumulative[-1] + haversine_m(left, right))
    total_distance = cumulative[-1]
    if total_distance <= 0:
        return None

    waypoint_indices = [0]
    previous_index = 0
    for waypoint in points[1:-1]:
        nearest_index = min(
            range(previous_index, len(path_points)),
            key=lambda index: haversine_m(waypoint, path_points[index]),
        )
        waypoint_indices.append(nearest_index)
        previous_index = nearest_index
    waypoint_indices.append(len(path_points) - 1)

    leg_distances = [cumulative[right] - cumulative[left] for left, right in zip(waypoint_indices, waypoint_indices[1:])]
    if any(distance < 0 for distance in leg_distances):
        return None
    durations = [round(duration_ms * distance / total_distance) for distance in leg_distances[:-1]]
    durations.append(max(0, duration_ms - sum(durations)))
    return durations


def _naver_credentials() -> tuple[str, str]:
    client_id = os.environ.get("MOAGIL_NAVER_CLIENT_ID", "").strip()
    client_secret = os.environ.get("MOAGIL_NAVER_CLIENT_SECRET", "").strip()
    if not client_id or not client_secret:
        raise RoutingError("네이버 경로 연동에 Client ID와 Client Secret이 필요합니다. 서버의 .env 파일에 설정해 주세요")
    return client_id, client_secret


def _naver_request_json(query: dict[str, str]) -> dict:
    client_id, client_secret = _naver_credentials()
    request = Request(
        NAVER_DIRECTIONS_URL + "?" + urlencode(query),
        headers={
            "Accept": "application/json",
            "User-Agent": "MOAGIL/1.0 (+https://github.com/kimps005/MOAGIL)",
            "x-ncp-apigw-api-key-id": client_id,
            "x-ncp-apigw-api-key": client_secret,
        },
    )
    for attempt in range(2):
        try:
            with urlopen(request, timeout=8) as response:
                payload = json.loads(response.read(8 * 1024 * 1024).decode("utf-8"))
            break
        except HTTPError as error:
            if attempt == 0 and (error.code == 429 or 500 <= error.code <= 599):
                time.sleep(0.5)
                continue
            if error.code in (401, 403):
                raise RoutingError("네이버 경로 인증에 실패했습니다. Maps 앱의 API 선택과 서버 인증 키를 확인해 주세요") from None
            if error.code == 429:
                raise RoutingError("네이버 경로 호출 한도에 도달했습니다. 잠시 후 다시 시도해 주세요") from None
            raise RoutingError("네이버 경로 서버가 요청을 처리하지 못했습니다") from None
        except (URLError, TimeoutError, OSError, ValueError):
            raise RoutingError("네이버 Directions 서버에 연결할 수 없습니다. 잠시 뒤 다시 시도해 주세요") from None
    if not isinstance(payload, dict):
        raise RoutingError("네이버 경로 서버 응답을 읽을 수 없습니다")
    if payload.get("code") != 0:
        code = payload.get("code")
        if code in (1, 2, 4):
            raise RoutingError("출발·하차 핀이 도로와 너무 멀거나 서로 같은 위치입니다. 지도에서 도로 가까운 곳을 선택해 주세요")
        if code == 5:
            raise RoutingError("입력한 경유지를 포함한 경로가 네이버 Directions 5 범위를 벗어났습니다")
        raise RoutingError("네이버에서 실제 자동차 경로를 찾지 못했습니다")
    return payload


def fetch_naver_route(points: list[dict]) -> dict:
    """Fetch one ordered NAVER Directions 5 route, including its road geometry."""
    if len(points) < 2:
        raise RoutingError("실제 경로에는 출발지와 하차 지점이 필요합니다")
    start = points[0]
    goal = points[-1]
    query = {
        "start": f"{float(start['lon']):.6f},{float(start['lat']):.6f}",
        "goal": f"{float(goal['lon']):.6f},{float(goal['lat']):.6f}",
        "option": "trafast",
        "cartype": "1",
        "lang": "ko",
    }
    if len(points) > 2:
        query["waypoints"] = "|".join(
            f"{float(point['lon']):.6f},{float(point['lat']):.6f}" for point in points[1:-1]
        )
    payload = _naver_request_json(query)
    options = payload.get("route", {}).get("trafast")
    if not isinstance(options, list) or not options or not isinstance(options[0], dict):
        raise RoutingError("네이버 Directions가 요청한 빠른 경로를 반환하지 않았습니다")
    route = options[0]
    summary = route.get("summary", {})
    geometry = route.get("path")
    if not isinstance(geometry, list) or len(geometry) < 2:
        raise RoutingError("네이버 실제 도로 경로의 지도 선을 만들 수 없습니다")
    try:
        duration_ms = round(float(summary["duration"]))
        distance_m = round(float(summary["distance"]))
        path = [[round(float(point[0]), 6), round(float(point[1]), 6)] for point in geometry if isinstance(point, list) and len(point) >= 2]
    except (KeyError, TypeError, ValueError, IndexError):
        raise RoutingError("네이버 Directions 응답에서 시간·거리 정보를 읽을 수 없습니다") from None
    if len(path) < 2 or duration_ms < 0 or distance_m < 0:
        raise RoutingError("네이버 Directions 경로 정보가 올바르지 않습니다")
    result = {
        "durationMs": duration_ms,
        "distanceM": distance_m,
        "tollKrw": summary.get("tollFare"),
        "dataSource": NAVER_DATA_SOURCE,
        "fetchedAt": payload.get("currentDateTime") or utc_now(),
        # NAVER Directions path follows GeoJSON-style [longitude, latitude] pairs.
        "geometry": path,
    }
    leg_durations = _leg_durations_from_path(points, path, duration_ms)
    if leg_durations is not None:
        result["legDurationsMs"] = leg_durations
        result["legDurationsApproximate"] = True
    return result


def naver_route_evaluator():
    """Return an evaluator that makes a distinct real traffic query per order."""
    return fetch_naver_route


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
        request_body["avoid"] = "toll"
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
    result = {
        "durationMs": duration_ms,
        "distanceM": distance_m,
        "tollKrw": (summary.get("fare") or {}).get("toll") if isinstance(summary.get("fare"), dict) else None,
        "dataSource": KAKAO_DATA_SOURCE,
        "fetchedAt": utc_now(),
        "geometry": geometry,
        "snappedWaypoints": [],
    }
    if len(leg_durations) == len(points) - 1 and all(value >= 0 for value in leg_durations):
        result["legDurationsMs"] = leg_durations
    return result
