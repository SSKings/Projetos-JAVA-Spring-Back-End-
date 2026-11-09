"""Testes do run-ci-job.py: roda os passos de um job do ci.yml localmente (lição do PR da revisão 5).

Uso: python3 -m unittest discover -s scripts -p 'test_run_ci_job.py'
"""
import importlib.util
import io
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("run-ci-job.py")
spec = importlib.util.spec_from_file_location("run_ci_job", SCRIPT)
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)

WORKFLOW = """
name: CI
jobs:
  demo:
    name: Demo
    defaults:
      run:
        working-directory: app
    steps:
      - uses: actions/checkout@0000000000000000000000000000000000000000
      - name: Passo que passa
        run: echo ok > passou.txt
      - name: Passo em outro diretório
        working-directory: outro
        run: test -f marcador
      - name: Deixa um processo para trás
        run: |
          sleep 300 &
          echo $! > ../sleep.pid
      - name: Passo que falha
        run: |
          echo "mensagem do erro"
          false
      - name: Pipe que falha no meio
        run: false | cat
"""


class RunCiJobTest(unittest.TestCase):

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        (self.root / ".github" / "workflows").mkdir(parents=True)
        (self.root / ".github" / "workflows" / "ci.yml").write_text(WORKFLOW)
        (self.root / "app").mkdir()
        (self.root / "outro").mkdir()
        (self.root / "outro" / "marcador").write_text("")

    def tearDown(self):
        shutil.rmtree(self.root)

    def run_job(self, job="demo"):
        out = io.StringIO()
        return ci.run_steps(self.root, job, out), out.getvalue()

    def test_runs_every_step_in_order_and_reports_failures(self):
        code, out = self.run_job()
        self.assertEqual(code, 1)
        self.assertRegex(out, r"pulado\s+1\. actions/checkout")
        self.assertRegex(out, r"OK\s+2\. Passo que passa")
        self.assertTrue((self.root / "app" / "passou.txt").exists())        # working-directory padrão do job
        self.assertRegex(out, r"OK\s+3\. Passo em outro diretório")
        self.assertRegex(out, r"FALHA\s+5\. Passo que falha")
        self.assertIn("mensagem do erro", out)                               # mostra a saída do passo
        self.assertRegex(out, r"FALHA\s+6\. Pipe que falha no meio")              # pipefail, como no GitHub
        self.assertIn("2 passo(s) com falha", out)

    def test_kills_processes_left_by_a_step(self):
        _, out = self.run_job()
        self.assertIn("1 processo(s) deixado(s) pelo passo encerrado(s)", out)
        pid = int((self.root / "sleep.pid").read_text())
        time.sleep(0.5)
        alive = subprocess.run(["ps", "-p", str(pid)], capture_output=True).returncode == 0
        self.assertFalse(alive, "o 'sleep 300 &' deveria ter sido encerrado ao fim do passo")

    def test_unknown_job(self):
        code, out = self.run_job("nao-existe")
        self.assertEqual(code, 2)
        self.assertIn("Jobs: demo", out)

    def test_real_workflow_jobs_have_existing_working_directories(self):
        """Cada job do ci.yml real tem passos e aponta para diretórios que existem (o script roda neles)."""
        root = SCRIPT.resolve().parents[2]
        jobs = ci.load_jobs(root)
        self.assertIn("shopping-delivery", jobs)
        for job_id, job in jobs.items():
            self.assertTrue(job.get("steps"), job_id)
            default_wd = job.get("defaults", {}).get("run", {}).get("working-directory", ".")
            for step in job["steps"]:
                if "run" in step:
                    wd = root / step.get("working-directory", default_wd)
                    self.assertTrue(wd.is_dir(), f"{job_id}: {wd} não existe")


if __name__ == "__main__":
    unittest.main()
