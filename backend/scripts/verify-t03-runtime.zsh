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
platform_user="t3_platform_$(date +%s)"
platform_password=$(openssl rand -hex 24)
venue_user="t3_venue_$(date +%s)"
venue_password=$(openssl rand -hex 24)
other_venue_user="t3_other_$(date +%s)"
other_venue_password=$(openssl rand -hex 24)

APP_BOOTSTRAP_ADMIN_USERNAME="$platform_user" APP_BOOTSTRAP_ADMIN_PASSWORD="$platform_password" APP_SESSION_SECURE_COOKIE=false SERVER_PORT=18081 \
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
    print -u2 "T-03 check: $1"
}

ready=false
for attempt in {1..60}; do
    http_status=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18081/api/admin/auth/session || true)
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
step 'application ready'
curl -sS -D "$verify_dir/platform.headers" -o "$verify_dir/platform.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$platform_user\",\"password\":\"$platform_password\"}" http://127.0.0.1:18081/api/admin/auth/login
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/platform.json"
platform_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")
platform_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/platform.headers")
step 'platform authenticated'

test_tag=$(date +%s)
org_payload=$(printf '{"organizationName":"T3 Verify Org %s","storeName":"T3 Verify Store %s","adminDisplayName":"T3 Venue","adminUsername":"%s","initialPassword":"%s","serviceRateBasisPoints":100}' \
    "$test_tag" "$test_tag" "$venue_user" "$venue_password")
curl -sS -o "$verify_dir/org.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t3-org-1' --data "$org_payload" http://127.0.0.1:18081/api/admin/organizations
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/org.json"

root_payload=$(printf '{"parentId":null,"code":"T3ROOT%s","name":"T3根%s","icon":"circle","sortOrder":1}' "$test_tag" "$test_tag")
curl -sS -o "$verify_dir/root.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t3-root-1' --data "$root_payload" http://127.0.0.1:18081/api/admin/catalog/sport-categories
root_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/root.json")
child_payload=$(printf '{"parentId":"%s","code":"T3ITEM%s","name":"T3项目%s","icon":"circle","sortOrder":1}' "$root_id" "$test_tag" "$test_tag")
curl -sS -o "$verify_dir/child.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t3-child-1' --data "$child_payload" http://127.0.0.1:18081/api/admin/catalog/sport-categories
child_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/child.json")

full_payload=$(printf '{"sportCategoryId":"%s","name":"T3全场%s"}' "$child_id" "$test_tag")
curl -sS -o "$verify_dir/full.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t3-full-1' --data "$full_payload" http://127.0.0.1:18081/api/admin/catalog/rental-types
full_type_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/full.json")
half_payload=$(printf '{"sportCategoryId":"%s","name":"T3半场%s"}' "$child_id" "$test_tag")
curl -sS -o "$verify_dir/half.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t3-half-1' --data "$half_payload" http://127.0.0.1:18081/api/admin/catalog/rental-types
half_type_id=$(ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); exit 1 unless body.fetch("code") == 1; puts body.fetch("data").fetch("id")' "$verify_dir/half.json")
step 'tenant, category and rental types created'

curl -sS -D "$verify_dir/venue.headers" -o "$verify_dir/venue.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$venue_user\",\"password\":\"$venue_password\"}" http://127.0.0.1:18081/api/admin/auth/login
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/venue.json"
venue_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")
venue_csrf=$(awk -F ': ' 'tolower($1) == "x-csrf-token" {gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/venue.headers")
step 'venue authenticated'

court_payload=$(printf '{"sportCategoryId":"%s","fullRentalTypeId":"%s","halfRentalTypeId":"%s","name":"T3篮球场%s","sortOrder":1}' \
    "$child_id" "$full_type_id" "$half_type_id" "$test_tag")
curl -sS -o "$verify_dir/court-first.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t3-court-1' --data "$court_payload" http://127.0.0.1:18081/api/venue/resources/courts
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); units=data.fetch("sellableUnits"); resources=data.fetch("baseResources"); exit(body.fetch("code") == 1 && resources.map { |r| r.fetch("code") } == ["A", "B"] && units.length == 3 && units[0].fetch("currentVersion").fetch("baseResources").length == 2 && units[1].fetch("currentVersion").fetch("baseResources").map { |r| r.fetch("code") } == ["A"] && units[2].fetch("currentVersion").fetch("baseResources").map { |r| r.fetch("code") } == ["B"] ? 0 : 1)' "$verify_dir/court-first.json"
curl -sS -o "$verify_dir/court-replay.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t3-court-1' --data "$court_payload" http://127.0.0.1:18081/api/venue/resources/courts
ruby -rjson -e 'first=JSON.parse(File.read(ARGV[0])); replay=JSON.parse(File.read(ARGV[1])); exit(first.fetch("data") == replay.fetch("data") ? 0 : 1)' "$verify_dir/court-first.json" "$verify_dir/court-replay.json"
step 'physical court and initial A/B sellable units created idempotently'

court_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("id")' "$verify_dir/court-first.json")
full_unit_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("sellableUnits").first.fetch("id")' "$verify_dir/court-first.json")
resource_a_id=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("data").fetch("baseResources").first.fetch("id")' "$verify_dir/court-first.json")
curl -sS -o "$verify_dir/court-list.json" -H "Cookie: $venue_cookie" 'http://127.0.0.1:18081/api/venue/resources/courts?page=1&pageSize=20'
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); exit(body.fetch("code") == 1 && data.fetch("current") == "1" && data.fetch("records").any? { |r| r.fetch("id") == ARGV[1] } ? 0 : 1)' "$verify_dir/court-list.json" "$court_id"
step 'court paging validated'

