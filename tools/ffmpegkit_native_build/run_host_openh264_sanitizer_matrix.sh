#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "usage: $0 <sanitized-ffmpeg> <new-output-directory>" >&2
  exit 2
fi

ffmpeg_bin="$1"
output_directory="$2"

if [[ ! -x "${ffmpeg_bin}" ]]; then
  echo "sanitized FFmpeg executable is missing or not executable: ${ffmpeg_bin}" >&2
  exit 2
fi
if [[ -e "${output_directory}" ]]; then
  echo "output directory already exists: ${output_directory}" >&2
  exit 2
fi

mkdir -p "${output_directory}"

run_case() {
  local resolution="$1"
  local thread_count="$2"
  local repetition="$3"
  local case_name="${resolution}-${thread_count}-${repetition}"
  local output_path="${output_directory}/${case_name}.mp4"
  local log_path="${output_directory}/${case_name}.log"
  local thread_arguments=()

  if [[ "${thread_count}" != "default" ]]; then
    thread_arguments=(-threads "${thread_count}")
  fi

  ASAN_OPTIONS="abort_on_error=1:detect_leaks=1:halt_on_error=1" \
  UBSAN_OPTIONS="halt_on_error=1:print_stacktrace=1" \
    "${ffmpeg_bin}" \
      -hide_banner \
      -nostdin \
      -y \
      -f lavfi \
      -i "testsrc=size=${resolution}:rate=30" \
      -f lavfi \
      -i "sine=frequency=440:sample_rate=44100" \
      -t 2 \
      -pix_fmt yuv420p \
      -c:v libopenh264 \
      -profile:v constrained_baseline \
      -b:v 2M \
      "${thread_arguments[@]}" \
      -c:a aac \
      -b:a 64k \
      -movflags +faststart \
      -shortest \
      "${output_path}" \
      >"${log_path}" 2>&1

  if [[ ! -s "${output_path}" ]]; then
    echo "matrix output is empty: ${output_path}" >&2
    exit 1
  fi

  ASAN_OPTIONS="abort_on_error=1:detect_leaks=1:halt_on_error=1" \
  UBSAN_OPTIONS="halt_on_error=1:print_stacktrace=1" \
    "${ffmpeg_bin}" \
      -hide_banner \
      -nostdin \
      -v error \
      -i "${output_path}" \
      -map 0:v:0 \
      -map 0:a:0 \
      -f null \
      - \
      >>"${log_path}" 2>&1
}

for thread_count in default 1 2 4; do
  for repetition in 1 2 3; do
    run_case "320x240" "${thread_count}" "${repetition}"
  done
  for repetition in 1 2; do
    run_case "1280x720" "${thread_count}" "${repetition}"
  done
done

mp4_count="$(find "${output_directory}" -maxdepth 1 -type f -name '*.mp4' | wc -l)"
log_count="$(find "${output_directory}" -maxdepth 1 -type f -name '*.log' | wc -l)"
if [[ "${mp4_count}" -ne 20 || "${log_count}" -ne 20 ]]; then
  echo "matrix artifact count differs: mp4=${mp4_count} log=${log_count}" >&2
  exit 1
fi

echo "OpenH264 host sanitizer matrix: 20/20 PASS"
