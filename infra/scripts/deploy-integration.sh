#!/usr/bin/env bash
# Deploy the loan-service integration environment (two regions, active-active)
# with CloudFormation change sets (IAC-07). Every stack is created or updated
# through a change set that is printed for review before it is executed.
#
# Usage:
#   IMAGE_TAG=<git-sha> infra/scripts/deploy-integration.sh [step]
#
# Steps (default "all", run in this order):
#   foundation  network + ECR + Aurora in both regions, then re-run the primary
#               data stack so the DB secret replicates to the secondary region
#   image-check verify ${IMAGE_TAG} exists in both regional ECR repositories
#   app         edge (ALB + WAF) and service (ECS/Fargate) in both regions
#   dns         Route 53 health checks/alarms (+ latency records when a hosted
#               zone is configured), deployed in us-east-1
#
# Environment:
#   IMAGE_TAG          immutable image tag (required for image-check/app)
#   PRIMARY_REGION     default us-east-1
#   SECONDARY_REGION   default us-west-2
#   EXPECTED_ACCOUNT   default 118938645536 (aborts on any other account)
#   AUTO_APPROVE       "true" executes change sets without prompting
set -euo pipefail

PREFIX="loan-svc-integration"
PRIMARY_REGION="${PRIMARY_REGION:-us-east-1}"
SECONDARY_REGION="${SECONDARY_REGION:-us-west-2}"
EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-118938645536}"
AUTO_APPROVE="${AUTO_APPROVE:-false}"
STEP="${1:-all}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CFN="${ROOT}/cloudformation"
PARAMS="${CFN}/params/integration"
TAGS=(Service=loan-service Environment=integration ManagedBy=cloudformation)

log() { printf '\n==> %s\n' "$*" >&2; }

account="$(aws sts get-caller-identity --query Account --output text)"
if [[ "${account}" != "${EXPECTED_ACCOUNT}" ]]; then
  echo "Refusing to deploy: caller account ${account} != expected ${EXPECTED_ACCOUNT}" >&2
  exit 1
fi

# Flat {"Key": "Value"} JSON -> Key=Value arguments.
param_args() {
  python3 -c 'import json,sys; [print(f"{k}={v}") for k,v in json.load(open(sys.argv[1])).items()]' "$1"
}

output() { # region stack key -> value ("" if the stack or output is missing)
  aws cloudformation describe-stacks --region "$1" --stack-name "$2" \
    --query "Stacks[0].Outputs[?OutputKey=='$3'].OutputValue | [0]" --output text 2>/dev/null \
    | sed 's/^None$//' || true
}

stack_status() {
  aws cloudformation describe-stacks --region "$1" --stack-name "$2" \
    --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo NONE
}

# deploy_stack <region> <stack-suffix> <template> <param-file> [Key=Value ...]
deploy_stack() {
  local region="$1" stack="${PREFIX}-$2" template="${CFN}/$3" params="$4"; shift 4
  local overrides=()
  mapfile -t overrides < <(param_args "${params}")
  overrides+=("$@")

  log "${stack} (${region}): creating change set"
  local out
  out="$(aws cloudformation deploy \
    --region "${region}" \
    --stack-name "${stack}" \
    --template-file "${template}" \
    --parameter-overrides "${overrides[@]}" \
    --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
    --tags "${TAGS[@]}" \
    --no-execute-changeset \
    --no-fail-on-empty-changeset 2>&1)" || { echo "${out}" >&2; return 1; }
  echo "${out}" >&2

  local cs
  cs="$(grep -o 'arn:aws[a-z-]*:cloudformation:[^ ]*:changeSet/[^ ]*' <<<"${out}" | head -n1 || true)"
  if [[ -z "${cs}" ]]; then
    log "${stack}: no changes"
    return 0
  fi

  aws cloudformation describe-change-set --region "${region}" --change-set-name "${cs}" \
    --query 'Changes[].ResourceChange.{Action:Action,LogicalId:LogicalResourceId,Type:ResourceType,Replacement:Replacement}' \
    --output table >&2

  if [[ "${AUTO_APPROVE}" != "true" ]]; then
    read -r -p "Execute change set for ${stack} in ${region}? [y/N] " answer
    [[ "${answer}" == "y" || "${answer}" == "Y" ]] || { echo "Skipped ${stack}; stopping." >&2; exit 1; }
  fi

  local before
  before="$(stack_status "${region}" "${stack}")"
  aws cloudformation execute-change-set --region "${region}" --change-set-name "${cs}"
  if [[ "${before}" == "REVIEW_IN_PROGRESS" ]]; then
    aws cloudformation wait stack-create-complete --region "${region}" --stack-name "${stack}"
  else
    aws cloudformation wait stack-update-complete --region "${region}" --stack-name "${stack}"
  fi
  log "${stack} (${region}): $(stack_status "${region}" "${stack}")"
}

