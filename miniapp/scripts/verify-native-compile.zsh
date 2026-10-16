#!/usr/bin/env zsh
set -euo pipefail
miniapp_dir=${0:A:h:h}
compiler_dir='/Applications/wechatwebdevtools.app/Contents/Resources/app.asar.unpacked/node_modules/wcc-exec'
[[ -x "$compiler_dir/wcc" && -x "$compiler_dir/wcsc" ]] || { print -u2 '未找到微信本地编译器'; exit 1; }
output_dir=$(mktemp -d /tmp/sports-booking-wxml.XXXXXX)
trap 'rm -rf "$output_dir"' EXIT INT TERM
cd "$miniapp_dir"
templates=("${(@f)$(rg --files pages custom-tab-bar components -g '*.wxml')}")
styles=(tokens.wxss "${(@f)$(rg --files pages custom-tab-bar components -g '*.wxss')}")
"$compiler_dir/wcc" -o "$output_dir/templates.js" "${templates[@]}"
"$compiler_dir/wcsc" -o "$output_dir/styles.js" app.wxss "${styles[@]}"
print "WeChat native compile: ${#templates} WXML files and $(( ${#styles} + 1 )) WXSS files passed"
