#!/usr/bin/env python3
"""Assign the fixed Beta version name and monotonically increasing versionCode."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ANDROID_MAX_VERSION_CODE = 2_100_000_000
WORKFLOW_RUN_PATTERN = re.compile(
    r"^workflowRun: https://github\.com/[^/\s]+/[^/\s]+/actions/runs/([0-9]+)$",
    flags=re.MULTILINE,
)


def resolve_beta_version(
    base_version: str,
    base_code: int,
    release_body: str | None,
    run_id: str | None = None,
) -> dict[str, int | str]:
    if base_version != "9.9.9":
        raise ValueError("Beta versionName must remain 9.9.9")
    if not 1 <= base_code <= ANDROID_MAX_VERSION_CODE:
        raise ValueError("Beta base versionCode is outside Android's valid range")

    if release_body is None:
        return {"version_name": base_version, "version_code": base_code, "source": "beta-version.properties"}

    matches = re.findall(r"^versionCode: ([0-9]+)$", release_body, flags=re.MULTILINE)
    if len(matches) != 1 or int(matches[0]) < 1:
        raise ValueError("Beta Release versionCode is missing or invalid")
    release_code = int(matches[0])
    workflow_runs = WORKFLOW_RUN_PATTERN.findall(release_body)
    if run_id is not None and len(workflow_runs) == 1 and workflow_runs[0] == str(run_id):
        return {
            "version_name": base_version,
            "version_code": release_code,
            "source": "beta Release (same workflow run)",
        }
    if release_code + 1 > ANDROID_MAX_VERSION_CODE:
        raise ValueError("Beta versionCode reached Android's maximum")
    return {"version_name": base_version, "version_code": release_code + 1, "source": "beta Release"}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--properties", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--run-id")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--release-json", type=Path)
    source.add_argument("--release-not-found", action="store_true")
    args = parser.parse_args()

    properties: dict[str, str] = {}
    for line in args.properties.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    try:
        base_code = int(properties["versionCode"])
        base_version = properties["versionName"]
    except (KeyError, ValueError) as error:
        raise SystemExit("Beta version properties are missing or invalid") from error

    release_body = None
    if args.release_json is not None:
        release = json.loads(args.release_json.read_text(encoding="utf-8"))
        if not isinstance(release, dict) or not isinstance(release.get("body"), str):
            raise SystemExit("Beta Release metadata has an invalid body")
        release_body = release["body"]

    try:
        plan = resolve_beta_version(base_version, base_code, release_body, run_id=args.run_id)
    except ValueError as error:
        raise SystemExit(str(error)) from error
    args.output.write_text(json.dumps(plan, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