deploy_primary_data() {
  # Replicate the master secret only once the secondary KMS replica key exists.
  local replica_region=""
  if [[ -n "$(output "${SECONDARY_REGION}" "${PREFIX}-data" DataKeyArn)" ]]; then
    replica_region="${SECONDARY_REGION}"
  fi
  deploy_stack "${PRIMARY_REGION}" data data.yaml "${PARAMS}/${PRIMARY_REGION}/data.json" \
    "SecretReplicaRegion=${replica_region}"
}

foundation() {
  deploy_stack "${PRIMARY_REGION}" network network.yaml "${PARAMS}/${PRIMARY_REGION}/network.json"
  deploy_stack "${PRIMARY_REGION}" ecr ecr.yaml "${PARAMS}/${PRIMARY_REGION}/ecr.json"
  deploy_primary_data

  deploy_stack "${SECONDARY_REGION}" network network.yaml "${PARAMS}/${SECONDARY_REGION}/network.json"
  deploy_stack "${SECONDARY_REGION}" ecr ecr.yaml "${PARAMS}/${SECONDARY_REGION}/ecr.json"
  deploy_stack "${SECONDARY_REGION}" data data.yaml "${PARAMS}/${SECONDARY_REGION}/data.json" \
    "PrimaryKmsKeyArn=$(output "${PRIMARY_REGION}" "${PREFIX}-data" DataKeyArn)" \
    "PrimaryDbSecretArn=$(output "${PRIMARY_REGION}" "${PREFIX}-data" DbSecretArn)"

  # Second pass: now that the replica key exists, replicate the secret.
  deploy_primary_data
}

require_image_tag() {
  [[ -n "${IMAGE_TAG:-}" ]] || { echo "IMAGE_TAG is required (immutable tag, never latest)" >&2; exit 1; }
  [[ "${IMAGE_TAG}" != "latest" ]] || { echo "IMAGE_TAG must not be latest" >&2; exit 1; }
}

image_uri() { # region
  echo "${account}.dkr.ecr.$1.amazonaws.com/${PREFIX}:${IMAGE_TAG}"
}

image_check() {
  require_image_tag
  local region
  for region in "${PRIMARY_REGION}" "${SECONDARY_REGION}"; do
    if ! aws ecr describe-images --region "${region}" --repository-name "${PREFIX}" \
        --image-ids "imageTag=${IMAGE_TAG}" >/dev/null 2>&1; then
      echo "Image $(image_uri "${region}") not found. Push it to both regions first (see infra/README.md)." >&2
      exit 1
    fi
    log "found $(image_uri "${region}")"
  done
}

app() {
  image_check
  local region
  for region in "${PRIMARY_REGION}" "${SECONDARY_REGION}"; do
    deploy_stack "${region}" edge edge.yaml "${PARAMS}/${region}/edge.json"
  done
  for region in "${PRIMARY_REGION}" "${SECONDARY_REGION}"; do
    deploy_stack "${region}" service service.yaml "${PARAMS}/${region}/service.json" \
      "ImageUri=$(image_uri "${region}")"
  done
}

dns() {
  local edge="${PREFIX}-edge"
  local protocol
  protocol="$(output "${PRIMARY_REGION}" "${edge}" EdgeProtocol)"
  deploy_stack us-east-1 global-dns global-dns.yaml "${PARAMS}/global-dns.json" \
    "RegionA=${PRIMARY_REGION}" "RegionB=${SECONDARY_REGION}" \
    "HealthCheckProtocol=${protocol:-HTTP}" \
    "RegionAAlbDnsName=$(output "${PRIMARY_REGION}" "${edge}" LoadBalancerDnsName)" \
    "RegionAAlbHostedZoneId=$(output "${PRIMARY_REGION}" "${edge}" LoadBalancerCanonicalHostedZoneId)" \
    "RegionBAlbDnsName=$(output "${SECONDARY_REGION}" "${edge}" LoadBalancerDnsName)" \
    "RegionBAlbHostedZoneId=$(output "${SECONDARY_REGION}" "${edge}" LoadBalancerCanonicalHostedZoneId)"
}

case "${STEP}" in
  foundation)  foundation ;;
  image-check) image_check ;;
  app)         app ;;
  dns)         dns ;;
  all)         foundation; app; dns ;;
  *) echo "Unknown step: ${STEP} (foundation|image-check|app|dns|all)" >&2; exit 2 ;;
esac
