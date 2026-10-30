"""Pure route-planning rules for the MOAGIL mock prototype."""

from __future__ import annotations

import concurrent.futures
from datetime import datetime, timezone
from itertools import permutations
from math import asin, cos, radians, sin, sqrt
from typing import Callable, Iterable

EARTH_RADIUS_M = 6_371_000


class RouteError(ValueError):
    pass


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def validate_point(point: object, field: str = "point") -> dict:
    if not isinstance(point, dict):
        raise RouteError(field + " must be an object")
    try:
        lat = float(point["lat"])
        lon = float(point["lon"])
    except (KeyError, TypeError, ValueError) as exc:
        raise RouteError(field + " requires numeric lat and lon") from exc
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise RouteError(field + " is outside valid WGS84 coordinates")
    label = str(point.get("label", "시연 지점")).strip()[:80] or "시연 지점"
    return {"lat": round(lat, 6), "lon": round(lon, 6), "label": label}


def haversine_m(a: dict, b: dict) -> float:
    lat1, lon1 = radians(a["lat"]), radians(a["lon"])
    lat2, lon2 = radians(b["lat"]), radians(b["lon"])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = sin(dlat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, h)))


def evaluate_route(points: Iterable[dict]) -> dict:
    points = list(points)
    meters = round(sum(haversine_m(a, b) for a, b in zip(points, points[1:])) * 1.25)
    duration_ms = round(meters / (30_000 / 3600) * 1000)
    return {
        "durationMs": duration_ms,
        "distanceM": meters,
        "tollKrw": None,
        "dataSource": "mock-straight-line",
        "fetchedAt": utc_now(),
    }


def plan_routes(
    start: dict,
    passengers: list[dict],
    final_point: dict | None = None,
    priority_id: str | None = None,
    evaluator: Callable[[list[dict]], dict] = evaluate_route,
    max_workers: int = 1,
    timeout_seconds: float | None = None,
) -> list[dict]:
    if not 1 <= len(passengers) <= 4:
        raise RouteError("경로 계산은 동승자 1~4명일 때 가능합니다")
    ids = [p["id"] for p in passengers]
    if len(set(ids)) != len(ids):
        raise RouteError("동승자 ID는 서로 달라야 합니다")
    by_id = {p["id"]: p for p in passengers}
    if priority_id and priority_id not in by_id:
        raise RouteError("먼저 내릴 동승자를 찾을 수 없습니다")
    remaining = [p for p in passengers if p["id"] != priority_id]
    orders = (
        [[by_id[priority_id], *tail] for tail in permutations(remaining)]
        if priority_id
        else [list(order) for order in permutations(passengers)]
    )
    def score(order):
        points = [start, *[p["point"] for p in order]]
        if final_point:
            points.append(final_point)
        estimate = evaluator(points)
        if not isinstance(estimate.get("durationMs"), int) or estimate["durationMs"] < 0:
            raise RouteError("경로 제공자 응답이 올바르지 않습니다")
        distance = estimate.get("distanceM")
        return {
            "order": [p["id"] for p in order],
            "durationMs": estimate["durationMs"],
            "distanceM": distance,
            "tollKrw": estimate.get("tollKrw"),
            "dataSource": estimate.get("dataSource", "unknown"),
            "fetchedAt": estimate.get("fetchedAt", utc_now()),
            "geometry": estimate.get("geometry"),
            "snappedWaypoints": estimate.get("snappedWaypoints"),
        }

    if max_workers <= 1:
        results = [score(order) for order in orders]
    else:
        executor = concurrent.futures.ThreadPoolExecutor(max_workers=max_workers)
        futures = [executor.submit(score, order) for order in orders]
        _done, pending = concurrent.futures.wait(futures, timeout=timeout_seconds)
        if pending:
            for future in pending:
                future.cancel()
            executor.shutdown(wait=False, cancel_futures=True)
            raise RouteError("경로 비교가 제한 시간 120초를 넘었습니다. 잠시 뒤 다시 시도해 주세요")
        try:
            results = [future.result() for future in futures]
        finally:
            executor.shutdown(wait=True)
    return sorted(
        results,
        key=lambda item: (
            item["durationMs"],
            item["distanceM"] is None,
            item["distanceM"] if item["distanceM"] is not None else 0,
            item["order"],
        ),
    )
