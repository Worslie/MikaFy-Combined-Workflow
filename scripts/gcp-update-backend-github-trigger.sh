#!/usr/bin/env bash
# Point an existing Cloud Build *GitHub* push trigger at the monorepo and scope it to the backend folder.
#
# Prerequisites: Google Cloud SDK installed, `gcloud auth login`, correct project:
#   gcloud config set project YOUR_PROJECT_ID
#
# Usage (1st-gen style GitHub trigger: --repo-owner / --repo-name):
#   ./scripts/gcp-update-backend-github-trigger.sh TRIGGER_NAME [BRANCH_REGEX]
#
# Example:
#   ./scripts/gcp-update-backend-github-trigger.sh deploy-backend-on-push '^main$'
#
# If this fails with errors about "repository" or Developer Connect / 2nd gen,
# use the console: Cloud Build → Triggers → your trigger → Edit:
#   - Repository: Worslie / MikaFy-Combined-Workflow
#   - Configuration: cloudbuild.yaml (repo root; default for many triggers)
#   - Included files (glob): AIBudgetTrackerBackend/**
# Optional in console: Ignored files BudgetTrackerAI/** (usually unnecessary if Included files is set).
#
set -euo pipefail

if ! command -v gcloud >/dev/null 2>&1; then
  echo "gcloud is not installed or not on PATH. Install the Google Cloud SDK, then re-run." >&2
  exit 1
fi

TRIGGER_NAME="${1:?Pass the Cloud Build trigger name or ID as the first argument.}"
BRANCH_PATTERN="${2:-^main$}"

echo "Updating trigger '${TRIGGER_NAME}' in project $(gcloud config get-value project 2>/dev/null) ..."

gcloud builds triggers update github "${TRIGGER_NAME}" \
  --repo-owner=Worslie \
  --repo-name=MikaFy-Combined-Workflow \
  --branch-pattern="${BRANCH_PATTERN}" \
  --build-config=cloudbuild.yaml \
  --included-files='AIBudgetTrackerBackend/**'

echo "Done. Describe trigger to verify:"
echo "  gcloud builds triggers describe ${TRIGGER_NAME} --format=yaml"
