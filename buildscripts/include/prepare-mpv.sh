#!/bin/bash -e

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$root/include/depinfo.sh"

source_dir="${1:-$root/deps/mpv}"
patch_file="$root/patches/mpv/0001-demux-cache-unselected-embedded-subtitles.patch"

if [ ! -d "$source_dir/.git" ]; then
	echo >&2 "mpv source must be a git checkout: $source_dir"
	exit 1
fi

actual_revision=$(git -C "$source_dir" rev-parse HEAD)
if [ "$actual_revision" != "$v_mpv" ]; then
	printf >&2 'mpv source is at %s; expected %s\n' "$actual_revision" "$v_mpv"
	echo >&2 "Remove buildscripts/deps/mpv and run download.sh again."
	exit 1
fi

if git -C "$source_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
	exit 0
fi

if ! git -C "$source_dir" apply --check "$patch_file"; then
	echo >&2 "mpv patch cannot be applied cleanly"
	exit 1
fi

git -C "$source_dir" apply "$patch_file"

if ! git -C "$source_dir" apply --reverse --check "$patch_file"; then
	echo >&2 "mpv patch verification failed"
	exit 1
fi
