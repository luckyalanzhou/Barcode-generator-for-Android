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

    def test_existing_release_without_valid_version_code_fails(self):
        for body in ("Release notes", "versionCode: 0", "versionCode: nope", "versionCode: 2\nversionCode: 3"):
            with self.subTest(body=body), self.assertRaises(ValueError):
                resolve_beta_version("9.9.9", 1, body)

    def test_invalid_base_and_version_code_overflow_fail(self):
        with self.assertRaises(ValueError):
            resolve_beta_version("9.9.8", 1, None)
        with self.assertRaises(ValueError):
            resolve_beta_version("9.9.9", 2_100_000_000, None)
        with self.assertRaises(ValueError):
            resolve_beta_version("9.9.9", 1, "versionCode: 2099999999")


if __name__ == "__main__":
    unittest.main()
