"""Real road routing through OpenStreetMap data and the public OSRM demo API."""

from __future__ import annotations

import json
import os
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from domain import utc_now


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
_REQUEST_INTERVAL_SECONDS = 1.1
_request_lock = threading.Lock()
_last_request_at = 0.0


def routing_provider() -> str:
    configured = os.environ.get("MOAGIL_ROUTING_PROVIDER")
    # Keep route scoring on the verified OSRM road matrix by default. NAVER
    # Directions is an opt-in scorer so adding a map key never changes routing.
    provider = configured.strip().lower() if configured else "osrm"
    if provider not in {"naver", "osrm", "mock"}:
        raise RoutingError("경로 제공자 설정이 올바르지 않습니다")
    return provider


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
    return {
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
    return {
        "durationMs": duration_ms,
        "distanceM": distance_m,
        "tollKrw": summary.get("tollFare"),
        "dataSource": NAVER_DATA_SOURCE,
        "fetchedAt": payload.get("currentDateTime") or utc_now(),
        # NAVER Directions path follows GeoJSON-style [longitude, latitude] pairs.
        "geometry": path,
    }


def naver_route_evaluator():
    """Return an evaluator that makes a distinct real traffic query per order."""
    return fetch_naver_route
