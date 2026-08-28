#!/bin/zsh
set -euo pipefail

script_dir="${0:A:h}"
backend_dir="${script_dir:h}"
project_dir="${backend_dir:h}"
cd "$backend_dir"

set -a
source "$project_dir/infra/.env"
set +a

verify_dir=$(mktemp -d)
project_java_home=$(awk -F= '/^export JAVA_HOME=/{print $2; exit}' ./mvn-project)
platform_user="t5_platform_$(date +%s)"
platform_password=$(openssl rand -hex 24)
venue_user="t5_venue_$(date +%s)"
venue_password=$(openssl rand -hex 24)

APP_BOOTSTRAP_ADMIN_USERNAME="$platform_user" APP_BOOTSTRAP_ADMIN_PASSWORD="$platform_password" APP_SESSION_SECURE_COOKIE=false SERVER_PORT=18083 \
  "$project_java_home/bin/java" -jar target/sports-booking-admin-0.1.0-SNAPSHOT.jar > "$verify_dir/application.log" 2>&1 &
app_pid=$!

cleanup() {
    if kill -0 "$app_pid" 2>/dev/null; then
        kill "$app_pid"
        wait "$app_pid" || true
    fi
}
finish() {
    exit_code=$?
    if [[ "$exit_code" != "0" ]]; then
        grep -E 'ERROR|Exception|Caused by|Application run failed' "$verify_dir/application.log" || true
    fi
    cleanup
    exit "$exit_code"
}
trap finish EXIT

step() {
    print -u2 "T-05 check: $1"
}

ready=false
for attempt in {1..60}; do
    http_status=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18083/api/admin/auth/session || true)
    if [[ "$http_status" == "401" ]]; then
        ready=true
        break
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    tail -n 100 "$verify_dir/application.log"
    exit 1
fi
step 'application ready with V5 migration'

curl -sS -D "$verify_dir/platform.headers" -o "$verify_dir/platform.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$platform_user\",\"password\":\"$platform_password\"}" http://127.0.0.1:18083/api/admin/auth/login
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/platform.json"
platform_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")
platform_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")

test_tag=$(date +%s)
org_payload=$(printf '{"organizationName":"T5 Verify Org %s","storeName":"T5 Verify Store %s","adminDisplayName":"T5 Venue","adminUsername":"%s","initialPassword":"%s","serviceRateBasisPoints":100}' \
    "$test_tag" "$test_tag" "$venue_user" "$venue_password")
curl -sS -o "$verify_dir/org.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t5-org-1' --data "$org_payload" http://127.0.0.1:18083/api/admin/organizations
organization_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("organizationId")' "$verify_dir/org.json")
store_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("storeId")' "$verify_dir/org.json")

curl -sS -D "$verify_dir/venue.headers" -o "$verify_dir/venue.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$venue_user\",\"password\":\"$venue_password\"}" http://127.0.0.1:18083/api/admin/auth/login
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/venue.json"
venue_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")
venue_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")

curl -sS -o "$verify_dir/gate-open.json" -H "Cookie: $venue_cookie" http://127.0.0.1:18083/api/venue/transaction-gate
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("allowsNewTransaction") ? 0 : 1)' "$verify_dir/gate-open.json"
step 'new organization and store gates are open'

