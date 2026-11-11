#!/bin/bash -e

. ../../include/path.sh

build=_build$ndk_suffix

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf $build
	exit 0
else
	exit 255
fi

unset CC CXX # meson wants these unset

if [ "$target_os" == "ios" ]; then
	# libmpv.a is linked into the app, video goes through the render API (OpenGL ES)
	platform_args=(
		--default-library static
		-D{gl,plain-gl,ios-gl,audiounit}=enabled
		-D{vulkan,cocoa,gl-cocoa,videotoolbox-gl,coreaudio,avfoundation,swift-build}=disabled
		-D{macos-cocoa-cb,macos-media-player,macos-touchbar}=disabled
	)
else
	platform_args=(--default-library shared)
fi

meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	"${platform_args[@]}" \
	-Diconv=disabled -D{lua,libcurl}=enabled \
	-Dlibmpv=true -Dcplayer=false \
	-Dmanpage-build=disabled

ninja -C $build -j$cores
if [ "$target_os" != "ios" ] && [ -f $build/libmpv.a ]; then
	echo >&2 "Meson fucked up, forcing rebuild."
	$0 clean
	exec $0 build
fi
DESTDIR="$prefix_dir" ninja -C $build install
