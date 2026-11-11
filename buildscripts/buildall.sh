#!/bin/bash -e

cd "$( dirname "${BASH_SOURCE[0]}" )"
. ./include/depinfo.sh

cleanbuild=0
nodeps=0
clang=1
target=mpv-android
arch=armv7l

getdeps () {
	varname="dep_${1//-/_}[*]"
	echo ${!varname}
}

loadarch () {
	unset CC CXX CPATH LIBRARY_PATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH
	unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS

	if [[ "$1" == ios-* ]]; then
		loadarch_ios $1
		return
	fi
	export target_os=android

	local apilvl=21
	# ndk_triple: what the toolchain actually is
	# cc_triple: what Google pretends the toolchain is
	if [ "$1" == "armv7l" ]; then
		export ndk_suffix=
		export ndk_triple=arm-linux-androideabi
		cc_triple=armv7a-linux-androideabi$apilvl
		prefix_name=armv7l
	elif [ "$1" == "arm64" ]; then
		export ndk_suffix=-arm64
		export ndk_triple=aarch64-linux-android
		cc_triple=$ndk_triple$apilvl
		prefix_name=arm64
	elif [ "$1" == "x86" ]; then
		export ndk_suffix=-x86
		export ndk_triple=i686-linux-android
		cc_triple=$ndk_triple$apilvl
		prefix_name=x86
	elif [ "$1" == "x86_64" ]; then
		export ndk_suffix=-x64
		export ndk_triple=x86_64-linux-android
		cc_triple=$ndk_triple$apilvl
		prefix_name=x86_64
	else
		echo "Invalid architecture" >&2
		exit 1
	fi
	export prefix_dir="$PWD/prefix/$prefix_name"
	if [ $clang -eq 1 ]; then
		export CC=$cc_triple-clang
		export CXX=$cc_triple-clang++
	else
		export CC=$cc_triple-gcc
		export CXX=$cc_triple-g++
	fi
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
	[ "$cpu_family" == "i686" ] && cpu_family=x86

	if ! command -v pkg-config >/dev/null; then
		echo "pkg-config not provided!"
		return 1
	fi

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
	if [ $1 != "mpv-android" ] && [ ! -d deps/$1 ]; then
		printf >&2 '\e[1;31m%s\e[m\n' "Target $1 not found"
		return 1
	fi
	if [ $nodeps -eq 0 ]; then
		printf >&2 '\e[1;34m%s\e[m\n' "Preparing $1..."
		local deps=$(getdeps $1)
		echo >&2 "Dependencies: $deps"
		for dep in $deps; do
			build $dep
		done
	fi
	printf >&2 '\e[1;34m%s\e[m\n' "Building $1..."
	if [ "$1" == "mpv-android" ]; then
		pushd ..
		BUILDSCRIPT=buildscripts/scripts/$1.sh
	else
		pushd deps/$1
		BUILDSCRIPT=../../scripts/$1.sh
	fi
	[ $cleanbuild -eq 1 ] && $BUILDSCRIPT clean
	$BUILDSCRIPT build
	popd
}

usage () {
	printf '%s\n' \
		"Usage: buildall.sh [options] [target]" \
		"Builds the specified target (default: $target)" \
		"-n             Do not build dependencies" \
		"--clean        Clean build dirs before compiling" \
		"--gcc          Use gcc compiler (unsupported!)" \
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
		--gcc)
		clang=0
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

loadarch $arch
setup_prefix
build $target

[ "$target" == "mpv-android" ] && \
	ls -lh ../lib/build/outputs/aar/*.aar

exit 0
