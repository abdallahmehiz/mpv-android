#!/bin/bash -e

cd "$( dirname "${BASH_SOURCE[0]}" )"
. ./include/depinfo.sh

cleanbuild=0
nodeps=0
onlydeps=0
target=mpv-android
arch=armv7l

getdeps () {
	varname="dep_${1//-/_}[*]"
	echo ${!varname}
}

wasbuilt () {
	# don't use associative arrays to support bash 3 on macOS (thanks Tim Apple)
	varname="built_${1//-/_}"
	return "${!varname:-1}"
}

markbuilt () {
	varname="built_${1//-/_}"
	declare -g "$varname=0"
}

loadndk () {
	local ndk="$PWD/sdk/android-ndk-${v_ndk}"
	local toolchain=$(echo "$ndk/toolchains/llvm/prebuilt/"*)
	if [ ! -d "$toolchain" ]; then
		echo "Can't find toolchain inside NDK" >&2
		return 1
	fi
	export ANDROID_NDK_ROOT="$ndk"
	export PATH="$toolchain/bin:$ndk:$PWD/sdk/bin:$PATH"
}

loadarch () {
	unset CC CXX CPATH LIBRARY_PATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH
	unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS
	unset PKG_CONFIG_PATH

	if [[ "$1" == ios-* ]]; then
		loadarch_ios $1
		return
	fi
	export target_os=android

	local apilvl=23
	# ndk_triple: the target triple
	local cc_triple # how the compilers are actually prefixed
	if [[ "$1" == "armv7l" ]]; then
		export ndk_triple=arm-linux-androideabi
		cc_triple=armv7a-linux-androideabi$apilvl
		prefix_name=armv7l
	elif [[ "$1" == "arm64" ]]; then
		export ndk_triple=aarch64-linux-android
		cc_triple=$ndk_triple$apilvl
		prefix_name=arm64
	elif [[ "$1" == "x86" ]]; then
		export ndk_triple=i686-linux-android
		cc_triple=$ndk_triple$apilvl
		prefix_name=x86
	elif [[ "$1" == "x86_64" ]]; then
		export ndk_triple=x86_64-linux-android
		cc_triple=$ndk_triple$apilvl
		prefix_name=x86_64
	else
		echo "Invalid architecture" >&2
		exit 1
	fi
	export ndk_suffix=_$prefix_name
	export prefix_dir="$PWD/prefix/$prefix_name"
	export CC=$cc_triple-clang
	export CXX=$cc_triple-clang++
	export LDFLAGS="-Wl,-O1,--icf=safe -Wl,-z,max-page-size=16384"
	export AR=llvm-ar
	export RANLIB=llvm-ranlib
}

loadarch_ios () {
	if [[ "$OSTYPE" != "darwin"* ]] || ! command -v xcrun >/dev/null; then
		echo "Building for iOS requires macOS with Xcode (not supported in Docker)" >&2
		exit 1
	fi

	local sdk minversion
	if [ "$1" == "ios-arm64" ]; then
		ios_cpu=arm64 sdk=iphoneos ios_subsystem=ios
		minversion=-mios-version-min=$v_ios
	elif [ "$1" == "ios-sim-arm64" ]; then
		ios_cpu=arm64 sdk=iphonesimulator ios_subsystem=ios-simulator
		minversion=-mios-simulator-version-min=$v_ios
	elif [ "$1" == "ios-sim-x86_64" ]; then
		ios_cpu=x86_64 sdk=iphonesimulator ios_subsystem=ios-simulator
		minversion=-mios-simulator-version-min=$v_ios
	else
		echo "Invalid architecture" >&2
		exit 1
	fi
	export target_os=ios
	export ndk_suffix=-$1
	export ndk_triple=${ios_cpu/arm64/aarch64}-apple-darwin
	export prefix_dir="$PWD/prefix/$1"

	export ios_cflags="-arch $ios_cpu -isysroot $(xcrun --sdk $sdk --show-sdk-path) $minversion"
	export ios_cc=$(xcrun --sdk $sdk -f clang)
	export CC="$ios_cc $ios_cflags"
	export CXX="$(xcrun --sdk $sdk -f clang++) $ios_cflags"
	export AR=$(xcrun --sdk $sdk -f ar)
	export RANLIB=$(xcrun --sdk $sdk -f ranlib)
}

loadpkgconfig () {
	# set up correct paths for pkg-config
	if ! command -v pkg-config >/dev/null; then
		echo "pkg-config is missing!" >&2
		return 1
	fi
	export PKG_CONFIG_SYSROOT_DIR="$prefix_dir"
	export PKG_CONFIG_LIBDIR="$PKG_CONFIG_SYSROOT_DIR/lib/pkgconfig"
}

