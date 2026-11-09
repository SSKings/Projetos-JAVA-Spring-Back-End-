#!/usr/bin/env python3
"""Falha se algo que vai para produção ou roda no CI não estiver fixado por hash (revisão de segurança 3, M4/M7).

- GitHub Actions: `uses: dono/repo@<SHA de 40 caracteres>` (a tag v4 pode ser movida para um commit malicioso).
- Dockerfiles e compose de produção: `imagem:tag@sha256:<digest>` (a tag pode ser republicada).

O Dependabot atualiza SHAs e digests (.github/dependabot.yml). Ferramentas locais (observability-stack, load-tests)
ficam de fora: não vão para produção.

Uso: python3 shopping-delivery/scripts/check-pinning.py [raiz-do-repositório]
"""
import re
import sys
from pathlib import Path

USES = re.compile(r"^\s*-?\s*uses:\s*([^\s#]+)")
FROM = re.compile(r"^\s*FROM\s+(?:--platform=\S+\s+)?(\S+)", re.IGNORECASE)
IMAGE = re.compile(r"^\s*image:\s*['\"]?([^\s'\"#]+)")
SHA = re.compile(r"@[0-9a-f]{40}$")
DIGEST = re.compile(r"@sha256:[0-9a-f]{64}$")

PRODUCTION_FILES = [
    "shopping-delivery/shopping-delivery/Dockerfile",
    "shopping-delivery-frontend/Dockerfile",
    "shopping-delivery/deploy/docker-compose.prod.yml",
    "api.gestor.financeiro/Dockerfile",
    "api.gestor.financeiro/docker-compose.yml",
]


def check_workflow(path: Path) -> list[str]:
    problems = []
    for n, line in enumerate(path.read_text().splitlines(), 1):
        m = USES.match(line)
        if not m:
            continue
        ref = m.group(1)
        if ref.startswith("./"):
            continue                                   # action local, versionada com o repositório
        if ref.startswith("docker://"):
            ok = bool(DIGEST.search(ref))
        else:
            ok = bool(SHA.search(ref))
        if not ok:
            problems.append(f"{path}:{n}: action sem SHA fixo: {ref}")
    return problems


def check_images(path: Path) -> list[str]:
    problems, stages = [], set()
    for n, line in enumerate(path.read_text().splitlines(), 1):
        m = FROM.match(line) if path.name == "Dockerfile" else IMAGE.match(line)
        if not m:
            continue
        image = m.group(1)
        alias = re.search(r"\s+AS\s+(\S+)", line, re.IGNORECASE)
        if image.lower() in stages or image == "scratch":
            pass                                       # estágio anterior do mesmo Dockerfile
        elif not DIGEST.search(image):
            problems.append(f"{path}:{n}: imagem sem digest: {image}")
        if alias:
            stages.add(alias.group(1).lower())
    return problems


def check(root: Path) -> list[str]:
    problems = []
    for wf in sorted((root / ".github" / "workflows").glob("*.y*ml")):
        problems += check_workflow(wf)
    for rel in PRODUCTION_FILES:
        path = root / rel
        if path.exists():
            problems += check_images(path)
        else:
            problems.append(f"{path}: arquivo esperado não existe (atualize PRODUCTION_FILES)")
    return problems


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
    problems = check(root)
    for p in problems:
        print(p)
    if problems:
        print(f"\n{len(problems)} referência(s) sem hash. Fixe por SHA/digest (ver docs/security-review.md, revisão 3).")
        return 1
    print("OK: actions fixadas por SHA e imagens de produção por digest.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
