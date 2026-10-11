#!/usr/bin/env bash
# Reproducible-build check on F-Droid's toolchain (#72, release checklist R4).
#
# Builds the public tree twice and compares the unsigned APKs byte for byte:
#   1. `fdroid build` of both recipe entries (armeabi-v7a, arm64-v8a) inside
#      F-Droid's buildserver image (Debian, its JDK, a fresh Gradle cache), from
#      a local mirror of the public commit, with the recipe in
#      docs/release/fdroid/app.marmalade.stt.yml;
#   2. `./gradlew :app:assembleFdroidRelease` on this host from a fresh clone of
#      the same commit.
# Before building it runs `fdroid lint` and `fdroid rewritemeta` on the recipe
# (the real one, GitHub Repo: and all, with the commit filled in) and fails if
# rewritemeta would change it.
#
#   tools/release/fdroid-buildserver-check.sh <public-checkout> [--work DIR]
#
#   <public-checkout>  the "repo" directory publish-public.sh prints (dry run is
#                      enough); its HEAD is the commit F-Droid will build
#   --work             scratch directory (default: <public-checkout>/../fdroid-check)
#
# Env: SUBMODULE_SOURCE=<checkout> makes the HOST clone take its third_party
# submodules from a local checkout (offline). The container always fetches
# them from their upstream URLs, as F-Droid does.
#
# Re-running skips a side whose APKs already exist in the work directory;
# delete them to rebuild. Exit status 0 only if both APKs are identical.
#
# Needs Docker, ~10 GB of disk and a couple of hours. It is a one-off build
# container (--rm), never a service. The container gets 4 cores and an 8 GB
# memory cap; both sides cap Gradle at 2 workers and 2 parallel C++ compiles
# (nativeJobs), which changes no output bytes.
set -euo pipefail

# F-Droid's buildserver image, pinned by digest (pulled 2026-10-07, built
# 2026-09-22: Debian, OpenJDK 21). Bump deliberately.
IMAGE=registry.gitlab.com/fdroid/fdroidserver@sha256:9cb68105642ca4e7b295f0ceab10f069f5b3247dc18fa7c36046e9d81aa469a8
APP=app.marmalade.stt
NDK=26.3.11579264   # the recipe's ndk: r26d

die() { echo "fdroid-buildserver-check: $*" >&2; exit 1; }
log() { echo "[$(date +%H:%M:%S)] $*"; }

pub="" work=""
while [ $# -gt 0 ]; do
  case $1 in
    --work) work=$2; shift 2 ;;
    -h|--help) sed -n '2,31p' "$0"; exit 0 ;;
    -*) die "unknown option $1" ;;
    *) [ -z "$pub" ] || die "one checkout only"; pub=$1; shift ;;
  esac
done
[ -n "$pub" ] || die "usage: $0 <public-checkout> [--work DIR]"
pub=$(cd "$pub" && pwd)
work=${work:-$(dirname "$pub")/fdroid-check}
mkdir -p "$work"; work=$(cd "$work" && pwd)
commit=$(git -C "$pub" rev-parse HEAD)
recipe_src=$pub/docs/release/fdroid/$APP.yml
[ -f "$recipe_src" ] || die "no recipe at $recipe_src"
mapfile -t codes < <(sed -n 's/^ *versionCode: *\([0-9]*\)$/\1/p' "$recipe_src")
[ "${#codes[@]}" -gt 0 ] || die "no versionCode in the recipe"
cd "$work"
log "commit $commit, versionCodes ${codes[*]}, work dir $work"

# --- 1. fdroidserver, fdroiddata, a mirror of the public commit ------------
[ -d fdroidserver ] || git clone -q --depth=1 https://gitlab.com/fdroid/fdroidserver.git fdroidserver
[ -d fdroiddata ] || git clone -q --depth=1 https://gitlab.com/fdroid/fdroiddata.git fdroiddata
rm -rf repo.git && git clone -q --mirror "$pub" repo.git
log "fdroidserver $(git -C fdroidserver rev-parse --short HEAD), fdroiddata $(git -C fdroiddata rev-parse --short HEAD)"

in_container() {   # in_container <docker args...> -- <script>
  local args=()
  while [ "$1" != -- ]; do args+=("$1"); shift; done; shift
  docker run --rm -u vagrant --entrypoint /bin/bash \
    -v "$work/fdroiddata:/build" -v "$work/fdroidserver:/home/vagrant/fdroidserver" \
    -v "$work/repo.git:/build-src/repo.git:ro" "${args[@]}" "$IMAGE" -c "
      set -euo pipefail
      . /etc/profile
      export PATH=\"\$fdroidserver:\$PATH\" PYTHONPATH=\"\$fdroidserver\"
      cd /build
      $1"
}

# --- 2. lint the real recipe ------------------------------------------------
meta=fdroiddata/metadata/$APP.yml
sed "s/COMMIT_FULL_HASH/$commit/" "$recipe_src" > "$meta"
cp "$meta" lint-input.yml
log "fdroid lint + rewritemeta"
in_container -- "fdroid lint $APP && fdroid rewritemeta $APP" > lint.log 2>&1 \
  || { cat lint.log; die "fdroid lint failed (lint.log)"; }
