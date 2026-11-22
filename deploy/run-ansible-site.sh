#!/usr/bin/env bash
# Run ansible/site.yml against the integration inventory (WSL/Linux).
#
# Environment overrides (all optional):
#   LOAN_SVC_ROOT   repo root           (default: parent of this script's directory)
#   INVENTORY_FILE  Ansible inventory   (default: $LOAN_SVC_ROOT/ansible/inventories/integration/hosts.yml)
#   WAR_FILE        WAR to deploy       (default: $LOAN_SVC_ROOT/target/loan-service.war)
set -euo pipefail

ROOT="${LOAN_SVC_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
INVENTORY_FILE="${INVENTORY_FILE:-${ROOT}/ansible/inventories/integration/hosts.yml}"
WAR="${WAR_FILE:-${ROOT}/target/loan-service.war}"
export PATH="${HOME}/.local/bin:${PATH}"

# DrvFs is world-writable; Ansible refuses ansible.cfg there — set env explicitly.
export ANSIBLE_CONFIG="${ROOT}/ansible/ansible.cfg"
export ANSIBLE_ROLES_PATH="${ROOT}/ansible/roles"
export ANSIBLE_HOST_KEY_CHECKING=False
export ANSIBLE_RETRY_FILES_ENABLED=False

python3 -m pip install --user -q -r "${ROOT}/ansible/requirements.txt"

cd "${ROOT}/ansible"
ansible --version | head -n 2
ansible-playbook \
  -i "${INVENTORY_FILE}" \
  "${ROOT}/ansible/site.yml" \
  -e "app_war_src=${WAR}"
