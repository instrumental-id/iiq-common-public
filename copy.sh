#!/bin/bash

SOURCE=$1

if [[ -z ${SOURCE} ]]; then
	echo "Usage: ./copy.sh [path-to-IIQCommon]"
	exit 1
fi

rm -rf src

cp -rf "${SOURCE}/build/minimal/src" .

find . -name *.bak -delete