version_payload=$(printf '{"baseResourceIds":["%s"]}' "$resource_a_id")
curl -sS -o "$verify_dir/version-first.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t3-version-1' --data "$version_payload" "http://127.0.0.1:18081/api/venue/resources/sellable-units/$full_unit_id/versions"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); data=body.fetch("data"); exit(body.fetch("code") == 1 && data.fetch("versionNo") == 2 && data.fetch("versionStatus") == "CURRENT" && data.fetch("baseResources").map { |r| r.fetch("code") } == ["A"] ? 0 : 1)' "$verify_dir/version-first.json"
curl -sS -o "$verify_dir/version-replay.json" -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t3-version-1' --data "$version_payload" "http://127.0.0.1:18081/api/venue/resources/sellable-units/$full_unit_id/versions"
ruby -rjson -e 'first=JSON.parse(File.read(ARGV[0])); replay=JSON.parse(File.read(ARGV[1])); exit(first.fetch("data") == replay.fetch("data") ? 0 : 1)' "$verify_dir/version-first.json" "$verify_dir/version-replay.json"
curl -sS -o "$verify_dir/version-list.json" -H "Cookie: $venue_cookie" "http://127.0.0.1:18081/api/venue/resources/sellable-units/$full_unit_id/versions?page=1&pageSize=20"
ruby -rjson -e 'body=JSON.parse(File.read(ARGV[0])); records=body.fetch("data").fetch("records"); exit(body.fetch("code") == 1 && body.fetch("data").fetch("total") == "2" && records[0].fetch("versionStatus") == "CURRENT" && records[1].fetch("versionStatus") == "RETIRED" ? 0 : 1)' "$verify_dir/version-list.json"
unchanged_status=$(curl -sS -o "$verify_dir/version-unchanged.json" -w '%{http_code}' -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t3-version-same' --data "$version_payload" "http://127.0.0.1:18081/api/venue/resources/sellable-units/$full_unit_id/versions")
[[ "$unchanged_status" == "409" ]]
step 'immutable version transition validated'

curl -sS -o "$verify_dir/court-disabled.json" -X PATCH -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t3-court-disabled' --data '{"status":"DISABLED"}' "http://127.0.0.1:18081/api/venue/resources/courts/$court_id/status"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/court-disabled.json"
curl -sS -o "$verify_dir/unit-disabled.json" -X PATCH -H 'Content-Type: application/json' -H "Cookie: $venue_cookie" -H "X-CSRF-Token: $venue_csrf" \
    -H 'Idempotency-Key: t3-unit-disabled' --data '{"status":"DISABLED"}' "http://127.0.0.1:18081/api/venue/resources/sellable-units/$full_unit_id/status"
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/unit-disabled.json"
step 'court and sellable unit status validated'

forbidden_status=$(curl -sS -o "$verify_dir/platform-resource-forbidden.json" -w '%{http_code}' -H "Cookie: $platform_cookie" \
    'http://127.0.0.1:18081/api/venue/resources/courts?page=1&pageSize=20')
[[ "$forbidden_status" == "403" ]]
step 'platform role rejection validated'

other_org_payload=$(printf '{"organizationName":"T3 Other Org %s","storeName":"T3 Other Store %s","adminDisplayName":"T3 Other Venue","adminUsername":"%s","initialPassword":"%s","serviceRateBasisPoints":100}' \
    "$test_tag" "$test_tag" "$other_venue_user" "$other_venue_password")
curl -sS -o "$verify_dir/other-org.json" -H 'Content-Type: application/json' -H "Cookie: $platform_cookie" -H "X-CSRF-Token: $platform_csrf" \
    -H 'Idempotency-Key: t3-other-org-1' --data "$other_org_payload" http://127.0.0.1:18081/api/admin/organizations
ruby -rjson -e 'exit(JSON.parse(File.read(ARGV[0])).fetch("code") == 1 ? 0 : 1)' "$verify_dir/other-org.json"
curl -sS -D "$verify_dir/other-venue.headers" -o "$verify_dir/other-venue.json" -H 'Content-Type: application/json' \
    --data "{\"username\":\"$other_venue_user\",\"password\":\"$other_venue_password\"}" http://127.0.0.1:18081/api/admin/auth/login
other_venue_cookie=$(awk -F ': ' 'tolower($1) == "set-cookie" {sub(/;.*/, "", $2); gsub(/\r/, "", $2); print $2; exit}' "$verify_dir/other-venue.headers")
cross_store_status=$(curl -sS -o "$verify_dir/cross-store.json" -w '%{http_code}' -H "Cookie: $other_venue_cookie" \
    "http://127.0.0.1:18081/api/venue/resources/courts/$court_id")
[[ "$cross_store_status" == "404" ]]
step 'cross-store scope rejection validated'

delete_status=$(curl -sS -o "$verify_dir/delete-version.json" -w '%{http_code}' -X DELETE -H "Cookie: $venue_cookie" \
    "http://127.0.0.1:18081/api/venue/resources/sellable-units/$full_unit_id/versions")
[[ "$delete_status" == "404" || "$delete_status" == "405" ]]
step 'no physical delete route validated'

printf 'T-03 runtime verification passed: V3 migration, A/B initial resources, full/half v1, immutable version transition, paging, status, idempotency, role/store boundaries, no delete route\n'
