#!/bin/bash -e

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$root/include/depinfo.sh"

source_dir="${1:-$root/deps/mpv}"

if [ ! -e "$source_dir" ]; then
	mkdir -p "$source_dir"
	git -C "$source_dir" init -q
	git -C "$source_dir" remote add origin https://github.com/mpv-player/mpv.git
	git -C "$source_dir" fetch --depth 256 origin "$v_mpv" tag "$v_mpv_base_tag"
	git -C "$source_dir" checkout --detach "$v_mpv"
fi

"$root/include/prepare-mpv.sh" "$source_dir"
