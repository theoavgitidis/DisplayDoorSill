#!/usr/bin/env bash
# Install DisplayDoorSill services on Raspberry Pi OS. Read SECURITY.md first.
set -euo pipefail

APP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="${DISPLAYDOORSILL_VENV_DIR:-$APP_DIR/.venv}"
SERVICE_USER="$(id -un)"
SERVICE_HOME="$(getent passwd "$SERVICE_USER" | cut -d: -f6)"

if [[ "$SERVICE_USER" == root ]]; then
    echo "Run this script as the desktop user; it uses sudo only to install services." >&2
    exit 1
fi
for check in "$VENV_DIR/bin/python" "$APP_DIR/server.py" "$APP_DIR/ble.py"; do
    if [[ ! -f "$check" ]]; then
        echo "Missing: $check. Create .venv and install requirements.txt first." >&2
        exit 1
    fi
done
# systemd paths in these generated unit files must be unambiguous.
if [[ "$APP_DIR" =~ [[:space:]%] || "$VENV_DIR" =~ [[:space:]%] || "$SERVICE_HOME" =~ [[:space:]%] ]]; then
    echo "Use installation and home directories without whitespace or percent signs." >&2
    exit 1
fi

sudo tee /etc/systemd/system/displaydoorsill-server.service > /dev/null <<UNIT
[Unit]
Description=DisplayDoorSill display and HTTP server
After=graphical.target network-online.target
Wants=network-online.target
StartLimitIntervalSec=60
StartLimitBurst=5

[Service]
Type=simple
User=${SERVICE_USER}
WorkingDirectory=${APP_DIR}
ExecStart=${VENV_DIR}/bin/python ${APP_DIR}/server.py
Restart=on-failure
RestartSec=5
Environment=PYTHONUNBUFFERED=1
Environment=DISPLAY=:0
Environment=XAUTHORITY=${SERVICE_HOME}/.Xauthority
EnvironmentFile=-/etc/default/displaydoorsill
UMask=0077
NoNewPrivileges=true

[Install]
WantedBy=graphical.target
UNIT

# BLE accepts network credentials without authenticating the phone. It is opt-in.
if [[ "${DISPLAYDOORSILL_ENABLE_BLE_PROVISIONING:-0}" == 1 ]]; then
    sudo tee /etc/systemd/system/displaydoorsill-ble.service > /dev/null <<UNIT
[Unit]
Description=DisplayDoorSill experimental BLE provisioning
After=bluetooth.target
Wants=bluetooth.target
StartLimitIntervalSec=60
StartLimitBurst=5

[Service]
Type=simple
User=${SERVICE_USER}
WorkingDirectory=${APP_DIR}
ExecStart=${VENV_DIR}/bin/python ${APP_DIR}/ble.py
Restart=on-failure
RestartSec=5
Environment=PYTHONUNBUFFERED=1
Environment=DISPLAYDOORSILL_ENABLE_BLE_PROVISIONING=1
EnvironmentFile=-/etc/default/displaydoorsill
UMask=0077
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
UNIT
fi

sudo systemctl daemon-reload
sudo systemctl enable --now displaydoorsill-server.service
if [[ "${DISPLAYDOORSILL_ENABLE_BLE_PROVISIONING:-0}" == 1 ]]; then
    sudo systemctl enable --now displaydoorsill-ble.service
else
    # Respect the default even after an earlier opt-in installation.
    sudo systemctl disable --now displaydoorsill-ble.service 2>/dev/null || true
fi
printf '%s\n' 'Installed. HTTP defaults to loopback; BLE requires explicit opt-in.'
printf '%s\n' 'Configure local overrides in /etc/default/displaydoorsill.'
printf '%s\n' 'Other applications and legacy services have not been modified.'
