#!/bin/bash
set -e

TOOLS="$(ls -d ${ANDROID_HOME}/build-tools/* | tail -1)"

shopt -s globstar nullglob extglob
APKS=( **/*".apk" )

# Take base64 encoded key input and put it into a file
STORE_PATH=$PWD/signingkey.jks
rm -f $STORE_PATH && touch $STORE_PATH
echo $1 | base64 -d > $STORE_PATH

STORE_ALIAS=$2
export KEY_STORE_PASSWORD=$3
export KEY_PASSWORD=$4

# Sign all of the APKs
for APK in ${APKS[@]}; do
    ${TOOLS}/zipalign -c -v -p 4 $APK
    ${TOOLS}/apksigner sign --ks $STORE_PATH --ks-key-alias $STORE_ALIAS --ks-pass env:KEY_STORE_PASSWORD --key-pass env:KEY_PASSWORD $APK
done

rm $STORE_PATH
unset KEY_STORE_PASSWORD
unset KEY_PASSWORD