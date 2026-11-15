"""Logs de produção (deploy/docker-compose.prod.yml e application-prod.yml) — roda no CI.

Achados da análise de logs de 2026-11-15:
- o driver json-file do Docker guarda os logs para sempre: um dia de ataque (ou um erro em loop) enche o disco do
  servidor, e com o disco cheio o PostgreSQL para. Todo serviço precisa de max-size e max-file;
- em produção não há agente nem Loki: os logs ficam no `docker logs`. Em JSON (ECS), dá para filtrar sem regex e a
  stack trace vem num campo só.

    python3 -m unittest discover -s scripts -p 'test_production_logging.py'
"""
import os
import re
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
COMPOSE = os.path.join(ROOT, "deploy", "docker-compose.prod.yml")
PROD_YML = os.path.join(ROOT, "backend", "src", "main", "resources", "application-prod.yml")


def services(compose):
    """{nome: bloco} dos serviços de primeiro nível (sem PyYAML: o CI usa só a biblioteca padrão)."""
    body = compose.split("\nservices:\n", 1)[1]
    body = re.split(r"\n(?=[a-z])", body, maxsplit=1)[0]       # até a próxima chave de topo (networks:)
    found = {}
    for m in re.finditer(r"^  ([a-z][a-z0-9_-]*):\n((?:(?:    .*)?\n)+)", body, re.M):
        found[m.group(1)] = m.group(2)
    return found


class ComposeLogRotationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(COMPOSE, encoding="utf-8") as f:
            cls.compose = f.read()

    def test_logging_anchor_limits_size_and_files(self):
        m = re.search(r"^x-logging: &logging\n((?:  .*\n)+)", self.compose, re.M)
        self.assertIsNotNone(m, "x-logging: &logging no topo do compose")
        block = m.group(1)
        self.assertRegex(block, r'driver: json-file')
        size = re.search(r'max-size: "(\d+)m"', block)
        files = re.search(r'max-file: "(\d+)"', block)
        self.assertTrue(size and files, "max-size em MB e max-file")
        self.assertLessEqual(int(size.group(1)) * int(files.group(1)), 500, "teto por container: até 500 MB")

    def test_every_service_uses_the_anchor(self):
        svcs = services(self.compose)
        self.assertEqual(set(svcs), {"db", "api", "web"})
        for name, block in svcs.items():
            self.assertIn("    logging: *logging\n", block, f"{name} sem rotação de log")

    def test_parser_sees_a_service_without_logging(self):
        broken = self.compose.replace("  web:\n    logging: *logging\n", "  web:\n", 1)
        self.assertNotEqual(broken, self.compose)
        self.assertNotIn("logging: *logging", services(broken)["web"])


class StructuredLogsTest(unittest.TestCase):

    def test_prod_profile_writes_ecs_json(self):
        with open(PROD_YML, encoding="utf-8") as f:
            prod = f.read()
        self.assertRegex(prod, r"(?m)^logging:\n(?:  .*\n|\s*#.*\n)*  structured:\n    format:\n      console: ecs\n")


if __name__ == "__main__":
    unittest.main()
