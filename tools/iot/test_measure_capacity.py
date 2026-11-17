"""校验容量报告的分位数与订阅分批，防止空样本或丢项被当成通过。"""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('capacity', Path(__file__).with_name('measure-capacity.py'))
capacity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capacity)


class CapacityMetricsTest(unittest.TestCase):
    def test_empty_samples_do_not_claim_zero_latency(self):
        self.assertEqual(capacity.percentiles([]), {'p50': None, 'p95': None, 'p99': None})

    def test_nearest_rank_uses_tail_and_keeps_input_order(self):
        values = list(range(100, 0, -1))
        self.assertEqual(capacity.percentiles(values), {'p50': 50, 'p95': 95, 'p99': 99})
        self.assertEqual(values[0], 100)

    def test_thousand_bindings_cover_each_device_once(self):
        rows = list(range(1000))
        groups = capacity.batches(rows)
        self.assertEqual(len(groups), 50)
        self.assertTrue(all(len(group) <= 20 for group in groups))
        self.assertEqual([row for group in groups for row in group], rows)


if __name__ == '__main__':
    unittest.main()
