#!/usr/bin/env bash
set -euo pipefail

ROOT="/mnt/c/Users/jlebo/repos/cognition-interview/j2ee-loan-service"
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
  -i "${ROOT}/ansible/inventories/integration/hosts.yml" \
  "${ROOT}/ansible/site.yml" \
  -e "app_war_src=${ROOT}/target/loan-service.war"
