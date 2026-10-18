#!/usr/bin/env bash
# Run from an authenticated maintainer machine. This script never reads secret values.
set -euo pipefail
repo=${1:-AQSAMA/FAndroidTerminal}
[[ $repo =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || exit 2
command -v gh >/dev/null || { echo 'Install GitHub CLI and run gh auth login first.' >&2; exit 1; }
gh auth status
[[ $(gh api "repos/$repo" --jq '.permissions.admin') == true ]] || { echo 'Repository administration permission required.' >&2; exit 1; }
gh api --method PATCH "repos/$repo" -F allow_squash_merge=true -F allow_merge_commit=false -F allow_rebase_merge=false -F delete_branch_on_merge=true >/dev/null
printf '%s' '{"default_workflow_permissions":"read","can_approve_pull_request_reviews":false}' | gh api --method PUT "repos/$repo/actions/permissions/workflow" --input - >/dev/null
for channel in preview production; do
  printf '%s' '{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}' | gh api --method PUT "repos/$repo/environments/$channel" --input - >/dev/null
  if ! gh api "repos/$repo/environments/$channel/deployment-branch-policies" --jq '.branch_policies[].name' | grep -qx main; then
    printf '%s' '{"name":"main","type":"branch"}' | gh api --method POST "repos/$repo/environments/$channel/deployment-branch-policies" --input - >/dev/null
  fi
done
gh variable set PREVIEW_ENABLED --repo "$repo" --body true
printf '%s' '{"required_status_checks":{"strict":true,"contexts":["Foundation","Android build and checks"]},"enforce_admins":true,"required_pull_request_reviews":{"dismiss_stale_reviews":true,"required_approving_review_count":0},"restrictions":null,"required_conversation_resolution":true,"allow_force_pushes":false,"allow_deletions":false}' | gh api --method PUT "repos/$repo/branches/main/protection" --input - >/dev/null
printf 'Repository policies configured. Set distinct signing identities with scripts/setup-signing.py.\n'
