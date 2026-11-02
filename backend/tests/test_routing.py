import sys
import unittest
from pathlib import Path
from unittest.mock import call, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from routing import KakaoMatrixEvaluator


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
            [call([points[0], points[1]]), call([points[1], points[0]])],
        )


if __name__ == "__main__":
    unittest.main()
