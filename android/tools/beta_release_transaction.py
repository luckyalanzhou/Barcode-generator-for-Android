#!/usr/bin/env python3
"""通过 GitHub REST API 发布 Beta Release，并在失败时补偿恢复旧状态。"""

from __future__ import annotations

import argparse
import hashlib
import http.client
import json
import os
import shutil
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


class GitHubApiError(RuntimeError):
    def __init__(self, method: str, url: str, status: int, detail: str):
        super().__init__(f"{method} {url} returned HTTP {status}: {detail}")
        self.status = status


class BetaReleaseTransactionError(RuntimeError):
    pass


class GitHubApi:
    """使用工作流 token 调用 GitHub REST API，上传大文件时按块读取。"""

    def __init__(self, api_url: str, token: str):
        self.api_url = api_url.rstrip("/")
        self.token = token
        self._connections: dict[tuple[str, str, int], http.client.HTTPConnection] = {}

    def _open_request(self, method: str, url: str, body, headers: dict[str, str]):
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise ValueError(f"GitHub API URL is invalid: {url}")
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        key = (parsed.scheme, parsed.hostname, port)
        connection = self._connections.get(key)
        if connection is None:
            if parsed.scheme == "https":
                connection = http.client.HTTPSConnection(parsed.hostname, port, timeout=60)
            else:
                connection = http.client.HTTPConnection(parsed.hostname, port, timeout=60)
            self._connections[key] = connection
        path = urllib.parse.urlunsplit(("", "", parsed.path or "/", parsed.query, ""))
        try:
            connection.request(method, path, body=body, headers=headers)
            return key, connection, connection.getresponse()
        except (OSError, http.client.HTTPException, TimeoutError) as error:
            self._discard_connection(key)
            raise RuntimeError(f"{method} {url} failed: {error}") from error

    def _discard_connection(self, key: tuple[str, str, int]) -> None:
        connection = self._connections.pop(key, None)
        if connection is not None:
            connection.close()

    def close(self) -> None:
        for key in list(self._connections):
            self._discard_connection(key)

    def request_json(self, method: str, path: str, payload: dict | None = None):
        url = path if path.startswith("http://") or path.startswith("https://") else f"{self.api_url}{path}"
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        headers = self._headers("application/vnd.github+json")
        if body is not None:
            headers["Content-Type"] = "application/json"
        key = None
        try:
            key, connection, response = self._open_request(method, url, body, headers)
            response_body = response.read()
            if response.will_close:
                self._discard_connection(key)
            if response.status >= 400:
                detail = response_body.decode("utf-8", errors="replace")
                raise GitHubApiError(method, url, response.status, detail[:500])
            if not response_body:
                return None
            return json.loads(response_body)
        except (OSError, http.client.HTTPException, TimeoutError) as error:
            if key is not None:
                self._discard_connection(key)
            raise RuntimeError(f"{method} {url} failed: {error}") from error

    def download_asset(self, repository: str, asset_id: int, destination: Path) -> None:
        url = f"{self.api_url}{_repository_path(repository, f'releases/assets/{asset_id}')}"
        key, _connection, response = self._open_request(
            "GET",
            url,
            None,
            self._headers("application/octet-stream"),
        )
        if response.status in (301, 302, 303, 307, 308):
            redirect_url = response.getheader("Location")
            response.read()
            if response.will_close:
                self._discard_connection(key)
            if not redirect_url:
                raise RuntimeError("GitHub returned an asset redirect without a location")
            # 下载存储地址使用短期签名 URL；不要把 GitHub token 转发到其他主机。
            opener = urllib.request.build_opener(_NoRedirectHandler())
            try:
                response = opener.open(
                    urllib.request.Request(redirect_url, headers={"User-Agent": "barcode-beta-release"}),
                    timeout=60,
                )
            except urllib.error.HTTPError as error:
                detail = error.read().decode("utf-8", errors="replace")
                raise GitHubApiError("GET", redirect_url, error.code, detail[:500]) from error
            with response, destination.open("wb") as output:
                shutil.copyfileobj(response, output, length=1024 * 1024)
            return
        if response.status >= 400:
            detail = response.read().decode("utf-8", errors="replace")
            if response.will_close:
                self._discard_connection(key)
            raise GitHubApiError("GET", url, response.status, detail[:500])
        with destination.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        if response.will_close:
            self._discard_connection(key)

    def upload_asset(self, upload_url: str, asset_path: Path) -> dict:
        endpoint = upload_url.split("{", 1)[0]
        separator = "&" if "?" in endpoint else "?"
        endpoint = f"{endpoint}{separator}name={urllib.parse.quote(asset_path.name)}"
        parsed = urllib.parse.urlsplit(endpoint)
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise ValueError("GitHub Release upload URL is invalid")

        headers = self._headers("application/vnd.github+json") | {
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(asset_path.stat().st_size),
        }
        key = None
        try:
            with asset_path.open("rb") as asset:
                key, _connection, response = self._open_request("POST", endpoint, asset, headers)
                response_body = response.read()
                if response.will_close:
                    self._discard_connection(key)
                if response.status != 201:
                    detail = response_body.decode("utf-8", errors="replace")
                    raise GitHubApiError("POST", endpoint, response.status, detail[:500])
                return json.loads(response_body)
        except (OSError, http.client.HTTPException, TimeoutError) as error:
            if key is not None:
                self._discard_connection(key)
            raise RuntimeError(f"POST {endpoint} failed: {error}") from error

    def _headers(self, accept: str) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self.token}",
            "Accept": accept,
            "User-Agent": "barcode-beta-release",
            "X-GitHub-Api-Version": "2022-11-28",
        }


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, _request, _file_pointer, _code, _message, _headers, _new_url):
        return None


