#!/usr/bin/env python3
"""Roda localmente, na ordem, todos os passos `run:` de um job do CI (.github/workflows/ci.yml).

Por quê: no PR da revisão de segurança 5, os testes passavam aqui, mas um passo do CI que ninguém rodou localmente
("k6 inspect") quebrou por um comentário no fim de uma linha do load-tests/run.sh. Rodar "os testes" não basta: antes
do PR, rode o JOB inteiro, lido do próprio ci.yml — assim nenhum passo fica de fora, nem os que forem criados depois.

    python3 shopping-delivery/scripts/run-ci-job.py --list            # jobs do ci.yml
    python3 shopping-delivery/scripts/run-ci-job.py shopping-delivery # roda o job

- Passos `uses:` (checkout, setup-java, setup-node) são pulados: o ambiente local faz esse papel (Java 17, Node, Docker).
- Cada passo roda em `bash -eo pipefail`, no working-directory dele (ou no padrão do job), como no GitHub.
- Processos que um passo deixa rodando (ex.: a API do smoke test, iniciada com `&`) são encerrados ao fim do passo,
  pelo grupo de processos do passo — sem `pkill` por nome, que pode acertar o processo errado.
- Sai com código 1 se algum passo falhar e mostra o fim da saída dele.
"""
import argparse
import os
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover - depende do ambiente
    sys.exit("PyYAML não encontrado: pip install pyyaml (ou apt install python3-yaml)")

ROOT = Path(__file__).resolve().parents[2]
TAIL = 3000


def load_jobs(root: Path) -> dict:
    workflow = yaml.safe_load((root / ".github" / "workflows" / "ci.yml").read_text())
    return workflow["jobs"]


def run_steps(root: Path, job_id: str, out=sys.stdout) -> int:
    jobs = load_jobs(root)
    if job_id not in jobs:
        print(f"Job '{job_id}' não existe no ci.yml. Jobs: {', '.join(jobs)}", file=out)
        return 2
    job = jobs[job_id]
    default_wd = job.get("defaults", {}).get("run", {}).get("working-directory", ".")
    failed = 0
    for n, step in enumerate(job["steps"], 1):
        name = step.get("name") or step.get("uses") or step.get("run", "")[:50]
        if "run" not in step:
            print(f"  pulado  {n:2}. {name} (uses: o ambiente local faz esse papel)", file=out, flush=True)
            continue
        wd = root / step.get("working-directory", default_wd)
        start = time.time()
        # Saída num arquivo, não num pipe: um processo deixado em segundo plano (ex.: a API do smoke, com "&") herda a
        # saída e manteria o pipe aberto — esperar o fim do pipe travaria até ele morrer. Espera-se só o bash do passo.
        with tempfile.TemporaryFile(mode="w+") as log:
            proc = subprocess.Popen(["bash", "-eo", "pipefail", "-c", step["run"]], cwd=wd, start_new_session=True,
                                    stdout=log, stderr=subprocess.STDOUT, text=True)
            proc.wait()
            leftovers = stop_group(proc.pid)
            log.seek(0)
            output = log.read()
        ok = proc.returncode == 0
        failed += not ok
        extra = f"; {leftovers} processo(s) deixado(s) pelo passo encerrado(s)" if leftovers else ""
        print(f"  {'OK   ' if ok else 'FALHA'}   {n:2}. {name} ({time.time() - start:.0f}s{extra})", file=out, flush=True)
        if not ok:
            print("        " + output[-TAIL:].replace("\n", "\n        "), file=out)
    print(f"\n{failed} passo(s) com falha no job '{job_id}'", file=out)
    return 1 if failed else 0


def stop_group(pgid: int) -> int:
    """Encerra o que ficou vivo no grupo de processos do passo (SIGTERM, depois SIGKILL). Devolve quantos havia."""
    alive = group_members(pgid)
    if not alive:
        return 0
    for sig, wait in ((signal.SIGTERM, 10), (signal.SIGKILL, 5)):
        try:
            os.killpg(pgid, sig)
        except ProcessLookupError:
            break
        deadline = time.time() + wait
        while time.time() < deadline and group_members(pgid):
            time.sleep(0.2)
        if not group_members(pgid):
            break
    return len(alive)


def group_members(pgid: int) -> list[int]:
    out = subprocess.run(["ps", "-o", "pid=", "-g", str(pgid)], capture_output=True, text=True).stdout
    return [int(p) for p in out.split()]


def main() -> int:
    parser = argparse.ArgumentParser(description="Roda localmente os passos de um job do ci.yml")
    parser.add_argument("job", nargs="?", help="id do job (ex.: shopping-delivery)")
    parser.add_argument("--list", action="store_true", help="lista os jobs do ci.yml")
    parser.add_argument("--root", type=Path, default=ROOT, help="raiz do repositório")
    args = parser.parse_args()
    if args.list or not args.job:
        for job_id, job in load_jobs(args.root).items():
            print(f"{job_id:30} {job.get('name', '')}")
        return 0
    return run_steps(args.root, args.job)


if __name__ == "__main__":
    sys.exit(main())
