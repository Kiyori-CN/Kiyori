#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_dir="$root/app/build/tmp/prepareMihomoRuntime/source/mihomo"
output="$root/app/build/mihomoSpliceTests"
test -f "$source_dir/go.mod" || { echo 'Run :app:prepareMihomoRuntime first.' >&2; exit 1; }
cmp "$root/tools/mihomo_runtime/splice_linux.go" "$source_dir/../sing/common/bufio/splice_linux.go"
command -v strace >/dev/null
export GOTOOLCHAIN="$(sed -n 's/^go.version=//p' "$root/tools/mihomo_runtime/source.properties")"
export GOOS=linux GOARCH=amd64 CGO_ENABLED=0 GOWORK=off GOFLAGS=
mkdir -p "$output"
go build -C "$source_dir" -mod=readonly -tags with_gvisor -trimpath -buildvcs=false -o "$output/mihomo" .
MIHOMO_CORE="$output/mihomo" go test -count=1 -timeout=40s -v "$root/tools/mihomo_runtime/splice_integration_test.go"
