#!/usr/bin/env bash
# Generates the android/ and ios/ folders (kept out of git) and applies the
# settings EduLens needs: camera/photo permissions and HTTP to the campus server.
# Run once after cloning:  cd flutter_app && ./setup_platforms.sh
set -euo pipefail
cd "$(dirname "$0")"

flutter create . --org in.svit --project-name edulens --platforms=android,ios >/dev/null
flutter pub get

MANIFEST=android/app/src/main/AndroidManifest.xml
if ! grep -q usesCleartextTraffic "$MANIFEST"; then
  # allow http:// to the campus server during the pilot (switch to HTTPS for rollout)
  sed -i.bak 's/<application/<application android:usesCleartextTraffic="true"/' "$MANIFEST" && rm -f "$MANIFEST.bak"
fi
if ! grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  sed -i.bak 's#<application#<uses-permission android:name="android.permission.INTERNET"/>\n    <application#' "$MANIFEST" && rm -f "$MANIFEST.bak"
fi

PLIST=ios/Runner/Info.plist
if [ -f "$PLIST" ] && ! grep -q NSCameraUsageDescription "$PLIST"; then
  /usr/libexec/PlistBuddy -c "Add :NSCameraUsageDescription string 'Take classroom photos for attendance'" "$PLIST" 2>/dev/null || true
  /usr/libexec/PlistBuddy -c "Add :NSPhotoLibraryUsageDescription string 'Choose classroom photos for attendance'" "$PLIST" 2>/dev/null || true
  /usr/libexec/PlistBuddy -c "Add :NSAppTransportSecurity:NSAllowsArbitraryLoads bool true" "$PLIST" 2>/dev/null || true
fi

echo "Done. Run:  flutter run --dart-define=API_URL=http://<server-ip>:5000"
