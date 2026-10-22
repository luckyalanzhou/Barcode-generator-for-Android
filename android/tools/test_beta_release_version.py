import unittest

from beta_release_version import resolve_beta_version


class BetaReleaseVersionTests(unittest.TestCase):
    def test_only_confirmed_missing_release_uses_initial_baseline(self):
        plan = resolve_beta_version("9.9.9", 1, None)
        self.assertEqual(1, plan["version_code"])
        self.assertEqual("beta-version.properties", plan["source"])

    def test_existing_release_increments_version_code_and_keeps_fixed_name(self):
        plan = resolve_beta_version("9.9.9", 1, "Release notes\nversionCode: 12\n")
        self.assertEqual("9.9.9", plan["version_name"])
        self.assertEqual(13, plan["version_code"])
        self.assertEqual("beta Release", plan["source"])

    def test_retrying_same_run_reuses_its_already_published_version_code(self):
        release_body = (
            "Release notes\n"
            "versionCode: 13\n"
            "workflowRun: https://github.com/example/barcode/actions/runs/12345\n"
        )

        plan = resolve_beta_version("9.9.9", 1, release_body, run_id="12345")

        self.assertEqual(13, plan["version_code"])
        self.assertEqual("beta Release (same workflow run)", plan["source"])

    def test_new_run_increments_version_code_after_previous_run_was_published(self):
        release_body = (
            "Release notes\n"
            "versionCode: 13\n"
            "workflowRun: https://github.com/example/barcode/actions/runs/12345\n"
        )

        plan = resolve_beta_version("9.9.9", 1, release_body, run_id="67890")

        self.assertEqual(14, plan["version_code"])
        self.assertEqual("beta Release", plan["source"])

    def test_existing_release_without_valid_version_code_fails(self):
        for body in ("Release notes", "versionCode: 0", "versionCode: nope", "versionCode: 2\nversionCode: 3"):
            with self.subTest(body=body), self.assertRaises(ValueError):
                resolve_beta_version("9.9.9", 1, body)

    def test_invalid_base_and_version_code_overflow_fail(self):
        with self.assertRaises(ValueError):
            resolve_beta_version("9.9.8", 1, None)
        with self.assertRaises(ValueError):
            resolve_beta_version("9.9.9", 2_100_000_001, None)
        with self.assertRaises(ValueError):
            resolve_beta_version("9.9.9", 1, "versionCode: 2100000000")

    def test_android_maximum_version_code_is_inclusive(self):
        plan = resolve_beta_version("9.9.9", 2_100_000_000, None)
        self.assertEqual(2_100_000_000, plan["version_code"])

        plan = resolve_beta_version("9.9.9", 2_099_999_999, None)
        self.assertEqual(2_099_999_999, plan["version_code"])

        plan = resolve_beta_version("9.9.9", 1, "versionCode: 2099999999")
        self.assertEqual(2_100_000_000, plan["version_code"])


if __name__ == "__main__":
    unittest.main()
