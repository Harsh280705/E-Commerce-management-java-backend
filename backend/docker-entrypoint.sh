#!/usr/bin/env bash
# Level 3 entrypoint: wait for Level 3 infrastructure, then exec the API.
set -e

targets=(
  "level3-postgres:5432"
  "level3-mongodb:27017"
  "level3-rabbitmq:5672"
  "level3-elasticsearch:9200"
)
deadline=$((SECONDS + 180))
for target in "${targets[@]}"; do
  host="${target%%:*}"
  port="${target##*:}"
  while true; do
    if (echo > "/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
      echo "Reachable: ${target}"
      break
    fi
    if [ $SECONDS -ge $deadline ]; then
      echo "Infrastructure not ready: ${targets[*]}" >&2
      exit 1
    fi
    sleep 2
  done
done
echo "Infrastructure reachable; starting Level 3 Java API."

exec "$@"