def _repository_path(repository: str, suffix: str) -> str:
    return f"/repos/{urllib.parse.quote(repository, safe='/')}/{suffix}"


def _tag_path(repository: str, tag: str) -> str:
    encoded_tag = urllib.parse.quote(tag, safe="")
    return _repository_path(repository, f"git/refs/tags/{encoded_tag}")


def _release_for_tag(api: GitHubApi, repository: str, tag: str) -> dict | None:
    path = _repository_path(repository, f"releases/tags/{urllib.parse.quote(tag, safe='')}")
    try:
        return api.request_json("GET", path)
    except GitHubApiError as error:
        if error.status == 404:
            return None
        raise


def _tag_ref(api: GitHubApi, repository: str, tag: str) -> dict | None:
    try:
        return api.request_json("GET", _tag_path(repository, tag))
    except GitHubApiError as error:
        if error.status == 404:
            return None
        raise


def _matching_assets(release: dict, asset_name: str) -> list[dict]:
    return [asset for asset in release.get("assets", []) if asset.get("name") == asset_name]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _upload_url(release: dict) -> str:
    upload_url = release.get("upload_url")
    if not isinstance(upload_url, str) or not upload_url:
        raise ValueError("GitHub Release is missing its asset upload URL")
    return upload_url


def _restore_tag(api: GitHubApi, repository: str, tag: str, previous_ref: dict | None) -> None:
    path = _tag_path(repository, tag)
    if previous_ref is not None:
        payload = {"sha": previous_ref["object"]["sha"], "force": True}
        try:
            api.request_json("PATCH", path, payload)
        except GitHubApiError as error:
            if error.status != 404:
                raise
            api.request_json(
                "POST",
                _repository_path(repository, "git/refs"),
                {"ref": f"refs/tags/{tag}", "sha": payload["sha"]},
            )
        return

    current_ref = _tag_ref(api, repository, tag)
    if current_ref is not None:
        api.request_json("DELETE", path)


