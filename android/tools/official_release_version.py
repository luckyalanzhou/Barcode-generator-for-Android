#!/usr/bin/env python3
"""管理正式版版本号，并生成 GitHub Release API 请求。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


TAG_PREFIX = "android-v"
MAX_ANDROID_VERSION_CODE = 2_100_000_000
MARKER_PREFIX = "<!-- barcode-official-release:"
MARKER_PATTERN = re.compile(
    r"<!-- barcode-official-release: version=(\S+) version_code=(\d+) "
    r"run_id=(\S+)(?: release_type=(patch|minor|major))? status=(reserved|complete) -->"
)
MARKER_LINE_PATTERN = re.compile(
    r"(?m)^<!-- barcode-official-release:.*?-->[ \t]*(?:\r?\n|$)"
)
VERSION_PART_PATTERN = r"(?:0|[1-9][0-9]*)"
TAG_PATTERN = re.compile(
    rf"^{re.escape(TAG_PREFIX)}({VERSION_PART_PATTERN})\.({VERSION_PART_PATTERN})\.({VERSION_PART_PATTERN})$"
)
RELEASE_TYPES = ("patch", "minor", "major")


@dataclass(frozen=True, order=True)
class ReleaseVersion:
    """正式版 SemVer 版本；三个数字段均无固定上限。"""

    major: int
    minor: int
    patch: int

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"


def parse_version(value: str) -> ReleaseVersion:
    """解析正式版 MAJOR.MINOR.PATCH，并拒绝前导零和预发布后缀。"""

    match = TAG_PATTERN.fullmatch(f"{TAG_PREFIX}{value}")
    if not match:
        raise ValueError(f"无效正式版版本号：{value!r}，必须为无前导零的 MAJOR.MINOR.PATCH")
    return ReleaseVersion(*(int(part) for part in match.groups()))


def next_version(current: ReleaseVersion, release_type: str = "patch") -> ReleaseVersion:
    """按手动选择的 SemVer 级别递增；Patch 默认，不做自动进位。"""

    if release_type == "patch":
        return ReleaseVersion(current.major, current.minor, current.patch + 1)
    if release_type == "minor":
        return ReleaseVersion(current.major, current.minor + 1, 0)
    if release_type == "major":
        return ReleaseVersion(current.major + 1, 0, 0)
    raise ValueError(f"无效正式版递增类型：{release_type!r}，必须为 patch、minor 或 major")


def parse_tag(tag_name: str) -> ReleaseVersion | None:
    """读取正式版 tag；同前缀但格式错误的 tag 会触发保护性失败。"""

    if not tag_name.startswith(TAG_PREFIX):
        return None
    version_text = tag_name[len(TAG_PREFIX) :]
    return parse_version(version_text)


def read_metadata(body: str, version: ReleaseVersion) -> dict[str, Any] | None:
    """读取 Release 正文中的恢复标记，防止重跑重新分配版本。"""

    if MARKER_PREFIX not in body:
        return None
    match = MARKER_PATTERN.search(body)
    if not match:
        raise ValueError(f"Release v{version} 的版本状态标记损坏，拒绝猜测版本")
    marked_version, code_text, run_id, release_type, status = match.groups()
    if parse_version(marked_version) != version:
        raise ValueError(f"Release tag 与正文版本标记不一致：v{version} / v{marked_version}")
    version_code = int(code_text)
    if version_code < 1 or version_code > MAX_ANDROID_VERSION_CODE:
        raise ValueError(f"Release v{version} 的 versionCode 超出 Android 可用范围")
    return {
        "version": str(version),
        "version_code": version_code,
        "run_id": run_id,
        "release_type": release_type,
        "status": status,
    }


def write_metadata(
    body: str,
    version: ReleaseVersion,
    version_code: int,
    run_id: str,
    status: str,
    release_type: str | None = None,
) -> str:
    """保留可见 Release 说明，只替换隐藏的发布状态标记。"""

    content = MARKER_LINE_PATTERN.sub("", body or "").strip()
    if not content:
        content = f"正式版 v{version}。"
    type_marker = f" release_type={release_type}" if release_type is not None else ""
    marker = f"{MARKER_PREFIX} version={version} version_code={version_code} run_id={run_id}{type_marker} status={status} -->"
    return f"{content}\n\n{marker}\n"


def read_release_lines(path: Path) -> list[dict[str, Any]]:
    """读取 GitHub CLI 输出的 JSON Lines；兼容空列表和单个 JSON 数组。"""

    content = path.read_text(encoding="utf-8").strip()
    if not content:
        return []
    try:
        parsed = json.loads(content)
    except json.JSONDecodeError:
        parsed = [json.loads(line) for line in content.splitlines() if line.strip()]
    results: list[dict[str, Any]] = []

    def collect(value: Any) -> None:
        """展开 gh api --paginate --slurp 产生的多层页面数组。"""

        if isinstance(value, dict):
            results.append(value)
        elif isinstance(value, list):
            for item in value:
                collect(item)

    collect(parsed)
    if not results and not isinstance(parsed, list):
        raise ValueError("Release 列表不是有效的 JSON 对象列表")
    return results


def _asset_map(release: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """按文件名索引附件，并保留 API 上传状态和文件大小。"""

    return {
        str(asset.get("name", "")): asset
        for asset in release.get("assets", [])
        if isinstance(asset, dict) and asset.get("name")
    }


def _is_uploaded(asset: dict[str, Any] | None) -> bool:
    """只有 API 标记已上传且非空的附件才可视为完整。"""

    return bool(asset and asset.get("state") == "uploaded" and int(asset.get("size", 0)) > 0)


def _release_records(releases: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """规范化正式版 Release，识别可恢复草稿和完整发布。"""

    records = []
    for release in releases:
        version = parse_tag(str(release.get("tag_name", "")))
        if version is None:
            continue
        body = str(release.get("body") or "")
        metadata = read_metadata(body, version)
        assets = _asset_map(release)
        apk_name = f"BarcodeGeneratorOfficial{version}.apk"
        checksum_name = f"{apk_name}.sha256"
        complete = (
            not bool(release.get("draft"))
            and metadata is not None
            and metadata["status"] == "complete"
            and _is_uploaded(assets.get(apk_name))
            and _is_uploaded(assets.get(checksum_name))
        )
        records.append(
            {
                "id": release.get("id"),
                "version": version,
                "body": body,
                "draft": bool(release.get("draft")),
                "metadata": metadata,
                "complete": complete,
            }
        )
    return records


def _next_version_code(records: list[dict[str, Any]]) -> int:
    """versionCode 与显示版本分离，整个 Release 历史中只递增不回绕。"""

    codes = [
        int(record["metadata"]["version_code"])
        for record in records
        if record["metadata"] is not None
    ]
    candidate = max(codes, default=0) + 1
    if candidate > MAX_ANDROID_VERSION_CODE:
        raise ValueError("versionCode 已达 Android 上限，停止发布")
    return candidate


def create_release_plan(
    releases: Iterable[dict[str, Any]],
    run_id: str,
    release_type: str = "patch",
) -> dict[str, Any]:
    """按用户选择分配新版本，或恢复已有分配，不重复递增。"""

    if release_type not in RELEASE_TYPES:
        raise ValueError(f"无效正式版递增类型：{release_type!r}，必须为 patch、minor 或 major")

    records = _release_records(releases)

    # 同一 Actions run 被重新运行时，完整发布应直接结束，不能再加版本。
    for record in records:
        metadata = record["metadata"]
        if record["complete"] and metadata["run_id"] == str(run_id):
            version = record["version"]
            return {
                "action": "skip",
                "version": str(version),
                "version_code": metadata["version_code"],
                "release_type": metadata["release_type"],
                "tag_name": f"{TAG_PREFIX}{version}",
                "release_id": record["id"],
                "run_id": str(run_id),
                "draft": False,
            }

    incomplete = sorted(
        (record for record in records if not record["complete"]),
        key=lambda record: record["version"],
    )
    if incomplete:
        record = incomplete[0]
        version = record["version"]
        metadata = record["metadata"]
        # 新流程会在构建前保存 versionCode；旧草稿若没有标记，则只在这里分配一次。
        version_code = (
            int(metadata["version_code"])
            if metadata is not None
            else _next_version_code(records)
        )
        assigned_release_type = metadata["release_type"] if metadata is not None else None
        action = "resume"
        release_id = record["id"]
        body = record["body"]
    else:
        completed = [record["version"] for record in records if record["complete"]]
        baseline = max(completed, default=ReleaseVersion(0, 0, 0))
        version = next_version(baseline, release_type)
        version_code = _next_version_code(records)
        assigned_release_type = release_type
        action = "create"
        release_id = None
        body = ""

    return {
        "action": action,
        "version": str(version),
        "version_code": version_code,
        "release_type": assigned_release_type,
        "tag_name": f"{TAG_PREFIX}{version}",
        "release_id": release_id,
        "run_id": str(run_id),
        "draft": bool(record["draft"]) if action == "resume" else True,
        "name": f"Barcode Generator Release v{version}",
        "body": write_metadata(
            body, version, version_code, str(run_id), "reserved", assigned_release_type
        ),
    }


def _load_plan(path: Path) -> dict[str, Any]:
    """载入前一步生成的不可变版本分配结果。"""

    plan = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(plan, dict):
        raise ValueError("Release plan 格式错误")
    return plan


def write_reserve_request(plan: dict[str, Any], target_commitish: str) -> dict[str, Any]:
    """生成创建或恢复草稿 Release 的 API 请求体。"""

    request: dict[str, Any] = {
        "name": plan["name"],
        "body": plan["body"],
        "draft": bool(plan.get("draft", True)),
        "prerelease": False,
    }
    if plan["action"] == "create":
        request["tag_name"] = plan["tag_name"]
        request["target_commitish"] = target_commitish
    elif plan["action"] != "resume":
        raise ValueError(f"不能为 action={plan['action']!r} 生成草稿请求")
    # 恢复时沿用原有公开状态，避免尝试撤回无法改回草稿的已发布 Release。
    return request


def write_publish_request(plan: dict[str, Any]) -> dict[str, Any]:
    """准备最终发布请求；调用方必须先完成远端附件下载和校验。"""

    if plan.get("action") not in {"create", "resume"}:
        raise ValueError("只有新建或恢复中的 Release 可以发布")
    version = parse_version(str(plan["version"]))
    return {
        "body": write_metadata(
            str(plan.get("body") or ""),
            version,
            int(plan["version_code"]),
            str(plan["run_id"]),
            "complete",
            plan.get("release_type"),
        ),
        "draft": False,
        "prerelease": False,
    }


def _write_json(path: Path, value: dict[str, Any]) -> None:
    """以 UTF-8 写入 API 请求或工作流间共享的状态文件。"""

    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    """命令行入口，供 Actions 与本地单元测试共用。"""

    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    plan_parser = commands.add_parser("plan", help="分配或恢复正式版版本")
    plan_parser.add_argument("--releases", type=Path, required=True)
    plan_parser.add_argument("--run-id", required=True)
    plan_parser.add_argument("--release-type", choices=RELEASE_TYPES, default="patch")
    plan_parser.add_argument("--output", type=Path, required=True)

    reserve_parser = commands.add_parser("reserve-request", help="生成草稿 Release 请求")
    reserve_parser.add_argument("--plan", type=Path, required=True)
    reserve_parser.add_argument("--target-commitish", required=True)
    reserve_parser.add_argument("--output", type=Path, required=True)

    publish_parser = commands.add_parser("publish-request", help="生成正式发布请求")
    publish_parser.add_argument("--plan", type=Path, required=True)
    publish_parser.add_argument("--output", type=Path, required=True)

    args = parser.parse_args(argv)
    try:
        if args.command == "plan":
            plan = create_release_plan(read_release_lines(args.releases), args.run_id, args.release_type)
            _write_json(args.output, plan)
        elif args.command == "reserve-request":
            plan = _load_plan(args.plan)
            _write_json(args.output, write_reserve_request(plan, args.target_commitish))
        elif args.command == "publish-request":
            plan = _load_plan(args.plan)
            _write_json(args.output, write_publish_request(plan))
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"正式版版本管理失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
