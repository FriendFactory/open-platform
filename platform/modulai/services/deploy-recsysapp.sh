#!/usr/bin/env bash

set -euo pipefail

NAMESPACE="${1:-modulai}"

kubectl -n "$NAMESPACE" rollout restart deployment

