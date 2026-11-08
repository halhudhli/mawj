#!/usr/bin/env bash
# Mawj iPhone build: builds an UNSIGNED Release .ipa (signed later on the phone by Zeus/SideStore).
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd -P)"
derived_data="${repository_root}/build/ios-derived"
output_directory="${repository_root}/build/ios-ipa"

cd "${repository_root}"
env \
    NUVIO_IOS_DISTRIBUTION=full \
    CLANG_MODULE_CACHE_PATH="${derived_data}/ModuleCache.noindex" \
    SWIFTPM_MODULECACHE_OVERRIDE="${derived_data}/SwiftPMModuleCache.noindex" \
    ${NUVIO_KOTLIN_NATIVE_JVMARGS:+"ORG_GRADLE_PROJECT_kotlin.native.jvmArgs=${NUVIO_KOTLIN_NATIVE_JVMARGS}"} \
    xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Release \
    -sdk iphoneos \
    -destination 'generic/platform=iOS' \
    -derivedDataPath "${derived_data}" \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGN_IDENTITY= \
    build

products_directory="${derived_data}/Build/Products/Release-iphoneos"
app_path="$(/usr/bin/find "${products_directory}" -maxdepth 1 -type d -name '*.app' -print | head -n 1)"
if [[ -z "${app_path}" ]]; then
    echo "No .app was produced." >&2
    exit 1
fi
executable="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "${app_path}/Info.plist")"
if [[ " $(xcrun lipo -archs "${app_path}/${executable}") " != *" arm64 "* ]]; then
    echo "The iPhone app does not contain arm64." >&2
    exit 1
fi

mkdir -p "${output_directory}"
package_root="$(mktemp -d "${TMPDIR:-/tmp}/mawj-ipa.XXXXXX")"
trap 'rm -rf "${package_root}"' EXIT
mkdir -p "${package_root}/Payload"
ditto "${app_path}" "${package_root}/Payload/$(basename "${app_path}")"
(
    cd "${package_root}"
    /usr/bin/zip -qry "${output_directory}/Mawj-iPhone.ipa" Payload
)
unzip -tq "${output_directory}/Mawj-iPhone.ipa"
echo "Created ${output_directory}/Mawj-iPhone.ipa"
