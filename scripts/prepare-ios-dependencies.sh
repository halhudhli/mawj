#!/usr/bin/env bash
# Mawj iPhone build: fetches the two native pieces the iOS app links against.
#  - MPVKit (the video player), at the revision this code was written for.
#  - Nuvio Engine (torrent / P2P streaming), the published Apple package, checksum-verified.
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd -P)"
mpvkit_url=https://github.com/luqmanfadlli/MPVKit.git
mpvkit_revision=bfeb6fcfa2bd5081a9666ae5bb42b50e6c1537e8
engine_version=0.1.5
engine_checksum=8d2a7d9b5def39bff0f5ee77a4c600bb14791b2ec388d05eeecae6424fdeab36
engine_root="${NUVIO_ENGINE_ROOT:-${repository_root}/../nuvio-engine}"
engine_framework="${engine_root}/platform/apple/NuvioEngine.xcframework"

if [[ ! -f "${repository_root}/MPVKit/Package.swift" ]]; then
    rm -rf "${repository_root}/MPVKit"
    git init -q "${repository_root}/MPVKit"
    git -C "${repository_root}/MPVKit" remote add origin "${mpvkit_url}"
    git -C "${repository_root}/MPVKit" fetch -q --depth 1 origin "${mpvkit_revision}"
    git -C "${repository_root}/MPVKit" checkout -q FETCH_HEAD
fi

if [[ -f "${engine_framework}/Info.plist" ]]; then
    exit 0
fi

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/mawj-ios-dependencies.XXXXXX")"
trap 'rm -rf "${temporary_directory}"' EXIT

archive="${temporary_directory}/nuvio-engine-apple-${engine_version}.zip"
curl --fail --location --retry 5 --retry-all-errors --silent --show-error \
    --output "${archive}" \
    "https://github.com/NuvioMedia/nuvio-engine/releases/download/v${engine_version}/nuvio-engine-apple-${engine_version}.zip"

actual_checksum="$(shasum -a 256 "${archive}" | awk '{print $1}')"
if [[ "${actual_checksum}" != "${engine_checksum}" ]]; then
    echo "Nuvio Engine Apple package checksum mismatch." >&2
    exit 1
fi

extraction_root="${temporary_directory}/extracted"
mkdir -p "${extraction_root}"
unzip -q "${archive}" -d "${extraction_root}"
source_framework="$(find "${extraction_root}" -type d -name NuvioEngine.xcframework -print -quit)"
if [[ -z "${source_framework}" || ! -f "${source_framework}/ios-arm64/libCNuvioEngine.a" ]]; then
    echo "Nuvio Engine Apple package is incomplete." >&2
    exit 1
fi
mkdir -p "$(dirname "${engine_framework}")"
ditto "${source_framework}" "${engine_framework}"
