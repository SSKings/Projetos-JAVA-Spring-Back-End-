"""Testes do telegram-admin-bot-setup.sh contra um servidor falso da Bot API (sem internet, sem token real).

Uso: python3 -m unittest discover -s scripts -p 'test_telegram_admin_bot_setup.py'
"""
import json
import os
import shutil
import stat
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

SCRIPT = Path(__file__).with_name("telegram-admin-bot-setup.sh")
TOKEN = "123456789:AAH-valid_token_0123456789abcdefghij"
OTHER = "987654321:BBH-other_token_0123456789abcdefghij"


class FakeTelegram(BaseHTTPRequestHandler):
    chats = []        # chats privados que "mandaram /start"
    sent = []         # corpo dos sendMessage recebidos

    def log_message(self, *args):
        pass

    def do_POST(self):
        self.do_GET()

    def do_GET(self):
        token, _, method = self.path.removeprefix("/bot").partition("/")
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length)) if length else {}
        if token not in (TOKEN, OTHER):
            return self.reply(401, {"ok": False, "description": "Unauthorized"})
        if method == "getMe":
            return self.reply(200, {"ok": True, "result": {"username": "LojaAdminBot"}})
        if method == "getUpdates":
            result = [{"update_id": i, "message": {"text": "/start", "chat": {"id": cid, "type": "private", "first_name": name}}}
                      for i, (cid, name) in enumerate(self.chats)]
            result.append({"update_id": 99, "message": {"text": "oi", "chat": {"id": -100, "type": "group", "title": "Grupo"}}})
            return self.reply(200, {"ok": True, "result": result})
        if method == "sendMessage":
            FakeTelegram.sent.append(body)
            return self.reply(200, {"ok": True, "result": {}})
        self.reply(404, {"ok": False, "description": "Not Found"})

    def reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class TelegramAdminBotSetupTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeTelegram)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.api = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        # cópia isolada: o script grava em <pasta do script>/../.env.telegram-admin-bot
        self.root = Path(tempfile.mkdtemp())
        (self.root / "scripts").mkdir()
        shutil.copy(SCRIPT, self.root / "scripts" / SCRIPT.name)
        self.env_file = self.root / ".env.telegram-admin-bot"
        FakeTelegram.chats = [(555, "Ana")]
        FakeTelegram.sent = []

    def tearDown(self):
        shutil.rmtree(self.root)

    def run_setup(self, *answers):
        return subprocess.run(["bash", str(self.root / "scripts" / SCRIPT.name)], input="\n".join(answers) + "\n",
                              capture_output=True, text=True, timeout=60,
                              env={**os.environ, "TELEGRAM_API": self.api})

    def env(self):
        return dict(line.split("=", 1) for line in self.env_file.read_text().splitlines() if not line.startswith("#"))

    def test_happy_path_writes_private_env_file(self):
        result = self.run_setup(TOKEN, "", "")   # e-mail padrão, Enter depois do /start

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.env(), {"TELEGRAM_ADMIN_BOT_ENABLED": "true", "TELEGRAM_ADMIN_BOT_TOKEN": TOKEN,
                                      "TELEGRAM_ADMIN_BOT_ADMINS": "555:admin@shopeasy.com"})
        self.assertEqual(stat.S_IMODE(self.env_file.stat().st_mode), 0o600)
        self.assertEqual(FakeTelegram.sent[0]["chat_id"], 555)
        self.assertIn("admin@shopeasy.com", FakeTelegram.sent[0]["text"])
        self.assertNotIn(TOKEN, result.stdout + result.stderr)   # o token nunca aparece
        self.assertIn("@LojaAdminBot", result.stdout)

    def test_same_bot_adds_admin_and_replaces_same_chat(self):
        self.run_setup(TOKEN, "", "")
        FakeTelegram.chats = [(666, "Bia")]
        self.run_setup(TOKEN, "Bia@Loja.com", "")
        self.assertEqual(self.env()["TELEGRAM_ADMIN_BOT_ADMINS"], "555:admin@shopeasy.com,666:bia@loja.com")

        FakeTelegram.chats = [(555, "Ana")]
        self.run_setup(TOKEN, "ana@loja.com", "")
        self.assertEqual(self.env()["TELEGRAM_ADMIN_BOT_ADMINS"], "666:bia@loja.com,555:ana@loja.com")

    def test_several_chats_lets_user_choose(self):
        FakeTelegram.chats = [(555, "Ana"), (666, "Bia")]
        result = self.run_setup(TOKEN, "", "", "2")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.env()["TELEGRAM_ADMIN_BOT_ADMINS"], "666:admin@shopeasy.com")

    def test_manual_chat_id_when_api_already_reads_the_bot(self):
        FakeTelegram.chats = []   # só o grupo: ignorado
        result = self.run_setup(TOKEN, "", "", "777")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.env()["TELEGRAM_ADMIN_BOT_ADMINS"], "777:admin@shopeasy.com")

    def test_other_bot_existing_file_is_kept_unless_confirmed(self):
        self.run_setup(OTHER, "", "")
        result = self.run_setup(TOKEN, "", "", "n")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.env()["TELEGRAM_ADMIN_BOT_TOKEN"], OTHER)

        self.run_setup(TOKEN, "", "", "s")
        self.assertEqual(self.env()["TELEGRAM_ADMIN_BOT_TOKEN"], TOKEN)

    def test_rejects_alerts_bot_bad_token_and_bad_email(self):
        (self.root / "observability-stack").mkdir()
        (self.root / "observability-stack" / ".env").write_text(f"TELEGRAM_BOT_TOKEN={TOKEN}\n")
        cases = [((TOKEN,), "bot dos alertas"),
                 (("abc",), "não parece um token"),
                 (("123456789:AAH-revoked_token_0123456789abcdefgh",), "recusou o token"),
                 ((OTHER, "sem-arroba"), "E-mail inválido")]
        for answers, message in cases:
            with self.subTest(message):
                result = self.run_setup(*answers)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr)
                self.assertFalse(self.env_file.exists())


if __name__ == "__main__":
    unittest.main()
