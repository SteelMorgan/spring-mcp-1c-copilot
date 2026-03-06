#!/usr/bin/env bash

set -euo pipefail

ENV_FILE="${1:-.env}"
SECRET_FILE="${2:-.secrets/onec_ai_token}"
SECRET_DIR="$(dirname "$SECRET_FILE")"
BACKUP_FILE="${ENV_FILE}.bak"
SECRET_RELATIVE_PATH="$SECRET_FILE"

strip_wrapping_quotes() {
  local value="$1"

  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi

  printf '%s' "$value"
}

read_token_from_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    return 1
  fi

  local line
  line="$(grep -E '^[[:space:]]*(export[[:space:]]+)?ONEC_AI_TOKEN=' "$ENV_FILE" | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    return 1
  fi

  local value="${line#*=}"
  value="$(strip_wrapping_quotes "$value")"
  if [[ -z "$value" ]]; then
    return 1
  fi

  printf '%s' "$value"
}

ensure_secret_file() {
  if [[ -s "$SECRET_FILE" ]]; then
    return 0
  fi

  local token
  token="$(read_token_from_env_file)" || {
    echo "Не найден токен ни в $SECRET_FILE, ни в $ENV_FILE (строка ONEC_AI_TOKEN=...)." >&2
    exit 1
  }

  mkdir -p "$SECRET_DIR"
  chmod 700 "$SECRET_DIR"
  printf '%s' "$token" > "$SECRET_FILE"
  chmod 600 "$SECRET_FILE"
  echo "Создан secret-файл: $SECRET_FILE"
}

update_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    printf 'ONEC_AI_TOKEN_FILE=%s\n' "$SECRET_RELATIVE_PATH" > "$ENV_FILE"
    echo "Создан $ENV_FILE"
    return
  fi

  cp "$ENV_FILE" "$BACKUP_FILE"

  awk '
    !match($0, /^[[:space:]]*(export[[:space:]]+)?ONEC_AI_TOKEN=/) &&
    !match($0, /^[[:space:]]*(export[[:space:]]+)?ONEC_AI_TOKEN_FILE=/)
  ' "$BACKUP_FILE" > "$ENV_FILE"

  {
    printf '\n'
    printf 'ONEC_AI_TOKEN_FILE=%s\n' "$SECRET_RELATIVE_PATH"
  } >> "$ENV_FILE"

  echo "Файл $ENV_FILE обновлён, резервная копия: $BACKUP_FILE"
}

ensure_secret_file
update_env_file

echo "Готово. Для Docker Compose можно запускать:"
echo "  docker compose up -d"
