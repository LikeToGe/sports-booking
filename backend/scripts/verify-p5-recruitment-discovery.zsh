#!/usr/bin/env zsh
# source 后调用；使用隔离 MySQL 容器、已启动的 API 创建独立测试事实。
verify_p5_recruitment_discovery() {
  local container="${P5_MYSQL_CONTAINER:-${mysql_container:-}}"
  [[ -n "$container" ]] || { print -u2 '缺少 P5_MYSQL_CONTAINER 或 mysql_container'; return 1; }
  local api_base="${P5_API_BASE:-${base:-http://127.0.0.1:8091/api}}"
  local script_dir="${backend_dir:-${${(%):-%x}:A:h:h}}"
  P5_MYSQL_CONTAINER="$container" P5_API_BASE="$api_base" \
    python3 "$script_dir/scripts/verify-p5-recruitment-discovery.py"
}
