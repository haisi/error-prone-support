#!/usr/bin/env bash
# Generates a static "mutation score" badge SVG from a PIT mutations.xml report.
#
# Usage: generate-mutation-badge.sh <path-to-mutations.xml> <path-to-output.svg>
#
# The score is (killed mutations / total mutations) * 100, using PIT's own `detected` attribute
# (true for both KILLED and TIMED_OUT mutations) rather than re-deriving it from `status`.
set -euo pipefail

mutations_xml="${1:?Usage: $0 <mutations.xml> <output.svg>}"
output_svg="${2:?Usage: $0 <mutations.xml> <output.svg>}"

total=$(grep -o "<mutation " "$mutations_xml" | wc -l | tr -d ' ')
killed=$(grep -o "detected='true'" "$mutations_xml" | wc -l | tr -d ' ')

if [[ "$total" -eq 0 ]]; then
  echo "::warning::No mutations found in ${mutations_xml}; badge will show 0%."
  score="0.0"
else
  score=$(awk -v k="$killed" -v t="$total" 'BEGIN { printf "%.1f", (k / t) * 100 }')
fi

# Thresholds are a reasonable default for a small library; adjust if the project's expectations
# around mutation coverage change.
score_int=${score%%.*}
if [[ "$score_int" -ge 90 ]]; then
  color="brightgreen"
elif [[ "$score_int" -ge 75 ]]; then
  color="green"
elif [[ "$score_int" -ge 50 ]]; then
  color="yellow"
else
  color="red"
fi

echo "Mutation score: ${score}% (${killed}/${total} mutations killed) - badge color: ${color}"

mkdir -p "$(dirname "$output_svg")"
curl -sSf -o "$output_svg" "https://img.shields.io/badge/mutation%20score-${score}%25-${color}"

echo "Badge written to ${output_svg}"