def _restore_existing_release(
    api: GitHubApi,
    repository: str,
    release: dict,
    asset_name: str,
    previous_asset: Path | None,
) -> None:
    release_path = _repository_path(repository, f"releases/{release['id']}")
    current = api.request_json("GET", release_path)
    current_assets = _matching_assets(current, asset_name)
    for asset in current_assets:
        api.request_json(
            "DELETE",
            _repository_path(repository, f"releases/assets/{asset['id']}"),
        )
    api.request_json(
        "PATCH",
        release_path,
        {"name": release.get("name"), "body": release.get("body")},
    )
    if previous_asset is not None:
        api.upload_asset(_upload_url(current), previous_asset)


def _rollback(
    api: GitHubApi,
    repository: str,
    tag: str,
    sha: str,
    original_release: dict | None,
    original_tag: dict | None,
    previous_asset: Path | None,
    asset_name: str,
) -> list[str]:
    failures: list[str] = []

    def attempt(label: str, operation) -> None:
        try:
            operation()
        except Exception as error:  # noqa: BLE001 - 补偿需继续尝试恢复其他资源。
            failures.append(f"{label}: {error}")

    if original_release is None:
        current_release = None
        try:
            current_release = _release_for_tag(api, repository, tag)
        except Exception as error:  # noqa: BLE001
            failures.append(f"查找新建 Release: {error}")
        if current_release is not None:
            attempt(
                "删除新建 Release",
                lambda: api.request_json(
                    "DELETE",
                    _repository_path(repository, f"releases/{current_release['id']}"),
                ),
            )
        attempt("恢复 tag", lambda: _restore_tag(api, repository, tag, original_tag))
        return failures

    attempt("恢复 tag", lambda: _restore_tag(api, repository, tag, original_tag))
    attempt(
        "恢复原 Release 元数据与 APK",
        lambda: _restore_existing_release(
            api,
            repository,
            original_release,
            asset_name,
            previous_asset,
        ),
    )
    return failures


