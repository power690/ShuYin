#!/usr/bin/env bash
set -e
source /home/z/.android_env.sh
cd /home/z/my-project/download/android_project
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /home/z/my-project/download/ZMusic-debug-1.3.0.apk
echo "APK: /home/z/my-project/download/ZMusic-debug-1.3.0.apk"
