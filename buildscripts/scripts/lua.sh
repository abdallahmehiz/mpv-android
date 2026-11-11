#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make clean
	exit 0
else
	exit 255
fi

# Building separately from source tree is not supported, this means we are forced to always clean
$0 clean

mycflags=(
	# ensures correct linking into libmpv.so
	-fPIC
	# bionic is missing decimal_point in localeconv [src/llex.c]
	-Dgetlocaledecpoint\\\(\\\)=\\\(46\\\)
	# force fallback as ftello/fseeko are not defined [src/liolib.c]
	-Dlua_fseek
)
# system() is unavailable on iOS [src/loslib.c], replace it after stdlib.h declared it
if [ "$target_os" == "ios" ]; then
	printf '#include <stdlib.h>\n#define system(cmd) (-1)\n' >ios_compat.h
	mycflags+=(-include "$PWD/ios_compat.h")
fi

# LUA_T= and LUAC_T= to disable building lua & luac
# -Dgetlocaledecpoint()=('.') fixes bionic missing decimal_point in localeconv
make CC="$CC" AR="$AR rc" RANLIB="$RANLIB" \
	MYCFLAGS="${mycflags[*]}" \
	PLAT=linux LUA_T= LUAC_T= -j$cores

# TO_BIN=/dev/null disables installing lua & luac
# Install to usr/local to match meson-based builds (DESTDIR + prefix=/usr/local)
lua_install_dir="$prefix_dir/usr/local"
mkdir -p "$lua_install_dir"
make INSTALL=${INSTALL:-install} INSTALL_TOP="$lua_install_dir" TO_BIN=/dev/null install

# make pc only generates a partial pkg-config file because ????
mkdir -p "$lua_install_dir/lib/pkgconfig"
make pc >"$lua_install_dir/lib/pkgconfig/lua.pc"
cat >>"$lua_install_dir/lib/pkgconfig/lua.pc" <<'EOF'
Name: Lua
Description:
Version: ${version}
Libs: -L${libdir} -llua
Cflags: -I${includedir}
EOF
