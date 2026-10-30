"""Beta Release 发布事务的 loopback REST 回归。"""

from __future__ import annotations

import hashlib
import json
import tempfile
import threading
import traceback
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from beta_release_transaction import BetaReleaseTransactionError, GitHubApi, publish_beta_release


REPOSITORY = "example/barcode"
TAG = "android-test-v9.9.9"
APK_NAME = "BarcodeGeneratorBeta9.9.9.apk"
OLD_SHA = "a" * 40
NEW_SHA = "b" * 40


class ReleaseApiState:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.release: dict | None = {
            "id": 42,
            "tag_name": TAG,
            "name": "Barcode Generator Beta v9.9.9",
            "body": "old release notes",
            "draft": False,
            "prerelease": False,
            "upload_url": f"{base_url}/repos/{REPOSITORY}/releases/42/assets{{?name,label}}",
            "assets": [
                {"id": 77, "name": APK_NAME, "size": len(b"old beta apk")},
                {"id": 78, "name": "checksums.txt", "size": len(b"keep me")},
            ],
        }
        self.asset_bytes = {77: b"old beta apk", 78: b"keep me"}
        self.tag_sha = OLD_SHA
        self.next_asset_id = 100
        self.fail_once: tuple[str, str] | None = None
        self.corrupt_upload_once = False
        self.bad_auth = False
        self.requests: list[tuple[str, str]] = []


