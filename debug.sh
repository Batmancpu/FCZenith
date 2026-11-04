#!/bin/sh
# Build APK DEBUG (flavor dev, cepat).
# Pakai: sh debug.sh "pesan commit"
set -e
git add -A
git commit -m "${1:-debug build}" || true
git push origin HEAD
git push origin HEAD:refs/heads/debug --force
echo "Build DEBUG dimulai. Cek tab Actions di GitHub."