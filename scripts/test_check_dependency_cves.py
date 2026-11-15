"""Testes do check-dependency-cves.py (sem rede: o OSV é simulado). python3 -m unittest test_check_dependency_cves.py"""
import importlib.util
import os
import unittest
from unittest import mock

_spec = importlib.util.spec_from_file_location(
    "check_cves", os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-dependency-cves.py"))
check = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(check)

DEP_LIST = """
The following files have been resolved:
   org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.55:compile -- module org.apache.tomcat.embed.core
   com.fasterxml.jackson.core:jackson-databind:jar:2.21.4:compile -- module com.fasterxml.jackson.databind
   org.springframework:spring-webmvc:jar:6.2.19:compile
   \x1b[36morg.postgresql:postgresql:jar:42.7.11:runtime\x1b[0m
   io.netty:netty-tcnative:jar:linux-x86_64:2.0.0:runtime
   org.springframework:spring-webmvc:jar:6.2.19:compile
   junit:junit:jar:4.13:test
"""


class ParseTest(unittest.TestCase):
    def test_parse_dependency_list(self):
        self.assertEqual(check.parse_dependency_list(DEP_LIST), [
            ("org.apache.tomcat.embed", "tomcat-embed-core", "10.1.55"),
            ("com.fasterxml.jackson.core", "jackson-databind", "2.21.4"),
            ("org.springframework", "spring-webmvc", "6.2.19"),
            ("org.postgresql", "postgresql", "42.7.11"),          # cores ANSI removidas
            ("io.netty", "netty-tcnative", "2.0.0"),              # classifier ignorado
        ])  # duplicatas e escopo test ficam de fora

    def test_allowlist_requires_reason(self):
        self.assertEqual(check.load_allowlist("# comentário\n\nGHSA-1  # não usado\n"), {"GHSA-1": "não usado"})
        with self.assertRaises(ValueError):
            check.load_allowlist("GHSA-2\n")


class FindingsTest(unittest.TestCase):
    deps = [("g", "a", "1"), ("g", "b", "2"), ("g", "c", "3")]
    osv = [{"vulns": [{"id": "GHSA-A"}, {"id": "GHSA-OK"}]}, {}, {"vulns": []}]

    def test_split_blocked_and_allowed(self):
        blocked, allowed = check.find_findings(self.deps, self.osv, {"GHSA-OK": "motivo"})
        self.assertEqual(blocked, [(("g", "a", "1"), "GHSA-A")])
        self.assertEqual(allowed, [(("g", "a", "1"), "GHSA-OK")])

    def test_main_fails_only_with_unallowed(self):
        here = os.path.dirname(os.path.abspath(__file__))
        deps_file = os.path.join(here, "_deps_test.txt")
        allow_file = os.path.join(here, "_allow_test.txt")
        try:
            with open(deps_file, "w") as f:
                f.write("   g:a:jar:1:compile\n")
            with open(allow_file, "w") as f:
                f.write("GHSA-X  # não se aplica\n")
            with mock.patch.object(check, "query_osv", return_value=[{"vulns": [{"id": "GHSA-X"}]}]), \
                 mock.patch("builtins.print"):
                self.assertEqual(check.main(["--deps", deps_file, "--allowlist", allow_file]), 0)
            with mock.patch.object(check, "query_osv", return_value=[{"vulns": [{"id": "GHSA-Y"}]}]), \
                 mock.patch.object(check, "describe", return_value=""), mock.patch("builtins.print"):
                self.assertEqual(check.main(["--deps", deps_file, "--allowlist", allow_file]), 1)
            with open(deps_file, "w") as f:
                f.write("nada aqui\n")
            with mock.patch("builtins.print"):
                self.assertEqual(check.main(["--deps", deps_file, "--allowlist", allow_file]), 2)
        finally:
            for p in (deps_file, allow_file):
                if os.path.exists(p):
                    os.unlink(p)


if __name__ == "__main__":
    unittest.main()
