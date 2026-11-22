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
# Absolute paths: the scripts cd into ansible/ before running ansible-playbook.
INVENTORY_FILE="$(realpath -m "${INVENTORY_FILE}")"
WAR="$(realpath -m "${WAR}")"
# JSON extra-vars so paths with spaces survive Ansible's key=value parsing.
WAR_EXTRA_VARS="$(python3 -c 'import json, sys; print(json.dumps({"app_war_src": sys.argv[1]}))' "${WAR}")"
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
  -e "${WAR_EXTRA_VARS}"
