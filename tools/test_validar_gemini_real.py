"""Testes do verificador; o servidor desta suíte é um stub, sem chamadas Google."""
import contextlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest

spec = importlib.util.spec_from_file_location("gemini_smoke", Path(__file__).with_name("validar-gemini-real.py"))
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
PROTOCOL = "00000000-0000-4000-8000-000000000001"


@contextlib.contextmanager
def backend(source="IA_GEMINI", priority="ALTA", mismatch=False, http_error=False):
    row = {"protocolo": PROTOCOL, "tipoAnalise": source, "prioridade": priority,
           "categoria": "Vazamento intenso", "justificativa": "Relato sintético indica rompimento."}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def reply(self, status, body, cookie=False):
            content = json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(content)))
            if cookie:
                self.send_header("Set-Cookie", "SESSION=synthetic-session; Path=/; HttpOnly")
            self.end_headers()
            self.wfile.write(content)

        def do_GET(self):
            if self.path == "/actuator/health":
                self.reply(200, {"status": "UP"})
            elif self.path == "/api/auth/csrf":
                self.reply(200, {"headerName": "X-CSRF-TOKEN", "token": "synthetic-csrf"}, cookie=True)
            elif self.path == "/api/ocorrencias/protocolo/" + PROTOCOL:
                self.reply(200, {**row, "prioridade": "BAIXA"} if mismatch else row)
            else:
                self.reply(404, {})

        def do_POST(self):
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            if (self.headers.get("X-CSRF-TOKEN") != "synthetic-csrf"
                    or "SESSION=synthetic-session" not in self.headers.get("Cookie", "")):
                self.reply(403, {})
            elif http_error:
                self.reply(429, {"message": "MUST-NOT-LEAK"})
            else:
                self.reply(201, {**row, "id": 1, "tokenUpload": "MUST-NOT-LEAK"})

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield "http://127.0.0.1:" + str(server.server_port)
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


class ValidationTest(unittest.TestCase):
    def execute(self, **scenario):
        with backend(**scenario) as url, tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "result.json"
            log = io.StringIO()
            with contextlib.redirect_stdout(log):
                code = smoke.main(["--base-url", url, "--wait-seconds", "0", "--output", str(output)])
            text = log.getvalue()
            self.assertNotIn("MUST-NOT-LEAK", text)
            self.assertNotIn(PROTOCOL, text)
            self.assertEqual(json.loads(text), json.loads(output.read_text()))
            return code, json.loads(text)

    def test_success_checks_session_schema_and_persistence_without_leaking(self):
        code, result = self.execute()
        self.assertEqual(code, 0)
        self.assertTrue(result["realGeminiConfirmed"])
        self.assertEqual(result["tipoAnalise"], "IA_GEMINI")

    def test_fallback_never_passes_real_validation(self):
        for source in ("REGRAS", "REGRAS_CONTINGENCIA"):
            with self.subTest(source=source):
                code, result = self.execute(source=source)
                self.assertEqual(code, 1)
                self.assertFalse(result["realGeminiConfirmed"])

    def test_invalid_enum_and_type_are_rejected(self):
        for priority in ("URGENTE", [], None):
            with self.subTest(priority=priority):
                code, result = self.execute(priority=priority)
                self.assertEqual(code, 1)
                self.assertFalse(result["realGeminiConfirmed"])

    def test_persistence_mismatch_is_rejected(self):
        code, result = self.execute(mismatch=True)
        self.assertEqual(code, 1)
        self.assertFalse(result["realGeminiConfirmed"])

    def test_http_failure_omits_response_body(self):
        code, result = self.execute(http_error=True)
        self.assertEqual(code, 1)
        self.assertIn("HTTP 429", result["message"])


if __name__ == "__main__":
    unittest.main()
