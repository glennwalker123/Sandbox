#!/usr/bin/env bash
# Launches Chromium full-screen on the display. Started from the desktop session
# (see deploy/README.md). Waits for the server, then keeps Chromium running.
set -u
URL="${DISPLAY_URL:-http://localhost:8080/}"

until curl -sf "${URL}api/state" >/dev/null; do sleep 2; done

# Stop "Chromium didn't shut down correctly" bubbles after power cuts.
PREFS="$HOME/.config/chromium/Default/Preferences"
[ -f "$PREFS" ] && sed -i 's/"exited_cleanly":false/"exited_cleanly":true/; s/"exit_type":"[^"]*"/"exit_type":"Normal"/' "$PREFS"

while true; do
  chromium --kiosk --noerrdialogs --disable-infobars --disable-session-crashed-bubble \
    --disable-features=Translate --check-for-update-interval=31536000 \
    --autoplay-policy=no-user-gesture-required --ozone-platform=wayland \
    "$URL"
  sleep 3
done
