"""正式版版本自动分配、失败恢复和边界规则测试。"""

import json
import tempfile
import unittest
from pathlib import Path

from official_release_version import (
    MAX_ANDROID_VERSION_CODE,
    ReleaseVersion,
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
) -> dict:
    """构造 GitHub Releases API 的最小测试数据。"""

    apk_name = f"BarcodeGeneratorOfficial{version}.apk"
    assets = [{"name": apk_name, "size": 100, "state": "uploaded"}]
    if checksum:
        assets.append({"name": f"{apk_name}.sha256", "size": 90, "state": "uploaded"})
    body = write_metadata("正式版说明", parse_version(version), version_code, run_id, status)
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

    def test_patch_and_minor_major_rollovers(self) -> None:
        self.assertEqual(str(next_version(ReleaseVersion(0, 0, 98))), "0.0.99")
        self.assertEqual(str(next_version(ReleaseVersion(0, 0, 99))), "0.1.0")
        self.assertEqual(str(next_version(ReleaseVersion(0, 9, 99))), "1.0.0")
        self.assertEqual(str(next_version(ReleaseVersion(8, 9, 99))), "9.0.0")
        self.assertEqual(str(next_version(ReleaseVersion(9, 8, 99))), "9.9.0")

    def test_version_format_rejects_leading_zero_and_out_of_range_values(self) -> None:
        for value in ("00.0.1", "0.10.0", "0.0.100", "1.2.03", "10.0.0"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_version(value)

    def test_next_manual_release_increments_version_and_independent_code(self) -> None:
        plan = create_release_plan([make_release("0.0.1", 1)], "102")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.0.2", 2))

    def test_failed_draft_is_resumed_with_same_version_and_code(self) -> None:
        pending = make_release("0.0.8", 27, run_id="101", status="reserved", draft=True)
        plan = create_release_plan([make_release("0.0.7", 26), pending], "102")
        self.assertEqual(plan["action"], "resume")
        self.assertEqual((plan["version"], plan["version_code"]), ("0.0.8", 27))
        self.assertEqual(plan["release_id"], pending["id"])
        request = write_reserve_request(plan, "main-sha")
        self.assertTrue(request["draft"])
        self.assertIn("run_id=102 status=reserved", request["body"])

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
        self.assertIn("run_id=456", request["body"])

    def test_latest_version_stops_without_wrapping(self) -> None:
        with self.assertRaisesRegex(ValueError, "最高版本 9.9.99"):
            next_version(ReleaseVersion(9, 9, 99))
        with self.assertRaisesRegex(ValueError, "最高版本 9.9.99"):
            create_release_plan([make_release("9.9.99", 99)], "999")

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
