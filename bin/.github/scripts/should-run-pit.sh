#!/usr/bin/env bash
# Decides whether the nightly PIT workflow should actually run PIT.
#
# Rationale: mutation testing is slow, so re-running it against a commit that has already been
# mutation-tested (e.g. two scheduled runs with no commits in between) wastes CI minutes for no
# benefit. We only run it when at least one new commit has landed on the default branch since the
# last *successful* PIT run.
#
# Requires:
#   - GITHUB_TOKEN    GitHub Actions token with `actions: read` permission
#   - GITHUB_REPOSITORY, GITHUB_OUTPUT   set automatically by the Actions runner
#   - a prior `actions/checkout` with `fetch-depth: 0`, so the full commit history is available
#     locally for the `git rev-list` count below.
set -euo pipefail

# Must match the filename of the workflow this script is called from.
WORKFLOW_FILE="pit-mutation-testing.yml"

api_url="https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/workflows/${WORKFLOW_FILE}/runs?status=success&branch=main&per_page=1"

response=$(curl -sSf \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "$api_url")

last_sha=$(echo "$response" | jq -r '.workflow_runs[0].head_sha // empty')

if [[ -z "$last_sha" ]]; then
  echo "No previous successful run of ${WORKFLOW_FILE} found - running PIT for the first time."
  echo "should_run=true" >> "$GITHUB_OUTPUT"
  exit 0
fi

# Falls back to "run PIT" (rather than skipping) if the commit can't be resolved locally, e.g.
# after a force-push rewrote history - better to run an extra time than to skip silently forever.
if ! new_commits=$(git rev-list --count "${last_sha}..HEAD" 2>/dev/null); then
  echo "::warning::Could not find commit ${last_sha} (last successful PIT run) in local history; running PIT to be safe."
  echo "should_run=true" >> "$GITHUB_OUTPUT"
  exit 0
fi

echo "Last successful PIT run was at ${last_sha}; ${new_commits} new commit(s) on main since then."

if [[ "$new_commits" -gt 0 ]]; then
  echo "should_run=true" >> "$GITHUB_OUTPUT"
else
  echo "should_run=false" >> "$GITHUB_OUTPUT"
  echo "::notice::Skipping PIT mutation testing - no new commits since the last successful run (${last_sha})."
fi
