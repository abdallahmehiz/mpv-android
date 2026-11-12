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

meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	--default-library static \
	-Dauto_features=disabled \
	-Dfft=builtin -Dresampler=builtin \
	-D{jni,ladspa,lv2,vamp,cmdline,tests}=disabled

ninja -C $build -j$cores
DESTDIR="$prefix_dir" ninja -C $build install

# static lib needs the C++ runtime (and libatomic on Android) when linked into libmpv.so
extra="-lc++"
[ "$target_os" != "ios" ] && extra="$extra -latomic"
pc="$prefix_dir/lib/pkgconfig/rubberband.pc"
if [ -f "$pc" ] && ! grep -q -- '-lc++' "$pc"; then
	${SED:-sed} -i.bak "/^Libs:/ s/\$/ $extra/" "$pc"
	rm -f "$pc.bak"
fi
