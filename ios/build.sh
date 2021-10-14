#!/usr/bin/env bash

GITINFO=$( git describe --long --abbrev=6 --dirty=+ )
sed -e "s#\\\$GITINFO#$GITINFO#" manifest.template > manifest

echo '[mei] Building iOS Plot Projects module via Titanium SDK'


TI_SDK_VERSION="9.3.2.GA"
NODE_VERSION=10

echo '[mei] selecting node version via `n`'
n $NODE_VERSION

echo '[mei] building via Ti sdk'
ti build  -p ios -c --build-only --sdk $TI_SDK_VERSION $*

echo ''
echo '[mei] build complete'
