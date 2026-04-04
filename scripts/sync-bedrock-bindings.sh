#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
if [[ -n "${TPipe_REPO_ROOT:-}" ]]; then
    TPipe_REPO_ROOT="$(cd "${TPipe_REPO_ROOT}" && pwd)"
elif [[ -d "${REPO_ROOT}/../TPipe/TPipe" ]]; then
    TPipe_REPO_ROOT="$(cd "${REPO_ROOT}/../TPipe/TPipe" && pwd)"
else
    TPipe_REPO_ROOT="$(cd "${REPO_ROOT}/../TPipe" && pwd)"
fi
INFERENCE_FILE="${HOME}/.aws/inference.txt"
AWS_BIN="${AWS_BIN:-aws}"

DRY_RUN=false
BACKUP_PATH=""

usage() {
    cat <<'EOF'
Usage: scripts/sync-bedrock-bindings.sh [--dry-run]

Looks up the Bedrock ARN for each hard-coded model ID/region row, then binds it
with TPipe's inference-config CLI.

Environment overrides:
  AWS_BIN         Path to the AWS CLI binary to use.
  TPipe_REPO_ROOT  Override the sibling TPipe checkout root.
EOF
}

die() {
    printf 'Error: %s\n' "$1" >&2
    exit 1
}

resolve_profile_arn() {
    local lookup_id="$1"
    local model_id="$2"
    local region="$3"
    local arn

    arn="$(
        "$AWS_BIN" bedrock get-inference-profile \
            --region "$region" \
            --inference-profile-identifier "$lookup_id" \
            --query 'inferenceProfileArn' \
            --output text 2>/dev/null
    )" || return 1

    [[ -n "$arn" && "$arn" != "None" ]] || return 1
    printf '%s\n' "$arn"
}

resolve_foundation_arn() {
    local lookup_id="$1"
    local model_id="$2"
    local region="$3"
    local arn

    arn="$(
        "$AWS_BIN" bedrock list-foundation-models \
            --region "$region" \
            --query "modelSummaries[?modelId=='${model_id}'].modelArn | [0]" \
            --output text 2>/dev/null
    )" || return 1

    [[ -n "$arn" && "$arn" != "None" ]] || return 1
    printf '%s\n' "$arn"
}

resolve_model_arn() {
    local model_id="$1"
    local region="$2"
    local lookup_kind="$3"
    local lookup_id="$4"
    local arn

    if [[ "$lookup_kind" == "profile" ]]; then
        if arn="$(resolve_profile_arn "$lookup_id" "$model_id" "$region")"; then
            printf 'profile:%s\n' "$arn"
            return 0
        fi
    fi

    if arn="$(resolve_foundation_arn "$lookup_id" "$model_id" "$region")"; then
        printf 'foundation:%s\n' "$arn"
        return 0
    fi

    return 1
}

bind_model() {
    local model_id="$1"
    local region="$2"
    local lookup_kind="$3"
    local lookup_id="$4"
    local resolved
    local kind
    local arn

    if ! resolved="$(resolve_model_arn "$model_id" "$region" "$lookup_kind" "$lookup_id")"; then
        printf 'Warning: Could not resolve ARN for %s in %s. Skipping.\n' "$model_id" "$region" >&2
        return 0
    fi

    kind="${resolved%%:*}"
    arn="${resolved#*:}"

    printf 'Resolved %s (%s) via %s -> %s\n' "$model_id" "$region" "$kind" "$arn"

    if "$DRY_RUN"; then
        printf 'Dry run: ./gradlew runInferenceCli --args="bind %q %q"\n' "$model_id" "$arn"
        return 0
    fi

    if [[ -z "$BACKUP_PATH" && -f "$INFERENCE_FILE" ]]; then
        BACKUP_PATH="${INFERENCE_FILE}.bak.$(date +%Y%m%d%H%M%S)"
        cp -p "$INFERENCE_FILE" "$BACKUP_PATH"
        printf 'Backed up %s to %s\n' "$INFERENCE_FILE" "$BACKUP_PATH"
    fi

    if [[ ! -f "$INFERENCE_FILE" ]]; then
        mkdir -p "$(dirname "$INFERENCE_FILE")"
    fi

    ./gradlew runInferenceCli --args="bind $model_id $arn"
}

main() {
    if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
        usage
        exit 0
    fi

    if [[ "${1:-}" == "--dry-run" ]]; then
        DRY_RUN=true
        shift
    fi

    if [[ $# -gt 0 ]]; then
        usage >&2
        exit 64
    fi

    command -v "$AWS_BIN" >/dev/null 2>&1 || die "aws CLI is required: ${AWS_BIN}"
    [[ -d "$TPipe_REPO_ROOT/TPipe-Bedrock" || -d "$TPipe_REPO_ROOT/TPipe/TPipe-Bedrock" ]] || die "TPipe Bedrock module directory not found under $TPipe_REPO_ROOT"

    local manifest
    manifest="$(./gradlew exportModelManifest -q)" || die "Failed to export model manifest"

    local entry model_id region lookup_kind lookup_id
    set +e
    while IFS= read -r entry; do
        [[ -z "$entry" ]] && continue
        IFS='|' read -r model_id region lookup_kind lookup_id <<<"$entry"
        bind_model "$model_id" "$region" "$lookup_kind" "$lookup_id"
    done <<<"$manifest"
    set -e
}

main "$@"