def publish_beta_release(
    api: GitHubApi,
    repository: str,
    tag: str,
    sha: str,
    name: str,
    body: str,
    asset_path: Path,
    expected_sha256: str,
) -> None:
    if not asset_path.is_file():
        raise ValueError(f"Beta APK does not exist: {asset_path}")
    if len(sha) != 40 or any(character not in "0123456789abcdefABCDEF" for character in sha):
        raise ValueError("GITHUB_SHA must be a full 40-character commit SHA")
    normalized_sha256 = expected_sha256.strip().lower()
    if len(normalized_sha256) != 64 or any(
        character not in "0123456789abcdef" for character in normalized_sha256
    ):
        raise ValueError("Expected APK SHA-256 must contain 64 hexadecimal characters")
    if _sha256(asset_path) != normalized_sha256:
        raise ValueError("Local Beta APK SHA-256 does not match the build output")

    original_release = _release_for_tag(api, repository, tag)
    original_tag = _tag_ref(api, repository, tag)
    old_assets = [] if original_release is None else _matching_assets(original_release, asset_path.name)
    if len(old_assets) > 1:
        raise ValueError(f"Beta Release contains multiple assets named {asset_path.name}")

    with tempfile.TemporaryDirectory(prefix="beta-release-rollback-") as temporary_directory:
        previous_asset = None
        if old_assets:
            previous_asset = Path(temporary_directory) / asset_path.name
            api.download_asset(repository, int(old_assets[0]["id"]), previous_asset)
            if previous_asset.stat().st_size != int(old_assets[0].get("size", -1)):
                raise RuntimeError("Downloaded backup asset size does not match GitHub metadata")

        release = original_release
        try:
            if release is None:
                release = api.request_json(
                    "POST",
                    _repository_path(repository, "releases"),
                    {
                        "tag_name": tag,
                        "target_commitish": sha,
                        "name": name,
                        "body": body,
                        "draft": False,
                        "prerelease": False,
                    },
                )

            release_id = release["id"]
            current_release = api.request_json(
                "GET",
                _repository_path(repository, f"releases/{release_id}"),
            )
            for asset in _matching_assets(current_release, asset_path.name):
                api.request_json(
                    "DELETE",
                    _repository_path(repository, f"releases/assets/{asset['id']}"),
                )
            uploaded_asset = api.upload_asset(_upload_url(current_release), asset_path)
            if uploaded_asset.get("name") != asset_path.name or int(
                uploaded_asset.get("size", -1)
            ) != asset_path.stat().st_size:
                raise RuntimeError("GitHub upload response did not match the Beta APK")
            uploaded_apk = Path(temporary_directory) / "uploaded-beta.apk"
            api.download_asset(repository, int(uploaded_asset["id"]), uploaded_apk)
            if _sha256(uploaded_apk) != normalized_sha256:
                raise RuntimeError("Uploaded Beta APK SHA-256 did not match the build output")

            api.request_json(
                "PATCH",
                _repository_path(repository, f"releases/{release_id}"),
                {"name": name, "body": body},
            )

            tag_ref = _tag_ref(api, repository, tag)
            if tag_ref is None:
                api.request_json(
                    "POST",
                    _repository_path(repository, "git/refs"),
                    {"ref": f"refs/tags/{tag}", "sha": sha},
                )
            else:
                api.request_json("PATCH", _tag_path(repository, tag), {"sha": sha, "force": True})

            verified_release = api.request_json(
                "GET",
                _repository_path(repository, f"releases/{release_id}"),
            )
            verified_assets = _matching_assets(verified_release, asset_path.name)
            if verified_release.get("name") != name or verified_release.get("body") != body:
                raise RuntimeError("Published Beta Release metadata did not match the requested values")
            if (
                len(verified_assets) != 1
                or int(verified_assets[0].get("size", -1)) != asset_path.stat().st_size
                or int(verified_assets[0].get("id", -1)) != int(uploaded_asset["id"])
            ):
                raise RuntimeError("Published Beta APK was missing or changed after upload verification")
            verified_tag = _tag_ref(api, repository, tag)
            if verified_tag is None or verified_tag.get("object", {}).get("sha") != sha:
                raise RuntimeError("Beta Release tag does not point to the built commit")
        except Exception as error:
            rollback_failures = _rollback(
                api,
                repository,
                tag,
                sha,
                original_release,
                original_tag,
                previous_asset,
                asset_path.name,
            )
            if rollback_failures:
                details = "; ".join(rollback_failures)
                raise BetaReleaseTransactionError(
                    f"Beta Release failed ({error}); automatic rollback was incomplete: {details}"
                ) from error
            raise BetaReleaseTransactionError(
                f"Beta Release failed ({error}); previous published state was restored"
            ) from error


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--body-file", required=True, type=Path)
    parser.add_argument("--asset", required=True, type=Path)
    parser.add_argument("--sha256", required=True)
    args = parser.parse_args()

    repository = os.environ.get("GITHUB_REPOSITORY", "")
    sha = os.environ.get("GITHUB_SHA", "")
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    api_url = os.environ.get("GITHUB_API_URL", "https://api.github.com")
    if not repository or not token:
        print("GITHUB_REPOSITORY and GITHUB_TOKEN are required", file=sys.stderr)
        return 2
    try:
        body = args.body_file.read_text(encoding="utf-8")
        api = GitHubApi(api_url, token)
        publish_beta_release(
            api,
            repository,
            args.tag,
            sha,
            args.name,
            body,
            args.asset,
            args.sha256,
        )
    except (BetaReleaseTransactionError, GitHubApiError, OSError, RuntimeError, ValueError) as error:
        print(f"Beta Release transaction failed: {error}", file=sys.stderr)
        return 1
    finally:
        if "api" in locals():
            api.close()
    print(f"Beta Release {args.tag} updated successfully at {sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
