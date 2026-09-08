#!/usr/bin/env sh
set -eu

output_path="${1:-.env.release}"
if [ -e "$output_path" ] && [ "${FORCE:-0}" != "1" ]; then
  echo "Refusing to overwrite existing release environment file: $output_path. Set FORCE=1 only when replacement is intended." >&2
  exit 2
fi

new_private_value() {
  printf 'local_'
  openssl rand -base64 "$1" | tr '+/' '-_' | tr -d '=\n'
}

umask 077
cat > "$output_path" <<EOF
MYSQL_DATABASE=edu
MYSQL_USER=edu
MYSQL_PASSWORD=$(new_private_value 24)
MYSQL_ROOT_PASSWORD=$(new_private_value 24)
MYSQL_PORT=3306
JWT_SECRET=$(new_private_value 48)
JWT_ISSUER=https://edu-java.local
JWT_ACCESS_TOKEN_TTL_SECONDS=3600
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=$(new_private_value 24)
NEO4J_HTTP_PORT=7474
NEO4J_BOLT_PORT=7687
REDIS_PORT=6379
BACKEND_PORT=8080
FRONTEND_PORT=8081
VITE_API_BASE_URL=/api/v1
CORS_ALLOWED_ORIGINS=http://localhost:8081
APP_BUILD_VERSION=0.7.0-rc-local
APP_BUILD_TIME=local
APP_DEMO_SEED_ENABLED=true
EOF
echo "Created local release environment file: $output_path"
