#!/usr/bin/env sh
set -eu

env_file="${ENV_FILE:-.env.release}"
compose_file="${COMPOSE_FILE:-docker-compose.full.yml}"

if [ ! -f "$env_file" ]; then
  echo "Missing environment file: $env_file" >&2
  exit 2
fi

docker compose --env-file "$env_file" -f "$compose_file" exec -T mysql \
  sh -c 'exec mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' <<'SQL'
SELECT 'flyway_migration_rows', COUNT(*) FROM flyway_schema_history WHERE success = 1;
SELECT 'platform_users', COUNT(*) FROM sys_user;
SELECT 'active_courses', COUNT(*) FROM course WHERE status = 'ACTIVE';
SELECT 'answer_records', COUNT(*) FROM answer_record;
SELECT 'mastery_rows', COUNT(*) FROM student_knowledge_mastery;
SELECT 'published_graph_versions', COUNT(*) FROM graph_version WHERE status = 'PUBLISHED';
SELECT 'published_relations', COUNT(*)
  FROM knowledge_relation relation_row
  JOIN graph_version graph_version_row ON graph_version_row.id = relation_row.graph_version_id
  WHERE graph_version_row.status = 'PUBLISHED' AND relation_row.review_status = 'APPROVED';
SQL
