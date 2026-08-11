#!/bin/bash -e

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$root/include/depinfo.sh"

source_dir="${1:-$root/deps/mpv}"
patch_file="$root/patches/mpv/0001-demux-cache-unselected-embedded-subtitles.patch"

actual_revision=$(git -C "$source_dir" rev-parse HEAD)
if [ "$actual_revision" != "$v_mpv" ]; then
	printf >&2 'expected mpv revision %s, found %s\n' "$v_mpv" "$actual_revision"
	exit 1
fi

before=$(git -C "$source_dir" diff --binary)
"$root/include/prepare-mpv.sh" "$source_dir"
after_first=$(git -C "$source_dir" diff --binary)
"$root/include/prepare-mpv.sh" "$source_dir"
after_second=$(git -C "$source_dir" diff --binary)

if [ -z "$after_first" ] || [ "$after_first" != "$after_second" ]; then
	echo >&2 "mpv patch application is missing or not idempotent"
	exit 1
fi

if ! git -C "$source_dir" apply --reverse --check "$patch_file"; then
	echo >&2 "mpv source does not contain the complete downstream patch"
	exit 1
fi

# A pre-patched source is valid input; preparation must leave it unchanged.
if [ -n "$before" ] && [ "$before" != "$after_first" ]; then
	echo >&2 "preparing an already-patched mpv source changed its diff"
	exit 1
fi
