#!/usr/bin/env bash
# Local containers only. Generates one-day test TLS certificates and fake credentials.
set -euo pipefail
review_image="${1:-apiforge-ai:portfolio}"
review_id="apiforge-ai-review-$$"
review_db="${review_id}-db"
review_api="${review_id}-api"
review_dir="$(mktemp -d "${TMPDIR:-/tmp}/apiforge-review.XXXXXX")"
cleanup() {
  docker rm -f -v "$review_api" "$review_db" >/dev/null 2>&1 || true
  docker network rm "$review_id" >/dev/null 2>&1 || true
  rm -rf "$review_dir"
}
trap cleanup EXIT
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj "/CN=$review_db" -addext "subjectAltName=DNS:$review_db" \
  -keyout "$review_dir/test-server.key" -out "$review_dir/test-server.crt" >/dev/null 2>&1
chmod 644 "$review_dir/test-server.crt"
docker network create "$review_id" >/dev/null
docker run -d --name "$review_db" --network "$review_id" \
  -e POSTGRES_USER=test_user -e POSTGRES_DB=apiforge_test \
  -e POSTGRES_PASSWORD=test-only-not-a-secret \
  --mount "type=bind,src=$review_dir,dst=/certs,readonly" \
  --entrypoint /bin/sh postgres:17-alpine -c \
  'cp /certs/test-server.key /certs/test-server.crt /tmp/; chown postgres:postgres /tmp/test-server.*; chmod 600 /tmp/test-server.key; exec /usr/local/bin/docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/test-server.crt -c ssl_key_file=/tmp/test-server.key' >/dev/null
for ((i=0;i<60;i++)); do
  if docker exec "$review_db" pg_isready -U test_user -d apiforge_test >/dev/null 2>&1; then break; fi
  sleep 1
done
docker run -d --name "$review_api" --network "$review_id" \
  -p 127.0.0.1::10000 -e PORT=10000 \
  -e "DATABASE_URL=jdbc:postgresql://$review_db:5432/apiforge_test?sslmode=verify-full&sslrootcert=/run/test-ca.crt" \
  -e DATABASE_USER=test_user -e DATABASE_PASSWORD=test-only-not-a-secret \
  -e CORS_ALLOWED_ORIGINS=https://portfolio.example -e GEMINI_ENABLED=false \
  --mount "type=bind,src=$review_dir/test-server.crt,dst=/run/test-ca.crt,readonly" \
  --memory=512m --cpus=0.5 --read-only --tmpfs /tmp:rw,noexec,nosuid,size=32m \
  --cap-drop=ALL --security-opt=no-new-privileges "$review_image" >/dev/null
review_origin="http://$(docker port "$review_api" 10000/tcp)"
ready=false
for ((i=0;i<90;i++)); do
  if curl -fsS "$review_origin/api/health" >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then
  docker logs "$review_api" >&2
  exit 1
fi
[ "$(docker inspect --format '{{.Config.User}}' "$review_api")" = '10001:10001' ]
[ "$(curl -s -o /dev/null -w '%{http_code}' "$review_origin/api/analyses")" = 410 ]
curl -fsS "$review_origin/api/validate" -H 'Content-Type: application/json' \
  -d '{"json":"{}"}' | grep -q '"valid":true'
curl -fsS "$review_origin/api/analyses" -H 'Content-Type: application/json' \
  -d '{"method":"GET","url":"https://example.com","requestBody":"","responseBody":"{}","status":200,"useAi":false}' | grep -q '"id":null'
[ "$(curl -s -o /dev/null -w '%{http_code}' "$review_origin/api/analyses" -H 'Content-Type: application/json' \
  -d '{"method":"GET","url":"https://example.com","requestBody":"","responseBody":"{}","status":200,"useAi":true}')" = 400 ]
[ "$(docker exec "$review_db" psql -U test_user -d apiforge_test -Atc 'select count(*) from analyses')" = 0 ]
[ "$(docker exec "$review_db" psql -U test_user -d apiforge_test -Atc "select count(*) from pg_stat_ssl where ssl")" -gt 0 ]
echo 'PASS: production profile, Render-style port, non-root read-only 512 MiB runtime, verified PostgreSQL TLS, stateless API, disabled history and Gemini.'
