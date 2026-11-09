# Raspberry Pi setup

These steps are for a Raspberry Pi 5 running **Raspberry Pi OS (64-bit, Bookworm or newer, desktop)**, with the screen connected over HDMI.

## 1. Base system

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y chromium curl git
# Node 22 LTS
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

Set the Pi's timezone to your home's (`sudo raspi-config` → Localisation). Calendar times use it.

## 2. App

```bash
cd ~
git clone https://github.com/glennwalker123/Sandbox.git
ln -s ~/Sandbox/smart-display ~/smart-display
cd ~/smart-display
npm install
npm run build
cp .env.example .env && nano .env      # location, calendar URL(s), API keys
```

To try it with sample data first, run `MOCK=1 npm start` and open http://localhost:8080.

## 3. Start on boot

```bash
sudo cp deploy/smart-display.service /etc/systemd/system/
sudo systemctl enable --now smart-display
journalctl -u smart-display -f          # logs
```

If your user isn't `pi`, edit `User=` and `WorkingDirectory=` in the service file first.

## 4. Kiosk browser

1. Enable desktop auto-login: `sudo raspi-config` → System → Boot / Auto login → Desktop Autologin.
2. Add the kiosk script to the Wayland (labwc) autostart:

   ```bash
   mkdir -p ~/.config/labwc
   echo "$HOME/smart-display/deploy/kiosk.sh &" >> ~/.config/labwc/autostart
   ```

3. Unplug the mouse (the page also hides the pointer while it's over the display). Reboot, and the art should come up full-screen.

## 5. Portrait mounting

Rotate the output, and the page re-lays itself out and starts painting portrait art:

```bash
wlr-randr                                   # find the output name, e.g. HDMI-A-1
wlr-randr --output HDMI-A-1 --transform 90  # 270 if it's upside down
```

To make the rotation stick, add the same line to `~/.config/labwc/autostart`, *above* the `kiosk.sh` line. Alternatively, set it once in the Screen Configuration app, which saves it to `~/.config/kanshi/config`.

If you use a laptop panel (e.g. the Framework display) through an eDP→HDMI board, the board passes on the panel's EDID, so the Pi should pick up the full resolution (e.g. 2256×1504) automatically. If it doesn't, check `wlr-randr` for the available modes.

If the text feels small from across the room, set `UI_SCALE=1.2` (or higher) in `.env`.

## 6. Screen schedule (optional)

The page dims itself at night and during `QUIET_HOURS`. To switch the panel off completely, use `wlr-randr`. For example, add these lines with `crontab -e`:

```
0 23 * * * WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000 wlr-randr --output HDMI-A-1 --off
0 6  * * * WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000 wlr-randr --output HDMI-A-1 --on
```

## Setup page

From any phone or laptop on the home network, open `http://<pi-hostname>.local:8080/setup.html` to:

- see status
- generate a new piece now
- connect Google Photos and choose photos

**Google sign-in limitation:** Google only allows `localhost` (or a real HTTPS domain) as an OAuth redirect. That means the *Connect Google account* step must be done once on the Pi itself, at http://localhost:8080/setup.html. After that, choosing photos works from any device.
