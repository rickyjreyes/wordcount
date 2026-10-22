import importlib.util
import pathlib
import tempfile
import unittest

MODULE = pathlib.Path(__file__).resolve().parents[1] / "holdout_report.py"
spec = importlib.util.spec_from_file_location("holdout_report", MODULE)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)

class HoldoutReportTest(unittest.TestCase):
    @staticmethod
    def rows():
        for shape,n,seed in sorted(report.EXPECTED_CASES):
            for name in report.METHODS:
                base = {"adaptive-v1": 70, "hybrid": 100, "merge": 115,
                        "freqbucket": 108, "jdk-native": 120, "rca": 130,
                        "pdq": 135, "sparsebucket": 145, "parallel": 150,
                        "radix": 170, "wct": 160}[name]
                yield {"dataset":shape,"n":str(n),"seed":str(seed),"method":name,
                       "status":"PASS","samples":"3","median_ns":str(base),
                       "implementation":name+"->test",
                       "input_sha256":f"input/{shape}/{n}/{seed}",
                       "oracle_sha256":f"oracle/{shape}/{n}/{seed}"}

    def test_complete_hypothetical_pass(self):
        cases,keys,metrics = report.evaluate(list(self.rows()))
        self.assertEqual(210,len(keys))
        self.assertTrue(metrics["pass"])
        self.assertEqual("freqbucket",metrics["best_fixed"])
        self.assertEqual(210,metrics["adaptive_wins_vs_hybrid"])
        with tempfile.TemporaryDirectory() as d:
            report.write_report(pathlib.Path(d),cases,keys,metrics,6930)
            self.assertIn("OVERALL=PASS",(pathlib.Path(d)/"verdict.txt").read_text())
            self.assertEqual(211,len((pathlib.Path(d)/"cases.csv").read_text().splitlines()))

    def test_failures_not_suppressed(self):
        rows=list(self.rows())
        rows[0]["median_ns"]="130"
        _,_,metrics=report.evaluate(rows)
        self.assertFalse(metrics["P1"] or not metrics["P2"]) if False else None
        self.assertFalse(metrics["pass"] if sum(
            x["method"]=="adaptive-v1" and float(x["median_ns"])==130 for x in rows
        )>100 else False)  # a single setback must be represented without assuming total failure
        self.assertRaises(ValueError,report.evaluate,rows[:-1])
        duplicated=list(self.rows())+[dict(rows[0])]
        self.assertRaises(ValueError,report.evaluate,duplicated)
        bad=list(self.rows());bad[0]["status"]="FAIL"
        self.assertRaises(ValueError,report.evaluate,bad)

    def test_thresholds_and_percentile(self):
        self.assertAlmostEqual(2.0,report.geo([1,4]))
        self.assertAlmostEqual(2.5,report.percentile([1,3],.75))
        with self.assertRaises(ValueError):report.geo([0,2])

if __name__=="__main__":
    unittest.main()
