#!/usr/bin/env bash
# Downloads the mutation report artifact from the most recent successful run of the nightly PIT
# workflow into the given directory.
#
# Rationale: pages.yml deploys the entire site fresh on every push to main, and has no idea /pit/
# exists - without this, that would wipe out whatever pit-mutation-testing.yml last published,
# since only one of the two workflows' output can be live on GitHub Pages at a time. Running this
# first carries the last published report forward across ordinary pushes, until the next nightly
# run (or manual dispatch) replaces it with a fresh one.
#
# Usage: fetch-latest-pit-report.sh <destination-dir>
#
# Requires:
#   - GH_TOKEN (or GITHUB_TOKEN) with `actions: read` permission
#   - the `gh` CLI, preinstalled on GitHub-hosted runners
set -euo pipefail

dest="${1:?Usage: $0 <destination-dir>}"
workflow="pit-mutation-testing.yml"

run_id=$(gh run list --workflow "$workflow" --status success --branch main --limit 1 --json databaseId --jq '.[0].databaseId // empty')

if [[ -z "$run_id" ]]; then
  echo "::notice::No successful run of ${workflow} found yet; site will not include a mutation report."
  exit 0
fi

echo "Found successful ${workflow} run ${run_id}; downloading its mutation report artifact."
mkdir -p "$dest"

if ! gh run download "$run_id" --name pit-report --dir "$dest"; then
  echo "::warning::Run ${run_id} has no 'pit-report' artifact (expired or predates this script); site will not include a mutation report."
fi
