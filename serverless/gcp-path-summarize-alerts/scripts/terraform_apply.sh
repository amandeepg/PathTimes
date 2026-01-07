#!/usr/bin/env bash
set -euo pipefail

# Wrapper around terraform apply for the single image / single service deployment.
# Usage:
#   scripts/terraform_apply.sh   # full apply
# Env vars:
#   PROJECT_ID (default: underthehudson)
#   REGION (default: us-central1)
#   ENVIRONMENT (default: staging)
#   PARALLELISM (default: 20)
#   NO_EXPORT_REQS=1 to skip uv export when deps unchanged

PROJECT_ID=${PROJECT_ID:-underthehudson}
REGION=${REGION:-us-central1}
ENVIRONMENT=${ENVIRONMENT:-staging}
PARALLELISM=${PARALLELISM:-20}

if [[ -z "${NO_EXPORT_REQS:-}" ]]; then
  echo "Exporting requirements.txt (uv export)..."
  uv export --no-dev --frozen --format requirements-txt --no-hashes --output-file requirements.txt
else
  echo "Skipping uv export (NO_EXPORT_REQS=1)"
fi

echo "Initializing terraform (if needed)..."
terraform init >/dev/null

echo "Running terraform apply ${TARGET_ARGS[*]:-<full>} ..."
CLOUDSDK_CORE_HTTP_TIMEOUT=1200 \
CLOUDSDK_HTTP_TIMEOUT=1200 \
GOOGLE_HTTP_TIMEOUT=1200 \
GOOGLE_OAUTH_ACCESS_TOKEN=$(gcloud auth print-access-token) \
terraform apply \
  -auto-approve \
  -parallelism="${PARALLELISM}" \
  -var="project_id=${PROJECT_ID}" \
  -var="region=${REGION}" \
  -var="environment=${ENVIRONMENT}"

echo "Done."
