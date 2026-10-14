"""正式版版本自动分配、失败恢复和边界规则测试。"""

import json
import tempfile
import unittest
from pathlib import Path

from official_release_version import (
    MAX_ANDROID_VERSION_CODE,
    ReleaseVersion,
    RELEASE_TYPES,
    create_release_plan,
    next_version,
    parse_version,
    read_release_lines,
    write_metadata,
    write_publish_request,
    write_reserve_request,
)


def make_release(
    version: str,
    version_code: int,
    run_id: str = "100",
    status: str = "complete",
    draft: bool = False,
    checksum: bool = True,
    release_type: str | None = None,
) -> dict:
    """构造 GitHub Releases API 的最小测试数据。"""

    apk_name = f"BarcodeGeneratorOfficial{version}.apk"
    assets = [{"name": apk_name, "size": 100, "state": "uploaded"}]
    if checksum:
        assets.append({"name": f"{apk_name}.sha256", "size": 90, "state": "uploaded"})
    body = write_metadata("正式版说明", parse_version(version), version_code, run_id, status, release_type)
    return {
        "id": int(version_code),
        "tag_name": f"android-v{version}",
        "name": f"Barcode Generator Release v{version}",
        "body": body,
        "draft": draft,
        "assets": assets,
    }


class OfficialReleaseVersionTest(unittest.TestCase):
    """验证正式版本号、versionCode 和中断恢复规则。"""

    def test_initial_release_starts_at_patch_one_and_code_one(self) -> None:
        plan = create_release_plan([], "101")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.0.1", 1))
        self.assertEqual(plan["action"], "create")
        self.assertIn("status=reserved", plan["body"])
        self.assertIn("release_type=patch", plan["body"])

    def test_manual_patch_minor_and_major_bumps_do_not_auto_roll_patch(self) -> None:
        current = ReleaseVersion(1, 9, 99)
        self.assertEqual(str(next_version(current)), "1.9.100")
        self.assertEqual(str(next_version(current, "minor")), "1.10.0")
        self.assertEqual(str(next_version(current, "major")), "2.0.0")

    def test_version_format_has_unbounded_numeric_parts_and_rejects_leading_zero(self) -> None:
        self.assertEqual(str(parse_version("10.100.1000")), "10.100.1000")
        self.assertEqual(
            str(parse_version("123456789012345678901234567890.8.900000000000000000001")),
            "123456789012345678901234567890.8.900000000000000000001",
        )
        for value in ("00.0.1", "0.01.0", "0.0.03", "1.2.3-rc.1", "1.2"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_version(value)

    def test_default_patch_release_and_independent_code(self) -> None:
        plan = create_release_plan([make_release("0.0.1", 1)], "102")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.0.2", 2))
        self.assertEqual(plan["release_type"], "patch")

    def test_manual_minor_and_major_selection_is_applied_to_new_release(self) -> None:
        releases = [make_release("1.9.99", 12)]
        minor = create_release_plan(releases, "103", "minor")
        major = create_release_plan(releases, "104", "major")
        self.assertEqual((minor["version"], minor["release_type"]), ("1.10.0", "minor"))
        self.assertEqual((major["version"], major["release_type"]), ("2.0.0", "major"))
        self.assertIn("release_type=minor", minor["body"])

    def test_invalid_manual_release_type_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "递增类型"):
            create_release_plan([], "105", "unexpected")
        self.assertEqual(RELEASE_TYPES, ("patch", "minor", "major"))

    def test_failed_draft_is_resumed_with_same_version_and_code(self) -> None:
        pending = make_release(
            "0.0.8", 27, run_id="101", status="reserved", draft=True, release_type="minor"
        )
        plan = create_release_plan([make_release("0.0.7", 26), pending], "102", "major")
        self.assertEqual(plan["action"], "resume")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.0.8", 27))
        self.assertEqual(plan["release_type"], "minor")
        self.assertEqual(plan["release_id"], pending["id"])
        request = write_reserve_request(plan, "main-sha")
        self.assertTrue(request["draft"])
        self.assertIn("run_id=102 release_type=minor status=reserved", request["body"])

    def test_published_release_missing_checksum_keeps_its_original_visibility(self) -> None:
        incomplete = make_release("0.1.0", 100, status="complete", draft=False, checksum=False)
        plan = create_release_plan([incomplete], "200")
        self.assertEqual(plan["action"], "resume")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.1.0", 100))
        self.assertFalse(write_reserve_request(plan, "main-sha")["draft"])

    def test_legacy_draft_without_metadata_gets_code_once_and_marker(self) -> None:
        pending = {
            "id": 22,
            "tag_name": "android-v0.0.9",
            "body": "以前保存的发布说明",
            "draft": True,
            "assets": [],
        }
        plan = create_release_plan([make_release("0.0.8", 8), pending], "300")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.0.9", 9))
        self.assertIn("version_code=9", plan["body"])

    def test_release_list_reader_accepts_slurped_api_json_and_json_lines(self) -> None:
        releases = [make_release("0.0.1", 1), make_release("0.0.2", 2)]
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir) / "releases.json"
            source.write_text(json.dumps(releases), encoding="utf-8")
            self.assertEqual(len(read_release_lines(source)), 2)
            source.write_text(json.dumps([releases, [releases[0]]]), encoding="utf-8")
            self.assertEqual(len(read_release_lines(source)), 3)
            source.write_text("\n".join(json.dumps(item) for item in releases), encoding="utf-8")
            self.assertEqual(len(read_release_lines(source)), 2)

    def test_rerunning_same_completed_workflow_does_not_allocate_next_version(self) -> None:
        plan = create_release_plan([make_release("0.0.1", 1, run_id="123")], "123")
        self.assertEqual(plan["action"], "skip")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.0.1", 1))

    def test_publish_request_marks_complete_but_only_after_external_verification(self) -> None:
        plan = create_release_plan([], "456")
        request = write_publish_request(plan)
        self.assertFalse(request["draft"])
        self.assertIn("status=complete", request["body"])
        self.assertIn("release_type=patch", request["body"])
        self.assertIn("run_id=456", request["body"])

    def test_high_version_numbers_increment_without_overflow_or_wrapping(self) -> None:
        high = ReleaseVersion(9_999_999_999_999_999, 999_999_999_999_999, 99)
        self.assertEqual(str(next_version(high)), "9999999999999999.999999999999999.100")

    def test_android_version_code_upper_bound_stops(self) -> None:
        with self.assertRaisesRegex(ValueError, "Android"):
            create_release_plan([make_release("0.0.1", MAX_ANDROID_VERSION_CODE)], "1000")

    def test_tag_and_body_metadata_must_agree(self) -> None:
        release = make_release("0.0.1", 1)
        release["body"] = release["body"].replace("version=0.0.1", "version=0.0.2")
        with self.assertRaisesRegex(ValueError, "tag 与正文版本标记不一致"):
            create_release_plan([release], "101")


if __name__ == "__main__":
    unittest.main()
