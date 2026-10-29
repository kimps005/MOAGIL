import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from domain import RouteError, evaluate_route, plan_routes


def passenger(name, point):
    return {"id": name, "displayName": name, "point": {"lat": 0, "lon": point, "label": name}}


class RoutePlanningTests(unittest.TestCase):
    def setUp(self):
        self.start = {"lat": 0, "lon": 0, "label": "S"}
        self.people = [passenger(name, 0) for name in "ABCD"]
        self.matrix = {
            ("S", "A"): 1, ("S", "B"): 4, ("S", "C"): 7, ("S", "D"): 9, ("S", "F"): 9,
            ("A", "B"): 1, ("A", "C"): 7, ("A", "D"): 7, ("A", "F"): 9,
            ("B", "C"): 1, ("B", "D"): 7, ("B", "F"): 9,
            ("C", "D"): 1, ("C", "F"): 9, ("D", "F"): 1,
        }

    def fake(self, points):
        labels = [point["label"] for point in points]
        cost = sum(self.matrix.get((left, right), 9) for left, right in zip(labels, labels[1:]))
        return {"durationMs": cost * 60_000, "distanceM": cost * 100, "tollKrw": None, "dataSource": "test-matrix", "fetchedAt": "2026-01-01T00:00:00Z"}

    def test_four_passengers_evaluates_all_permutations_and_finds_unique_route(self):
        result = plan_routes(self.start, self.people, {"lat": 0, "lon": 1, "label": "F"}, evaluator=self.fake)
        self.assertEqual(24, len(result))
        self.assertEqual(["A", "B", "C", "D"], result[0]["order"])
        self.assertEqual(5 * 60_000, result[0]["durationMs"])

    def test_priority_reduces_four_person_comparison_to_six(self):
        result = plan_routes(self.start, self.people, {"lat": 0, "lon": 1, "label": "F"}, "B", self.fake)
        self.assertEqual(6, len(result))
        self.assertEqual(["B", "A", "C", "D"], result[0]["order"])
        self.assertEqual(22 * 60_000, result[0]["durationMs"])

    def test_same_coordinate_people_are_distinct_visitors(self):
        result = plan_routes(self.start, [passenger("A", 0), passenger("B", 0)])
        self.assertEqual(2, len(result))
        self.assertEqual(0, result[0]["distanceM"])

    def test_rejects_zero_or_more_than_four_people(self):
        with self.assertRaises(RouteError):
            plan_routes(self.start, [])
        with self.assertRaises(RouteError):
            plan_routes(self.start, self.people + [passenger("E", 0)])

    def test_mock_estimate_discloses_source_and_unknown_toll(self):
        result = evaluate_route([self.start, {"lat": 0, "lon": 0.01, "label": "A"}])
        self.assertEqual("mock-straight-line", result["dataSource"])
        self.assertEqual(1_390, result["distanceM"])
        self.assertIsNone(result["tollKrw"])


if __name__ == "__main__":
    unittest.main()
