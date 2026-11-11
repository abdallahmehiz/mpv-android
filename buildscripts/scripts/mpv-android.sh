#!/bin/bash -e

BUILD="./buildscripts"

. $BUILD/include/path.sh
. $BUILD/include/depinfo.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf {app,lib,.}/build lib/src/androidMain/{libs,obj}
	exit 0
else
	exit 255
fi

nativeprefix () {
	if [ -f $BUILD/prefix/$1/lib/libmpv.so ]; then
		echo "$(realpath "$BUILD/prefix/$1")"
	else
		echo >&2 "Warning: libmpv.so not found in native prefix for $1, support will be omitted"
	fi
}

prefix32=$(nativeprefix "armv7l")
prefix64=$(nativeprefix "arm64")
prefix_x64=$(nativeprefix "x86_64")
prefix_x86=$(nativeprefix "x86")

if [[ -z "$prefix32" && -z "$prefix64" && -z "$prefix_x64" && -z "$prefix_x86" ]]; then
	echo >&2 "Error: no mpv library detected."
	exit 1
fi

# versions are read from the mpv build dir of an arch that was actually built
# (same suffix as $ndk_suffix in buildall.sh)
built=$(basename "${prefix64:-${prefix32:-${prefix_x64:-$prefix_x86}}}")
chmod +x $BUILD/scripts/write_versions.sh
$BUILD/scripts/write_versions.sh "_$built"

### Native parts
PREFIX32="$prefix32" PREFIX64="$prefix64" PREFIX_X64="$prefix_x64" PREFIX_X86="$prefix_x86" \
ndk-build -C lib/src/androidMain -j$cores

### Java parts
# Android's gradle plugin needs both of these to correctly strip libraries.
# We could pass them directly to Gradle but by using this file it will persist
# inside Android Studio too.
printf '%s\n' \
	"# This file is automatically written by the build scripts, and read using Gradle" \
	"ndkVersion=$v_ndk_n" "ndkRoot=$ANDROID_NDK_ROOT" >ndk.properties


./gradlew :lib:assembleAndroidMain
