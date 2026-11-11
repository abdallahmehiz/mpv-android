#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf _build$ndk_suffix
	exit 0
else
	exit 255
fi

mkdir -p _build$ndk_suffix
cd _build$ndk_suffix

cpu=armv7-a
[[ "$ndk_triple" == "aarch64"* ]] && cpu=armv8-a
[[ "$ndk_triple" == "x86_64"* ]] && cpu=generic
[[ "$ndk_triple" == "i686"* ]] && cpu="i686 --disable-asm"

cpuflags=
[[ "$ndk_triple" == "arm"* ]] && cpuflags="$cpuflags -mfpu=neon -mcpu=cortex-a8"

if [ "$target_os" == "ios" ]; then
	platform_args=(
		--target-os=darwin --enable-cross-compile
		--cc="$ios_cc" --pkg-config=pkg-config
		--arch=${ndk_triple%%-*} --cpu=$cpu
		--extra-cflags="-I$prefix_dir/include $ios_cflags" --extra-ldflags="-L$prefix_dir/lib $ios_cflags"

		--enable-{videotoolbox,audiotoolbox} --disable-securetransport
		--enable-static --disable-shared
	)
else
	platform_args=(
		--target-os=android --enable-cross-compile
		--cross-prefix=$ndk_triple- --cc=$CC --pkg-config=pkg-config --nm=llvm-nm
		--arch=${ndk_triple%%-*} --cpu=$cpu
		--extra-cflags="-I$prefix_dir/include $cpuflags" --extra-ldflags="-L$prefix_dir/lib"

		--enable-{jni,mediacodec}
		--disable-static --enable-shared
	)
fi

args=(
	"${platform_args[@]}"
	--enable-{mbedtls,libdav1d,libxml2} --disable-vulkan
	--enable-{gpl,version3}

	# disable unneeded parts
	--disable-{stripping,doc,programs}
	# to keep the build lean we disable some feature quite aggressively:
	# - muxers, encoders: mpv-android does not have any way to use these
	# - devices: no practical use on Android
	--disable-{muxers,encoders,devices}
	# useful to taking screenshots
	--enable-encoder=mjpeg,png
	# useful for the `dump-cache` command
	--enable-muxer=mov,matroska,mpegts
)
../configure "${args[@]}"

make -j$cores
make DESTDIR="$prefix_dir" install
