#!/bin/sh
# Build APK RELEASE (prod, ditandatangani, masuk ke Releases).
# Pakai: sh release.sh v1.2 "pesan commit"
set -e
if [ -z "$1" ]; then
  echo "Pakai: sh release.sh v1.2"
  exit 1
fi
case "$1" in
  v*) ;;
  *) echo "Versi harus diawali v, contoh: v1.2"; exit 1 ;;
esac
git add -A
git commit -m "${2:-release $1}" || true
git push origin HEAD
git tag "$1"
git push origin "$1"
echo "Build RELEASE $1 dimulai. Cek tab Actions di GitHub."