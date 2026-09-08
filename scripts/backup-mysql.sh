#!/usr/bin/env sh
set -eu

env_file="${ENV_FILE:-.env.release}"
compose_file="${COMPOSE_FILE:-docker-compose.full.yml}"
output_path="${1:-artifacts/mysql-backup.sql}"

if [ ! -f "$env_file" ]; then
  echo "Missing environment file: $env_file" >&2
  exit 2
fi
mkdir -p "$(dirname "$output_path")"

docker compose --env-file "$env_file" -f "$compose_file" exec -T mysql \
  sh -c 'exec mysqldump --single-transaction --routines --events -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > "$output_path"

echo "MySQL backup written to $output_path"
