#!/usr/bin/env sh
set -eu

env_file="${ENV_FILE:-.env.release}"
compose_file="${COMPOSE_FILE:-docker-compose.full.yml}"
input_path="${1:?Usage: scripts/restore-mysql.sh <backup.sql>}"

if [ ! -f "$env_file" ]; then
  echo "Missing environment file: $env_file" >&2
  exit 2
fi
if [ ! -f "$input_path" ]; then
  echo "Missing SQL backup: $input_path" >&2
  exit 2
fi

docker compose --env-file "$env_file" -f "$compose_file" exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  < "$input_path"

echo "MySQL restore completed from $input_path"
