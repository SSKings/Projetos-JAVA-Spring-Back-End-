#!/usr/bin/env python3
"""
Verifica as dependências de runtime do back-end contra o banco OSV (osv.dev) e falha se houver
vulnerabilidade conhecida que não esteja na allowlist (cve-allowlist.txt, com justificativa).

Por quê: o Dependabot só atualiza o spring-boot-starter-parent; Tomcat, Jackson etc. vêm do BOM do Spring Boot
e ficaram com CVEs corrigidos sem ninguém perceber (docs/security-review.md, revisão 2).

Uso (no CI, a partir de shopping-delivery/shopping-delivery):
    python3 ../scripts/check-dependency-cves.py               # roda ./mvnw dependency:list
    python3 ../scripts/check-dependency-cves.py --deps arq.txt  # usa uma lista já gerada
Testes: python3 -m unittest ../scripts/test_check_dependency_cves.py
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/{}"
DEP_LINE = re.compile(r"^\s*([\w.\-]+):([\w.\-]+):jar:(?:[\w.\-]+:)?([\w.\-]+):(compile|runtime)")


def parse_dependency_list(text):
    """Linhas do `mvn dependency:list` → lista de (groupId, artifactId, versão), sem duplicatas."""
    seen, deps = set(), []
    for line in text.splitlines():
        m = DEP_LINE.match(re.sub(r"\x1b\[[0-9;]*m", "", line))
        if m:
            dep = (m.group(1), m.group(2), m.group(3))
            if dep not in seen:
                seen.add(dep)
                deps.append(dep)
    return deps


def load_allowlist(text):
    """`GHSA-xxxx  # motivo` por linha → {id: motivo}. Entradas sem motivo são recusadas."""
    allow = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        vuln_id, _, reason = line.partition("#")
        vuln_id, reason = vuln_id.strip(), reason.strip()
        if not reason:
            raise ValueError(f"allowlist: '{vuln_id}' precisa de um motivo após '#'")
        allow[vuln_id] = reason
    return allow


def find_findings(deps, osv_results, allowlist):
    """Cruza dependências com as respostas do OSV → (não permitidas, permitidas)."""
    blocked, allowed = [], []
    for dep, result in zip(deps, osv_results):
        for vuln in result.get("vulns") or []:
            entry = (dep, vuln["id"])
            (allowed if vuln["id"] in allowlist else blocked).append(entry)
    return blocked, allowed


def query_osv(deps):
    body = {"queries": [{"package": {"ecosystem": "Maven", "name": f"{g}:{a}"}, "version": v} for g, a, v in deps]}
    req = urllib.request.Request(OSV_BATCH, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)["results"]


def describe(vuln_id):
    try:
        with urllib.request.urlopen(OSV_VULN.format(vuln_id), timeout=30) as resp:
            d = json.load(resp)
        sev = (d.get("database_specific") or {}).get("severity", "?")
        cves = ",".join(a for a in d.get("aliases", []) if a.startswith("CVE"))
        return f"[{sev}] {cves} {d.get('summary', '')[:100]}"
    except Exception:  # descrição é só conveniência
        return ""


def maven_dependency_list():
    with tempfile.NamedTemporaryFile("r", suffix=".txt", delete=False) as out:
        path = out.name
    try:
        subprocess.run(["./mvnw", "-q", "-B", "dependency:list", "-DincludeScope=runtime", f"-DoutputFile={path}"],
                       check=True)
        with open(path) as f:
            return f.read()
    finally:
        os.unlink(path)


def read_text(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def main(argv=None):
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--deps", help="saída de `mvn dependency:list` (senão roda ./mvnw)")
    ap.add_argument("--allowlist", default=os.path.join(here, "cve-allowlist.txt"))
    args = ap.parse_args(argv)

    text = read_text(args.deps) if args.deps else maven_dependency_list()
    deps = parse_dependency_list(text)
    if not deps:
        print("nenhuma dependência encontrada na lista", file=sys.stderr)
        return 2
    allowlist = load_allowlist(read_text(args.allowlist)) if os.path.exists(args.allowlist) else {}
    blocked, allowed = find_findings(deps, query_osv(deps), allowlist)

    for (g, a, v), vid in allowed:
        print(f"permitido  {g}:{a}:{v}  {vid}  — {allowlist[vid]}")
    for (g, a, v), vid in blocked:
        print(f"VULNERÁVEL {g}:{a}:{v}  {vid}  {describe(vid)}")
    print(f"{len(deps)} dependências verificadas; {len(blocked)} vulnerabilidade(s) sem justificativa")
    if blocked:
        print("Atualize a versão (no pom.xml, propriedades do BOM) ou, se não se aplicar, registre em "
              "cve-allowlist.txt com o motivo.")
    return 1 if blocked else 0


if __name__ == "__main__":
    sys.exit(main())
