import sys
import unittest
from pathlib import Path
from unittest.mock import call, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from routing import KakaoMatrixEvaluator, RoutingError, fetch_kakao_route


class KakaoMatrixEvaluatorTests(unittest.TestCase):
    def test_pairs_outside_multi_origin_radius_use_full_kakao_directions(self):
        points = [
            {"lat": 37.5665, "lon": 126.9780, "label": "출발"},
            {"lat": 37.3627, "lon": 126.7394, "label": "오이도역"},
        ]
        matrix_responses = [
            {"routes": [{"key": "1", "result_code": 207}]},
            {"routes": [{"key": "0", "result_code": 207}]},
        ]
        route_responses = [
            {"durationMs": 4_591_000, "distanceM": 40_673},
            {"durationMs": 4_823_000, "distanceM": 41_102},
        ]
        with patch("routing._kakao_request_json", side_effect=matrix_responses), patch(
            "routing.fetch_kakao_route", side_effect=route_responses
        ) as fetch_route:
            evaluator = KakaoMatrixEvaluator(points)
            route = evaluator(points)

        self.assertEqual(route["durationMs"], 4_591_000)
        self.assertEqual(route["distanceM"], 40_673)
        self.assertEqual(route["legDurationsMs"], [4_591_000])
        self.assertEqual(evaluator.duration_ms[1][0], 4_823_000)
        self.assertEqual(
            fetch_route.call_args_list,
            [
                call([points[0], points[1]], avoid_toll=False),
                call([points[1], points[0]], avoid_toll=False),
            ],
        )

    def test_toll_avoidance_is_sent_to_multi_origin_as_an_array(self):
        points = [
            {"lat": 37.5665, "lon": 126.9780, "label": "출발"},
            {"lat": 37.5700, "lon": 126.9900, "label": "도착"},
        ]
        matrix_responses = [
            {"routes": [{"key": "1", "result_code": 0, "summary": {"duration": 120, "distance": 1500}}]},
            {"routes": [{"key": "0", "result_code": 0, "summary": {"duration": 130, "distance": 1600}}]},
        ]

        with patch("routing._kakao_request_json", side_effect=matrix_responses) as request:
            evaluator = KakaoMatrixEvaluator(points, avoid_toll=True)

        self.assertEqual(len(request.call_args_list), 2)
        for request_call in request.call_args_list:
            self.assertEqual(request_call.args[1]["avoid"], ["toll"])
        self.assertEqual(evaluator.duration_ms[0][1], 130_000)


class KakaoRouteAvoidTollTests(unittest.TestCase):
    points = [
        {"lat": 37.5665, "lon": 126.9780, "label": "출발"},
        {"lat": 37.5700, "lon": 126.9900, "label": "도착"},
    ]

    def response(self, toll):
        return {
            "routes": [{
                "result_code": 0,
                "summary": {"duration": 120, "distance": 1500, "fare": {"toll": toll}},
                "sections": [{
                    "duration": 120,
                    "roads": [{"vertexes": [126.978, 37.5665, 126.99, 37.57]}],
                }],
            }]
        }

    def test_toll_avoidance_is_sent_to_waypoints_as_an_array(self):
        with patch("routing._kakao_request_json", return_value=self.response(0)) as request:
            route = fetch_kakao_route(self.points, avoid_toll=True)

        self.assertEqual(request.call_args.args[1]["avoid"], ["toll"])
        self.assertEqual(route["tollKrw"], 0)

    def test_toll_avoidance_rejects_a_route_with_a_nonzero_toll(self):
        with patch("routing._kakao_request_json", return_value=self.response(1200)):
            with self.assertRaisesRegex(RoutingError, "통행료 0원"):
                fetch_kakao_route(self.points, avoid_toll=True)


if __name__ == "__main__":
    unittest.main()
