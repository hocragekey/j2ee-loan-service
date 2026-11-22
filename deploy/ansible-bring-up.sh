#!/usr/bin/env bash
# Copy the integration PEM onto the Linux filesystem, wait for SSH, then run ansible/site.yml.
# Run from WSL or Linux after deploy/provision-integration-ec2.sh and `mvn -DskipTests package`.
#
# Environment overrides (all optional):
#   LOAN_SVC_ROOT     repo root                         (default: parent of this script's directory)
#   KEY_NAME          EC2 key pair name                 (default: loan-svc-integration)
#   SRC_PEM           PEM written by the provision script (default: $LOAN_SVC_ROOT/deploy/secrets/$KEY_NAME.pem)
#   SSH_KEY_FILE      PEM path used by ssh/Ansible      (default: ~/.ssh/$KEY_NAME.pem)
#   INVENTORY_FILE    Ansible inventory                 (default: $LOAN_SVC_ROOT/ansible/inventories/integration/hosts.yml)
#   INTEGRATION_HOST  host/IP to wait on for SSH        (default: ansible_host from INVENTORY_FILE)
#   SSH_USER          SSH user                          (default: ec2-user)
#   WAR_FILE          WAR to deploy                     (default: $LOAN_SVC_ROOT/target/loan-service.war)
set -euo pipefail

ROOT="${LOAN_SVC_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
KEY_NAME="${KEY_NAME:-loan-svc-integration}"
SRC_PEM="${SRC_PEM:-${ROOT}/deploy/secrets/${KEY_NAME}.pem}"
PEM="${SSH_KEY_FILE:-${HOME}/.ssh/${KEY_NAME}.pem}"
INVENTORY_FILE="${INVENTORY_FILE:-${ROOT}/ansible/inventories/integration/hosts.yml}"
SSH_USER="${SSH_USER:-ec2-user}"
WAR="${WAR_FILE:-${ROOT}/target/loan-service.war}"

if [[ ! -f "${INVENTORY_FILE}" ]]; then
  echo "ERROR: inventory ${INVENTORY_FILE} not found. Run deploy/provision-integration-ec2.sh or copy hosts.yml.example." >&2
  exit 1
fi

HOST="${INTEGRATION_HOST:-$(sed -n 's/^[[:space:]]*ansible_host:[[:space:]]*\([^[:space:]#]*\).*/\1/p' "${INVENTORY_FILE}" | head -n 1)}"
if [[ -z "${HOST}" || "${HOST}" == "REPLACE_WITH_PUBLIC_IP" ]]; then
  echo "ERROR: no integration host. Set INTEGRATION_HOST or ansible_host in ${INVENTORY_FILE}." >&2
  exit 1
fi

mkdir -p "$(dirname "${PEM}")"
if [[ "$(realpath -m "${SRC_PEM}")" != "$(realpath -m "${PEM}")" ]]; then
  cp "${SRC_PEM}" "${PEM}"
fi
chmod 600 "${PEM}"

# Keep inventory key path on the Linux filesystem (DrvFs perms are too open for OpenSSH)
INVENTORY_FILE="${INVENTORY_FILE}" PEM="${PEM}" python3 - <<'PY'
import os
import re
from pathlib import Path

p = Path(os.environ["INVENTORY_FILE"])
pem = Path(os.environ["PEM"]).as_posix()
text = re.sub(r"ansible_ssh_private_key_file:.*", f"ansible_ssh_private_key_file: {pem}", p.read_text())
p.write_text(text)
print("inventory key ->", pem)
PY

echo "==> Waiting for SSH on ${HOST}"
for i in $(seq 1 30); do
  if ssh -i "${PEM}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=5 \
      "${SSH_USER}@${HOST}" "echo SSH_OK"; then
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
ansible-playbook -i "${INVENTORY_FILE}" site.yml -e "app_war_src=${WAR}"