venue_pause_payload='{"reason":"T5 venue maintenance"}'
curl -sS -o "$verify_dir/venue-pause-first.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t5-venue-pause-1' --data "$venue_pause_payload" http://127.0.0.1:18083/api/venue/transaction-blockers
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); exit(body.fetch("code") == 1 && !data.fetch("allowsNewTransaction") && data.fetch("storeGate").fetch("blockers").any? { |item| item.fetch("causeType") == "MANUAL" && item.fetch("blockerStatus") == "ACTIVE" } ? 0 : 1)' "$verify_dir/venue-pause-first.json"
venue_blocker_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); puts body.fetch("data").fetch("storeGate").fetch("blockers").find { |item| item.fetch("causeType") == "MANUAL" && item.fetch("blockerStatus") == "ACTIVE" }.fetch("id")' "$verify_dir/venue-pause-first.json")
curl -sS -o "$verify_dir/venue-pause-replay.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t5-venue-pause-1' --data "$venue_pause_payload" http://127.0.0.1:18083/api/venue/transaction-blockers
ruby -rjson -e 'first=JSON.parse(File.read(ARGV[0])); replay=JSON.parse(File.read(ARGV[1])); exit(first.fetch("data") == replay.fetch("data") ? 0 : 1)' "$verify_dir/venue-pause-first.json" "$verify_dir/venue-pause-replay.json"
venue_duplicate_status=$(curl -sS -o "$verify_dir/venue-pause-duplicate.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t5-venue-pause-2' --data "$venue_pause_payload" http://127.0.0.1:18083/api/venue/transaction-blockers)
[[ "$venue_duplicate_status" == "409" ]]
step 'venue manual pause, idempotency and duplicate cause rejection validated'

platform_pause_payload='{"reason":"T5 platform review"}'
curl -sS -o "$verify_dir/platform-pause.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t5-platform-pause-1' --data "$platform_pause_payload" "http://127.0.0.1:18083/api/admin/stores/$store_id/transaction-blockers"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); blockers=body.fetch("data").fetch("storeGate").fetch("blockers"); exit(body.fetch("code") == 1 && blockers.count { |item| item.fetch("blockerStatus") == "ACTIVE" } == 2 ? 0 : 1)' "$verify_dir/platform-pause.json"
platform_blocker_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); puts body.fetch("data").fetch("storeGate").fetch("blockers").find { |item| item.fetch("causeType") == "MANUAL" && item.fetch("id") != ARGV[1] && item.fetch("blockerStatus") == "ACTIVE" }.fetch("id")' "$verify_dir/platform-pause.json" "$venue_blocker_id")
curl -sS -o "$verify_dir/venue-release.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t5-venue-release-1' -X POST "http://127.0.0.1:18083/api/venue/transaction-blockers/$venue_blocker_id/release"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); blockers=body.fetch("data").fetch("storeGate").fetch("blockers"); exit(body.fetch("code") == 1 && !body.fetch("data").fetch("allowsNewTransaction") && blockers.count { |item| item.fetch("blockerStatus") == "ACTIVE" } == 1 ? 0 : 1)' "$verify_dir/venue-release.json"
curl -sS -o "$verify_dir/platform-release.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t5-platform-release-1' -X POST "http://127.0.0.1:18083/api/admin/stores/$store_id/transaction-blockers/$platform_blocker_id/release"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("allowsNewTransaction") ? 0 : 1)' "$verify_dir/platform-release.json"
step 'overlapping manual blockers stay effective until each is released'

curl -sS -o "$verify_dir/status-paused.json" -X PATCH -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t5-status-pause-1' --data '{"status":"PAUSED"}' "http://127.0.0.1:18083/api/admin/stores/$store_id/status"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/status-paused.json"
curl -sS -o "$verify_dir/status-paused-gate.json" -H "Cookie: $venue_cookie" http://127.0.0.1:18083/api/venue/transaction-gate
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); blockers=body.fetch("data").fetch("storeGate").fetch("blockers"); exit(!body.fetch("data").fetch("allowsNewTransaction") && blockers.any? { |item| item.fetch("causeType") == "PLATFORM_STATUS" && item.fetch("blockerStatus") == "ACTIVE" } ? 0 : 1)' "$verify_dir/status-paused-gate.json"
curl -sS -o "$verify_dir/status-active.json" -X PATCH -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t5-status-active-1' --data '{"status":"ACTIVE"}' "http://127.0.0.1:18083/api/admin/stores/$store_id/status"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/status-active.json"
curl -sS -o "$verify_dir/status-active-gate.json" -H "Cookie: $venue_cookie" http://127.0.0.1:18083/api/venue/transaction-gate
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("data").fetch("allowsNewTransaction") ? 0 : 1)' "$verify_dir/status-active-gate.json"
step 'legacy platform store status now owns only its platform-status blocker'

organization_pause_payload='{"reason":"T5 organization review"}'
curl -sS -o "$verify_dir/organization-pause.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t5-organization-pause-1' --data "$organization_pause_payload" "http://127.0.0.1:18083/api/admin/organizations/$organization_id/transaction-blockers"
organization_blocker_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1 && !body.fetch("data").fetch("allowsNewTransaction"); puts body.fetch("data").fetch("organizationGate").fetch("blockers").find { |item| item.fetch("causeType") == "MANUAL" && item.fetch("blockerStatus") == "ACTIVE" }.fetch("id")' "$verify_dir/organization-pause.json")
venue_forbidden_status=$(curl -sS -o "$verify_dir/venue-forbidden.json" -w '%{http_code}' -H "Cookie: $venue_cookie" "http://127.0.0.1:18083/api/admin/organizations/$organization_id/transaction-gate")
[[ "$venue_forbidden_status" == "403" ]]
curl -sS -o "$verify_dir/organization-release.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t5-organization-release-1' -X POST "http://127.0.0.1:18083/api/admin/organizations/$organization_id/transaction-blockers/$organization_blocker_id/release"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit(body.fetch("code") == 1 && body.fetch("data").fetch("allowsNewTransaction") ? 0 : 1)' "$verify_dir/organization-release.json"
step 'organization-level blocker and platform-only scope validated'

printf 'T-05 runtime verification passed: V5 migration, gate initialization, manual blocker overlap, independent release, platform-status compatibility, idempotency and role scope\n'