class ReleaseApiHandler(BaseHTTPRequestHandler):
    state: ReleaseApiState
    protocol_version = "HTTP/1.1"

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def _authorized(self) -> bool:
        if self.headers.get("Authorization") == "Bearer test-token":
            return True
        self.state.bad_auth = True
        self._json(401, {"message": "bad token"})
        return False

    def _json(self, status: int, value: object) -> None:
        payload = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _body(self) -> bytes:
        return self.rfile.read(int(self.headers.get("Content-Length", "0")))

    def _should_fail_after_mutation(self) -> bool:
        expected = self.state.fail_once
        actual = (self.command, urlparse(self.path).path)
        if expected == actual:
            self.state.fail_once = None
            return True
        return False

    def do_GET(self) -> None:
        self.state.requests.append((self.command, self.path))
        if not self._authorized():
            return
        path = urlparse(self.path).path
        if path == f"/repos/{REPOSITORY}/releases/tags/{TAG}":
            if self.state.release is None:
                self._json(404, {"message": "release not found"})
            else:
                self._json(200, self.state.release)
        elif path == f"/repos/{REPOSITORY}/releases/42":
            if self.state.release is None:
                self._json(404, {"message": "release not found"})
            else:
                self._json(200, self.state.release)
        elif path == f"/repos/{REPOSITORY}/git/refs/tags/{TAG}":
            if self.state.tag_sha is None:
                self._json(404, {"message": "tag not found"})
            else:
                self._json(200, {"object": {"sha": self.state.tag_sha, "type": "commit"}})
        elif path.startswith(f"/repos/{REPOSITORY}/releases/assets/"):
            asset_id = int(path.rsplit("/", 1)[-1])
            payload = self.state.asset_bytes.get(asset_id)
            if payload is None:
                self._json(404, {"message": "asset not found"})
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        else:
            self._json(404, {"message": "not found"})

    def do_PATCH(self) -> None:
        self.state.requests.append((self.command, self.path))
        if not self._authorized():
            return
        path = urlparse(self.path).path
        payload = json.loads(self._body() or b"{}")
        if path == f"/repos/{REPOSITORY}/releases/42":
            if self.state.release is None:
                self._json(404, {"message": "release not found"})
                return
            self.state.release.update(payload)
            if self._should_fail_after_mutation():
                self._json(500, {"message": "simulated response failure after metadata write"})
                return
            self._json(200, self.state.release)
        elif path == f"/repos/{REPOSITORY}/git/refs/tags/{TAG}":
            self.state.tag_sha = payload["sha"]
            if self._should_fail_after_mutation():
                self._json(500, {"message": "simulated response failure after tag move"})
                return
            self._json(200, {"object": {"sha": self.state.tag_sha, "type": "commit"}})
        else:
            self._json(404, {"message": "not found"})

    def do_POST(self) -> None:
        self.state.requests.append((self.command, self.path))
        if not self._authorized():
            return
        parsed = urlparse(self.path)
        if parsed.path == f"/repos/{REPOSITORY}/releases":
            payload = json.loads(self._body() or b"{}")
            self.state.release = {
                "id": 42,
                "tag_name": payload["tag_name"],
                "name": payload["name"],
                "body": payload["body"],
                "draft": payload["draft"],
                "prerelease": payload["prerelease"],
                "upload_url": f"{self.state.base_url}/repos/{REPOSITORY}/releases/42/assets{{?name,label}}",
                "assets": [],
            }
            self.state.tag_sha = payload["target_commitish"]
            self._json(201, self.state.release)
            return
        prefix = f"/repos/{REPOSITORY}/releases/42/assets"
        if parsed.path != prefix:
            self._json(404, {"message": "not found"})
            return
        name = parse_qs(parsed.query).get("name", [""])[0]
        content = self._body()
        if self.state.corrupt_upload_once:
            self.state.corrupt_upload_once = False
            content = bytes([content[0] ^ 1]) + content[1:]
        asset_id = self.state.next_asset_id
        self.state.next_asset_id += 1
        self.state.asset_bytes[asset_id] = content
        assert self.state.release is not None
        self.state.release["assets"] = [
            asset for asset in self.state.release["assets"] if asset["name"] != name
        ] + [{"id": asset_id, "name": name, "size": len(content)}]
        if self._should_fail_after_mutation():
            self._json(500, {"message": "simulated response failure after asset write"})
            return
        self._json(201, {"id": asset_id, "name": name, "size": len(content)})

    def do_DELETE(self) -> None:
        self.state.requests.append((self.command, self.path))
        if not self._authorized():
            return
        path = urlparse(self.path).path
        if path == f"/repos/{REPOSITORY}/releases/42":
            self.state.release = None
            self.state.asset_bytes.clear()
            self.send_response(204)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if path == f"/repos/{REPOSITORY}/git/refs/tags/{TAG}":
            self.state.tag_sha = None
            self.send_response(204)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        prefix = f"/repos/{REPOSITORY}/releases/assets/"
        if path.startswith(prefix):
            asset_id = int(path.rsplit("/", 1)[-1])
            self.state.asset_bytes.pop(asset_id, None)
            self.state.release["assets"] = [
                asset for asset in self.state.release["assets"] if asset["id"] != asset_id
            ]
            self.send_response(204)
            self.send_header("Content-Length", "0")
            self.end_headers()
        else:
            self._json(404, {"message": "not found"})


class RecordingHttpServer(ThreadingHTTPServer):
    def __init__(self, server_address, handler_class):
        super().__init__(server_address, handler_class)
        self.errors: list[str] = []

    def handle_error(self, _request, _client_address) -> None:
        self.errors.append(traceback.format_exc())


class BetaReleaseTransactionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.server = RecordingHttpServer(("127.0.0.1", 0), ReleaseApiHandler)
        base_url = f"http://127.0.0.1:{self.server.server_port}"
        self.state = ReleaseApiState(base_url)
        self.server.RequestHandlerClass.state = self.state
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.apk = Path(self.temp_dir.name) / APK_NAME
        self.apk.write_bytes(b"new beta apk")
        self.body_file = Path(self.temp_dir.name) / "release-notes.md"
        self.body_file.write_text("new release notes", encoding="utf-8")
        self.api = GitHubApi(self.state.base_url, "test-token")

    def tearDown(self) -> None:
        self.api.close()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temp_dir.cleanup()

    def run_publish(self) -> BetaReleaseTransactionError:
        try:
            publish_beta_release(
                self.api,
                REPOSITORY,
                TAG,
                NEW_SHA,
                "Barcode Generator Beta v9.9.9 (new)",
                self.body_file.read_text(encoding="utf-8"),
                self.apk,
                hashlib.sha256(self.apk.read_bytes()).hexdigest(),
            )
        except BetaReleaseTransactionError as error:
            return error
        except Exception as error:
            self.fail(
                f"REST 模拟请求异常: {error}; requests={self.state.requests!r}; "
                f"server_errors={self.server.errors!r}; server_alive={self.thread.is_alive()}"
            )
        self.fail("发布事务应传播注入的 REST 故障")

    def assert_previous_release_restored(self, diagnostic: str = "") -> None:
        self.assertIsNotNone(self.state.release, diagnostic)
        assert self.state.release is not None
        self.assertEqual("Barcode Generator Beta v9.9.9", self.state.release["name"], diagnostic)
        self.assertEqual("old release notes", self.state.release["body"], diagnostic)
        self.assertEqual(OLD_SHA, self.state.tag_sha, diagnostic)
        assets_by_name = {asset["name"]: asset for asset in self.state.release["assets"]}
        self.assertEqual({APK_NAME, "checksums.txt"}, set(assets_by_name), diagnostic)
        self.assertEqual(b"old beta apk", self.state.asset_bytes[assets_by_name[APK_NAME]["id"]], diagnostic)
        self.assertEqual(b"keep me", self.state.asset_bytes[assets_by_name["checksums.txt"]["id"]], diagnostic)
        self.assertFalse(self.state.bad_auth)

    def test_build_output_sha256_mismatch_is_rejected_before_api_access(self):
        with self.assertRaisesRegex(ValueError, "does not match the build output"):
            publish_beta_release(
                self.api,
                REPOSITORY,
                TAG,
                NEW_SHA,
                "Barcode Generator Beta v9.9.9 (new)",
                self.body_file.read_text(encoding="utf-8"),
                self.apk,
                "0" * 64,
            )

        self.assertEqual([], self.state.requests)
        self.assert_previous_release_restored()

    def test_asset_upload_failure_restores_previous_release_state(self):
        self.state.fail_once = ("POST", f"/repos/{REPOSITORY}/releases/42/assets")

        result = self.run_publish()

        diagnostic = str(result) + repr(self.state.requests) + repr(self.server.errors)
        self.assert_previous_release_restored(diagnostic)
        self.assertIn("asset", str(result).lower(), diagnostic)

    def test_metadata_update_failure_restores_previous_release_state(self):
        self.state.fail_once = ("PATCH", f"/repos/{REPOSITORY}/releases/42")

        result = self.run_publish()

        diagnostic = str(result) + repr(self.state.requests) + repr(self.server.errors)
        self.assert_previous_release_restored(diagnostic)
        self.assertIn("metadata", str(result).lower(), diagnostic)

    def test_tag_move_failure_restores_previous_release_state(self):
        self.state.fail_once = ("PATCH", f"/repos/{REPOSITORY}/git/refs/tags/{TAG}")

        result = self.run_publish()

        diagnostic = str(result) + repr(self.state.requests) + repr(self.server.errors)
        self.assert_previous_release_restored(diagnostic)
        self.assertIn("tag", str(result).lower(), diagnostic)

    def test_first_release_tag_failure_removes_new_release_and_tag(self):
        self.state.release = None
        self.state.tag_sha = None
        self.state.fail_once = ("PATCH", f"/repos/{REPOSITORY}/git/refs/tags/{TAG}")

        result = self.run_publish()

        self.assertIsNone(self.state.release, str(result))
        self.assertIsNone(self.state.tag_sha, str(result))
        self.assertEqual({}, self.state.asset_bytes, str(result))

    def test_same_size_corrupt_apk_upload_is_rejected_and_rolled_back(self):
        self.state.corrupt_upload_once = True

        result = self.run_publish()

        diagnostic = str(result) + repr(self.state.requests) + repr(self.server.errors)
        self.assert_previous_release_restored(diagnostic)
        self.assertIn("sha-256", str(result).lower(), diagnostic)


if __name__ == "__main__":
    unittest.main()
