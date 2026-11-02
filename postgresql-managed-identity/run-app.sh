#!/usr/bin/env bash
# Usage: ./run-app.sh [mi|user]
#   mi   - authenticate with the user-assigned managed identity (run on the Azure VM)
#   user - authenticate as the signed-in Azure CLI user (az login)
# Without an argument AUTH_MODE from .env is used (default: managed identity).
# All settings (server, database, identity) are read by the app from .env - see .env.example.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo ".env not found. Copy .env.example to .env and fill in your values." >&2
  exit 1
fi

case "${1:-}" in
  mi) export AUTH_MODE=managed-identity ;;
  user) export AUTH_MODE=user ;;
  "") ;;
  *) echo "Unknown mode \"$1\". Use \"mi\" or \"user\"." >&2; exit 1 ;;
esac

[ -f target/postgresql-managed-identity.jar ] || mvn -q -B package
exec java -jar target/postgresql-managed-identity.jar
