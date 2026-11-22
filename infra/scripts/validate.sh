#!/usr/bin/env bash
# Local and CI validation for infra/ (IAC-10). No AWS credentials needed.
#   pip install cfn-lint==1.60.1   and cfn-guard 3.x on PATH
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

echo "==> cfn-lint"
cfn-lint cloudformation/*.yaml

echo "==> cfn-guard rule tests"
if ! out="$(cfn-guard test -r guard/loan-service.guard -t guard/tests/loan-service-tests.yaml 2>&1)"; then
  echo "${out}"
  exit 1
fi
echo "OK ($(grep -c 'Expected = ' <<<"${out}") expectations)"

echo "==> cfn-guard validate templates"
cfn-guard validate -r guard/loan-service.guard -d cloudformation --show-summary fail

echo "==> parameter files match template parameters"
python3 - <<'PY'
import json, pathlib, sys, yaml

class Loader(yaml.SafeLoader):
    pass
Loader.add_multi_constructor("!", lambda loader, suffix, node: None)

cfn = pathlib.Path("cloudformation")
templates = {p.stem: set((yaml.load(p.read_text(), Loader) or {}).get("Parameters", {}))
             for p in cfn.glob("*.yaml")}
errors = []
for f in sorted((cfn / "params").rglob("*.json")):
    params = json.loads(f.read_text())
    unknown = set(params) - templates[f.stem]
    if unknown:
        errors.append(f"{f}: unknown parameters {sorted(unknown)}")
    if any(str(v) == "latest" or str(v).endswith(":latest") for v in params.values()):
        errors.append(f"{f}: mutable 'latest' image tag")
print("\n".join(errors) or f"OK ({len(list((cfn / 'params').rglob('*.json')))} files)")
sys.exit(1 if errors else 0)
PY
