"""Testes do deploy/backup-db.sh e deploy/restore-db.sh (revisão de segurança 3, M6).

Sem banco nem Docker de verdade: um `docker` falso no PATH faz o papel do container "db" (pg_dump devolve um
conteúdo conhecido; pg_restore --list confere o que recebeu; pg_restore grava o que recebeu num arquivo).
O openssl é o real — o que se testa é que o dump fica cifrado em disco e volta igual na restauração.

Uso: python3 -m unittest discover -s scripts -p 'test_backup_db.py'
"""
import os
import shutil
import stat
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

DEPLOY = Path(__file__).resolve().parent.parent / "deploy"
DUMP = "PGDMP-conteudo-com-dados-pessoais: ana@exemplo.com, CPF 123"
PASSPHRASE = "frase-de-teste-com-mais-de-20-caracteres"

FAKE_DOCKER = f"""#!/usr/bin/env bash
# docker compose ... exec -T db <comando>
args="$*"
if [[ "$args" == *pg_dump* ]]; then
  [ -n "$FAKE_DUMP_FAIL" ] && {{ echo "pg_dump: connection refused" >&2; exit 1; }}
  printf '%s' '{DUMP}'
elif [[ "$args" == *"pg_restore --list"* ]]; then
  [ "$(cat)" = '{DUMP}' ] || exit 1
elif [[ "$args" == *pg_restore* ]]; then
  cat > "$FAKE_RESTORED"
  echo "$args" > "$FAKE_RESTORED.args"
else
  exit 2
fi
"""


class BackupScriptsTest(unittest.TestCase):

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.deploy = self.tmp / "deploy"
        self.deploy.mkdir()
        for name in ("backup-db.sh", "restore-db.sh"):
            shutil.copy(DEPLOY / name, self.deploy / name)
        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        docker = bin_dir / "docker"
        docker.write_text(FAKE_DOCKER)
        docker.chmod(0o755)
        self.backups = self.tmp / "backups"
        self.write_env(BACKUP_PASSPHRASE=PASSPHRASE)
        self.env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}",
                    "FAKE_RESTORED": str(self.tmp / "restored")}
        for key in ("BACKUP_PASSPHRASE", "BACKUP_DIR", "BACKUP_KEEP_DAYS", "ENV_FILE", "RESTORE_CONFIRM"):
            self.env.pop(key, None)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def write_env(self, **extra):
        values = {"DATABASE_USERNAME": "shopeasy_app", "DATABASE_MIGRATION_USERNAME": "shopeasy_owner", "DATABASE_NAME": "shopeasy",
                  "BACKUP_DIR": str(self.backups), "BACKUP_KEEP_DAYS": "14", **extra}
        # linha maliciosa: o script não pode executar o .env.prod (lê com grep, não com source)
        text = "\n".join(f"{k}={v}" for k, v in values.items()) + f"\n$(touch {self.tmp}/pwned)\n"
        (self.deploy / ".env.prod").write_text(text)

    def run_script(self, name, *args, **env):
        return subprocess.run([str(self.deploy / name), *args], env={**self.env, **env},
                              capture_output=True, text=True, timeout=60)

    def backup_files(self):
        return sorted(self.backups.glob("shopeasy-*.dump.enc")) if self.backups.exists() else []

    def test_backup_is_encrypted_private_and_restorable(self):
        r = self.run_script("backup-db.sh")
        self.assertEqual(r.returncode, 0, r.stderr)
        [backup] = self.backup_files()
        data = backup.read_bytes()
        self.assertTrue(data.startswith(b"Salted__"))
        self.assertNotIn(b"ana@exemplo.com", data)
        self.assertEqual(stat.S_IMODE(backup.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(self.backups.stat().st_mode), 0o700)
        self.assertFalse(list(self.backups.glob("*.partial")))
        self.assertFalse((self.tmp / "pwned").exists())

        r = self.run_script("restore-db.sh", str(backup), RESTORE_CONFIRM="shopeasy")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual((self.tmp / "restored").read_text(), DUMP)
        args = (self.tmp / "restored.args").read_text()
        self.assertIn("--single-transaction", args)
        self.assertIn("-U shopeasy_owner", args)   # revisão 5: restaura com o dono, não com o usuário da API

    def test_missing_or_short_passphrase_refuses(self):
        self.write_env(BACKUP_PASSPHRASE="curta")
        r = self.run_script("backup-db.sh")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("BACKUP_PASSPHRASE", r.stderr)
        self.assertEqual(self.backup_files(), [])

    def test_dump_failure_leaves_no_file(self):
        r = self.run_script("backup-db.sh", FAKE_DUMP_FAIL="1")
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(self.backup_files(), [])
        self.assertFalse(list(self.backups.glob("*.partial")))

    def test_old_backups_are_removed(self):
        self.backups.mkdir(mode=0o700)
        old = self.backups / "shopeasy-20260101T030000Z.dump.enc"
        recent = self.backups / "shopeasy-20261101T030000Z.dump.enc"
        other = self.backups / "outra-coisa.txt"
        for f in (old, recent, other):
            f.write_text("x")
        twenty_days_ago = time.time() - 20 * 86400
        os.utime(old, (twenty_days_ago, twenty_days_ago))
        os.utime(other, (twenty_days_ago, twenty_days_ago))

        r = self.run_script("backup-db.sh")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertFalse(old.exists())
        self.assertTrue(recent.exists())
        self.assertTrue(other.exists())        # só apaga os próprios backups
        self.assertEqual(len(self.backup_files()), 2)

    def test_restore_requires_confirmation_and_right_passphrase(self):
        self.assertEqual(self.run_script("backup-db.sh").returncode, 0)
        [backup] = self.backup_files()

        r = self.run_script("restore-db.sh", str(backup), RESTORE_CONFIRM="outro-banco")
        self.assertNotEqual(r.returncode, 0)
        self.assertFalse((self.tmp / "restored").exists())

        r = self.run_script("restore-db.sh", str(backup), RESTORE_CONFIRM="shopeasy",
                            BACKUP_PASSPHRASE="senha-errada-com-mais-de-20-caracteres")
        self.assertNotEqual(r.returncode, 0)
        restored = self.tmp / "restored"
        self.assertNotEqual(restored.read_bytes() if restored.exists() else b"", DUMP.encode())


if __name__ == "__main__":
    unittest.main()
