#!/bin/bash

npx shadow-cljs release weebmanager

read -sp "keystore password: " password

cd android
export weebmanager_keystore_password="$password"
./gradlew assembleRelease
