#!/usr/bin/env bash
set -euo pipefail

ROOT="/mnt/c/Users/jlebo/repos/cognition-interview/j2ee-loan-service"
SRC_PEM="${ROOT}/deploy/secrets/loan-svc-integration.pem"
PEM="${HOME}/.ssh/loan-svc-integration.pem"
WAR="${ROOT}/target/loan-service.war"
HOST="34.226.211.125"

mkdir -p "${HOME}/.ssh"
cp "${SRC_PEM}" "${PEM}"
chmod 600 "${PEM}"

# Keep inventory key path on the Linux filesystem (DrvFs perms are too open for OpenSSH)
python3 - <<PY
from pathlib import Path
p = Path("${ROOT}/ansible/inventories/integration/hosts.yml")
text = p.read_text()
text = text.replace(
    "ansible_ssh_private_key_file: /mnt/c/Users/jlebo/repos/cognition-interview/j2ee-loan-service/deploy/secrets/loan-svc-integration.pem",
    "ansible_ssh_private_key_file: ${PEM}",
)
# also normalize if already set to another path
import re
text = re.sub(
    r"ansible_ssh_private_key_file:.*",
    f"ansible_ssh_private_key_file: {Path('${PEM}').as_posix()}",
    text,
)
p.write_text(text)
print("inventory key ->", Path('${PEM}').as_posix())
PY

echo "==> Waiting for SSH on ${HOST}"
for i in $(seq 1 30); do
  if ssh -i "${PEM}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=5 \
      ec2-user@"${HOST}" "echo SSH_OK"; then
    break
  fi
  echo "SSH wait ${i}..."
  sleep 5
done

echo "==> Installing ansible-core"
python3 -m pip install --user -q -r "${ROOT}/ansible/requirements.txt"
export PATH="${HOME}/.local/bin:${PATH}"
ansible --version | head -n 2

echo "==> Running site.yml"
cd "${ROOT}/ansible"
ansible-playbook site.yml -e "app_war_src=${WAR}"
