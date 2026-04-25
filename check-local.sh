#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

log() {
  echo "[check-local] $*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[check-local] ERROR: required command not found: $1" >&2
    exit 1
  fi
}

retry_until_ok() {
  local name="$1"
  local max_attempts="$2"
  local sleep_seconds="$3"
  shift 3

  local attempt=1
  while (( attempt <= max_attempts )); do
    if "$@" >/dev/null 2>&1; then
      log "$name is ready"
      return 0
    fi
    log "Waiting for $name (attempt ${attempt}/${max_attempts})..."
    attempt=$((attempt + 1))
    sleep "$sleep_seconds"
  done

  echo "[check-local] ERROR: $name did not become ready in time" >&2
  return 1
}

extract_token() {
  local json="$1"
  local token
  token="$(printf '%s' "$json" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  if [[ -z "$token" ]]; then
    echo "[check-local] ERROR: could not extract JWT token from login response" >&2
    echo "$json" >&2
    return 1
  fi
  printf '%s' "$token"
}

check_jwt_secret() {
  local env_file="$1"
  local secret
  secret="$(grep -E '^JWT_SECRET=' "$env_file" | head -n1 | cut -d'=' -f2- || true)"

  if [[ -z "$secret" ]]; then
    echo "[check-local] ERROR: JWT_SECRET is missing in $env_file" >&2
    return 1
  fi

  if [[ "$secret" == "replace_with_a_minimum_64_characters_random_secret_for_jwt_signing" ]]; then
    echo "[check-local] ERROR: JWT_SECRET in $env_file is still the placeholder value" >&2
    return 1
  fi

  if (( ${#secret} < 64 )); then
    echo "[check-local] ERROR: JWT_SECRET must be at least 64 characters (current length: ${#secret})" >&2
    return 1
  fi
}

require_cmd docker
require_cmd curl

log "Step 3: Prepare environment file"
if [[ ! -f .env ]]; then
  cp .env.example .env
  log "Created .env from .env.example"
else
  log ".env already exists (keeping current values)"
fi
check_jwt_secret .env

log "Step 4: Build and start containers"
docker compose up --build -d

log "Step 5: Verify service health and gateway routing"
retry_until_ok "user-service status endpoint" 30 5 curl -fsS http://localhost:8081/api/users/status
retry_until_ok "api-gateway status endpoint" 30 5 curl -fsS http://localhost:8080/api/users/status

user_status="$(curl -fsS http://localhost:8081/api/users/status)"
gateway_status="$(curl -fsS http://localhost:8080/api/users/status)"
[[ "$user_status" == "User Service is up and running!" ]]
[[ "$gateway_status" == "User Service is up and running!" ]]

if [[ -f ./test-routing.sh ]]; then
  bash ./test-routing.sh
fi

log "Step 6: Verify register/login/protected auth flow via gateway"
EMAIL="check.$(date +%s)@example.com"
PASSWORD="StrongPass123"
NAME="Local Check"

register_payload=$(cat <<JSON
{"name":"$NAME","email":"$EMAIL","password":"$PASSWORD"}
JSON
)

register_code="$(curl -sS -o /tmp/register-response.json -w "%{http_code}" \
  -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d "$register_payload")"
[[ "$register_code" == "201" ]]

login_payload=$(cat <<JSON
{"email":"$EMAIL","password":"$PASSWORD"}
JSON
)

login_body="$(curl -fsS \
  -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d "$login_payload")"

token="$(extract_token "$login_body")"

protected_body="$(curl -fsS \
  http://localhost:8080/api/users/protected \
  -H "Authorization: Bearer $token")"

[[ "$protected_body" == *"Access granted for: $EMAIL"* ]]

log "Step 7: Run Maven verify for user-service and api-gateway"
(
  cd user-service
  chmod +x mvnw
  ./mvnw -B clean verify
)
(
  cd api-gateway
  chmod +x mvnw
  ./mvnw -B clean verify
)

log "All checks passed successfully."
log "Services are still running. Stop them with: docker compose down"