meson_list () {
	local out=
	for word in $1; do out+="'$word', "; done
	echo "[${out%, }]"
}

setup_prefix () {
	if [ ! -d "$prefix_dir" ]; then
		mkdir -p "$prefix_dir"
		# enforce flat structure (/usr/local -> /)
		ln -s . "$prefix_dir/usr"
		ln -s . "$prefix_dir/local"
	fi

	local cpu_family=${ndk_triple%%-*}
	[[ "$cpu_family" == "i686" ]] && cpu_family=x86

	local binaries host
	if [ "$target_os" == "ios" ]; then
		binaries="c = $(meson_list "$CC")
cpp = $(meson_list "$CXX")
objc = $(meson_list "$CC")
objcpp = $(meson_list "$CXX")
ar = '$AR'
nm = '$(xcrun -f nm)'
strip = '$(xcrun -f strip)'"
		host="system = 'darwin'
subsystem = '$ios_subsystem'
kernel = 'xnu'
cpu_family = '$cpu_family'
cpu = '$ios_cpu'"
	else
		binaries="c = '$CC'
cpp = '$CXX'
ar = 'llvm-ar'
nm = 'llvm-nm'
strip = 'llvm-strip'"
		host="system = 'android'
cpu_family = '$cpu_family'
cpu = '${CC%%-*}'"
	fi

	# meson wants to be spoonfed this file, so create it ahead of time
	# also define: release build, static libs and no source downloads at runtime(!!!)
	cat >"$prefix_dir/crossfile.tmp" <<CROSSFILE
[built-in options]
buildtype = 'release'
default_library = 'static'
wrap_mode = 'nodownload'
prefix = '/usr/local'
[binaries]
$binaries
pkgconfig = 'pkg-config'
pkg-config = 'pkg-config'
[host_machine]
$host
endian = 'little'
CROSSFILE
	# also avoid rewriting it needlessly
	if cmp -s "$prefix_dir"/crossfile.{tmp,txt}; then
		rm "$prefix_dir/crossfile.tmp"
	else
		mv "$prefix_dir"/crossfile.{tmp,txt}
	fi
}

build () {
	if [[ $1 != "mpv-android" && ! -d deps/$1 ]]; then
		printf >&2 '\e[1;31m%s\e[m\n' "Target $1 not found"
		return 1
	fi
	wasbuilt "$1" && return 0
	if [ $nodeps -eq 0 ]; then
		printf >&2 '\e[1;34m%s\e[m\n' "Preparing $1..."
		local deps=$(getdeps $1)
		echo >&2 "Dependencies: $deps"
		for dep in $deps; do
			build $dep
		done
	fi
	printf >&2 '\e[1;34m%s\e[m\n' "Building $1..."
	if [[ "$1" == "mpv-android" ]]; then
		pushd ..
		BUILDSCRIPT=buildscripts/scripts/$1.sh
	else
		pushd deps/$1
		BUILDSCRIPT=../../scripts/$1.sh
	fi
	[ $cleanbuild -eq 1 ] && $BUILDSCRIPT clean
	$BUILDSCRIPT build
	popd
	markbuilt $1
}

usage () {
	printf '%s\n' \
		"Usage: buildall.sh [options] [target]" \
		"Builds the specified target (default: $target)" \
		"" \
		"-n             Do not build dependencies" \
		"--only-deps    Build only dependencies of the specified target" \
		"--clean        Clean build dirs before compiling" \
		"--arch <arch>  Build for specified architecture (default: $arch; supported: armv7l, arm64, x86, x86_64" \
		"               and for iOS (target mpv): ios-arm64, ios-sim-arm64, ios-sim-x86_64)"
	exit 0
}

while [ $# -gt 0 ]; do
	case "$1" in
		--clean)
		cleanbuild=1
		;;
		-n|--no-deps)
		nodeps=1
		;;
		--only-deps)
		onlydeps=1
		;;
		--arch)
		shift
		arch=$1
		;;
		-h|--help)
		usage
		;;
		-*)
		echo "Unknown flag $1" >&2
		exit 1
		;;
		*)
		target=$1
		;;
	esac
	shift
done

# iOS uses the Xcode toolchain, no NDK there
[[ "$arch" == ios-* ]] || loadndk
loadarch $arch
loadpkgconfig
setup_prefix
if [ $onlydeps -eq 1 ]; then
	deps=$(getdeps $target)
	for dep in $deps; do
		build $dep
	done
else
	build $target
fi

# be helpful and list the output AARs (if they exist)
if wasbuilt "mpv-android"; then
	ls -lh ../lib/build/outputs/aar/*.aar || :
fi

exit 0
