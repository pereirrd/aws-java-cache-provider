#!/usr/bin/env bash
set -euo pipefail

# Bootstrap LocalStack resources for local development.
# ElastiCache control-plane APIs may require LocalStack Pro; Secrets Manager works on Community.

awslocal secretsmanager create-secret \
  --name "aws-java-cache/local/redis-password" \
  --description "Sample Redis password for local development" \
  --secret-string "local-dev-password" \
  2>/dev/null || true

echo "LocalStack bootstrap completed."