diff -u lint-input.yml "$meta" > rewritemeta.diff || { cat rewritemeta.diff; die "rewritemeta changes the recipe"; }
log "lint clean, rewritemeta: no changes"

# --- 3. fdroid build in the container --------------------------------------
container_apk() { echo "fdroiddata/unsigned/${APP}_$1.apk"; }
missing=()
for c in "${codes[@]}"; do [ -f "$(container_apk "$c")" ] || missing+=("$APP:$c"); done
if [ "${#missing[@]}" -gt 0 ]; then
  # Same recipe, Repo: pointed at the read-only mirror and without binary:
  # (the published APKs don't exist yet; comparing them with F-Droid's build
  # is what this script stands in for).
  sed "s|^Repo: .*|Repo: /build-src/repo.git|" lint-input.yml \
    | awk '/^    binary:/ {skip = 1; next} skip && /^      / {next} {skip = 0; print}' > "$meta"
  log "container build of ${missing[*]} (container-build.log)"
  in_container --cpuset-cpus=0-3 --memory=8g --memory-swap=12g -- "
    # The image is slimmer than the production buildserver VM: no NDK.
    sudo sdkmanager 'ndk;$NDK' > /dev/null
    mkdir -p ~/.gradle
    printf 'org.gradle.workers.max=2\nnativeJobs=2\n' > ~/.gradle/gradle.properties
    java -version 2>&1 | head -1
    fdroid build --verbose --no-tarball ${missing[*]}
  " > container-build.log 2>&1 || { tail -40 container-build.log; die "container build failed (container-build.log)"; }
  # fdroid build exits 0 even when a build fails.
  for c in "${codes[@]}"; do
    [ -f "$(container_apk "$c")" ] || { grep -E 'ERROR|FAILED' container-build.log | tail; die "no APK for $c (container-build.log)"; }
  done
fi
rm -f "$meta" lint-input.yml
log "container build done"

# --- 4. host build of the same commit --------------------------------------
host_out=host-src/app/build/outputs/apk/fdroid/release
if ! ls "$host_out"/*-release-unsigned.apk > /dev/null 2>&1; then
  rm -rf host-src && git clone -q repo.git host-src && git -C host-src checkout -q --detach "$commit"
  sargs=()
  if [ -n "${SUBMODULE_SOURCE:-}" ]; then
    for m in $(git -C host-src config -f .gitmodules --get-regexp 'submodule\..*\.path' | awk '{print $2}'); do
      sargs+=(-c "submodule.$m.url=$SUBMODULE_SOURCE/$m")
    done
    sargs+=(-c protocol.file.allow=always)
  fi
  git -C host-src submodule init -q
  git -C host-src "${sargs[@]}" submodule update -q
  log "host build (host-build.log)"
  ( cd host-src && ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk} ./gradlew --no-daemon \
      --max-workers=2 -PnativeJobs=2 :app:assembleFdroidRelease ) > host-build.log 2>&1 \
    || { tail -40 host-build.log; die "host build failed (host-build.log)"; }
fi
log "host build done"

# --- 5. compare --------------------------------------------------------------
entries() {   # sha256 of every zip entry, sorted by name
  local d; d=$(mktemp -d -p "$work")
  ( cd "$d" && unzip -qq -o "$1" && find . -type f -print0 | xargs -0 sha256sum | sed 's|  \./|  |' | sort -k2 )
  rm -rf "$d"
}
for c in "${codes[@]}"; do
  a=$work/$(container_apk "$c")
  abi=$(unzip -Z1 "$a" 'lib/*' | sed -n 's|^lib/\([^/]*\)/.*|\1|p' | sort -u)
  b=$work/$host_out/app-fdroid-$abi-release-unsigned.apk
  [ "$(echo "$abi" | wc -l)" = 1 ] && [ -f "$b" ] || die "$c: no single ABI or no host APK for '$abi'"
  echo "== $c ($abi)"
  sha256sum "$a" "$b" | sed "s|$work/||"
  entries "$a" > "entries-$c-container.txt"; entries "$b" > "entries-$c-host.txt"
  echo "entries: container $(wc -l < "entries-$c-container.txt"), host $(wc -l < "entries-$c-host.txt")"
  if cmp -s "$a" "$b"; then
    echo "IDENTICAL (whole file)"
  elif diff -u "entries-$c-container.txt" "entries-$c-host.txt" > "entries-$c.diff"; then
    echo "DIFFERENT files, identical entries: zip metadata or order differs"
  else
    echo "DIFFERENT entries (entries-$c.diff):"
    grep '^[-+][0-9a-f]' "entries-$c.diff" | awk '{print $2}' | sort -u
  fi
done | tee compare.txt
status=$(grep -q '^DIFFERENT' compare.txt && echo 1 || echo 0)
log "done (compare.txt)"
exit "$status"
