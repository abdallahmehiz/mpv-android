#!/bin/bash -e

. ../../include/path.sh

[ -f configure ] || ./autogen.sh --host=$ndk_triple --without-python

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make clean
	exit 0
else
	exit 255
fi

$0 clean # separate building not supported, always clean

./configure \
	--host=$ndk_triple \
	--without-python \
	--without-iconv
make -j$cores
make DESTDIR="$prefix_dir" install
