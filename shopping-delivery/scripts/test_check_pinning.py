"""Testes do check-pinning.py (revisão de segurança 3, M4/M7).

Uso: python3 -m unittest discover -s scripts -p 'test_check_pinning.py'
"""
import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-pinning.py")
spec = importlib.util.spec_from_file_location("check_pinning", SCRIPT)
pinning = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pinning)

SHA = "11d5960a326750d5838078e36cf38b85af677262"
DIGEST = "sha256:" + "a" * 64


class CheckPinningTest(unittest.TestCase):

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.write(".github/workflows/ci.yml", f"""
jobs:
  build:
    steps:
      - uses: actions/checkout@{SHA} # v4.4.0
      - uses: ./.github/actions/local
      - name: x
        uses: docker://alpine@{DIGEST}
""")
        self.write("shopping-delivery/shopping-delivery/Dockerfile",
                   f"FROM eclipse-temurin:17-jdk-alpine@{DIGEST} AS build\nRUN x\nFROM --platform=linux/amd64 eclipse-temurin:17-jre@{DIGEST}\nCOPY --from=build /a /a\n")
        self.write("shopping-delivery-frontend/Dockerfile",
                   f"FROM node:24-alpine@{DIGEST} AS Build\nFROM build AS again\nFROM nginx:1.30@{DIGEST}\n")
        self.write("shopping-delivery/deploy/docker-compose.prod.yml",
                   f"services:\n  db:\n    image: postgres:16-alpine@{DIGEST}\n  api:\n    build: .\n")
        self.write("api.gestor.financeiro/Dockerfile", f"FROM maven:3.9@{DIGEST} AS build\nFROM eclipse-temurin:17-jre@{DIGEST}\n")
        self.write("api.gestor.financeiro/docker-compose.yml", f"services:\n  db:\n    image: 'postgres:16@{DIGEST}'\n")
        self.write("shopping-delivery/load-tests/run.sh", f'#!/bin/bash\nK6_IMAGE="grafana/k6:1.3.0@{DIGEST}"   # comentário\n')

    def tearDown(self):
        shutil.rmtree(self.root)

    def write(self, rel, text):
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def test_everything_pinned(self):
        self.assertEqual(pinning.check(self.root), [])

    def test_unpinned_references_are_reported(self):
        self.write(".github/workflows/other.yaml",
                   "steps:\n  - uses: actions/setup-node@v4\n  - uses: actions/x@main\n  - uses: docker://alpine:3\n")
        self.write("shopping-delivery-frontend/Dockerfile", "FROM node:24-alpine AS build\nFROM nginxinc/nginx-unprivileged:1.27-alpine\n")
        self.write("shopping-delivery/deploy/docker-compose.prod.yml", "services:\n  db:\n    image: \"postgres:16-alpine\"\n")
        self.write("shopping-delivery/load-tests/run.sh", 'K6_IMAGE="grafana/k6:1.3.0"\n')
        problems = pinning.check(self.root)
        self.assertEqual(len(problems), 7, problems)
        joined = "\n".join(problems)
        for ref in ("actions/setup-node@v4", "actions/x@main", "docker://alpine:3", "node:24-alpine",
                    "nginx-unprivileged:1.27-alpine", "postgres:16-alpine", "grafana/k6:1.3.0"):
            self.assertIn(ref, joined)

    def test_short_sha_or_missing_file_fails(self):
        self.write(".github/workflows/ci.yml", "steps:\n  - uses: actions/checkout@11d5960\n")
        (self.root / "shopping-delivery-frontend/Dockerfile").unlink()
        problems = pinning.check(self.root)
        self.assertEqual(len(problems), 2, problems)
        self.assertTrue(any("não existe" in p for p in problems))

    def test_ci_reads_the_pinned_k6_image(self):
        """O CI extrai K6_IMAGE do run.sh com sed (ci.yml); um comentário no fim da linha deixava o valor vazio e o
        passo "k6 inspect" falhava (revisão 5). Roda o MESMO sed do ci.yml contra o run.sh real."""
        import re
        import subprocess
        root = SCRIPT.resolve().parents[2]
        ci = (root / ".github/workflows/ci.yml").read_text()
        m = re.search(r"K6_IMAGE=\$\(sed -n '([^']+)' run\.sh\)", ci)
        self.assertIsNotNone(m, "o ci.yml não lê mais K6_IMAGE do run.sh com sed: atualize este teste")
        out = subprocess.run(["sed", "-n", m.group(1), str(root / "shopping-delivery/load-tests/run.sh")],
                             capture_output=True, text=True, check=True).stdout.strip()
        self.assertRegex(out, r"^grafana/k6:[^\s@]+@sha256:[0-9a-f]{64}$")

    def test_real_repository_is_pinned(self):
        self.assertEqual(pinning.check(SCRIPT.resolve().parents[2]), [])


if __name__ == "__main__":
    unittest.main()
