# DisplayDoorSill

An Android app and Raspberry Pi controller for a narrow display. The app sends text,
colors, brightness settings, and images to the Pi over a local network. Experimental
Bluetooth Low Energy (BLE) provisioning can connect the Pi to Wi-Fi.

This is a prototype for a bench or isolated lab network. Read [SECURITY.md](SECURITY.md)
before enabling network access or BLE provisioning. It is not validated for vehicle
installation or safety-critical use.

## Android app

Open this directory in Android Studio, or install JDK 17 and Android SDK 35 and run:

```sh
./gradlew testDebugUnitTest assembleDebug
```

Set `ANDROID_HOME` to your SDK directory, or use an untracked `local.properties` file.
The app uses public Google and Maven Central dependencies. No vendor SDK login or
private Maven repository is required. Its display name is `DisplayDoorSill`; the
existing `com.example.desl_app` application ID and BLE identifiers remain compatible
with existing prototypes.

The bundled icon and two image presets are original generic display graphics.
Custom image uploads are also supported.

## Raspberry Pi

Use a Raspberry Pi with a graphical desktop, Python 3.11–3.13, NetworkManager,
and BlueZ. Install the OS Tk bindings (for example `sudo apt install python3-tk`)
and create a virtual environment:

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
python server.py
```

The HTTP server binds to `127.0.0.1:5000` by default. To use the Android app on an
isolated network, explicitly bind to the Pi's LAN address (replace the example):

```sh
DISPLAYDOORSILL_HOST=192.168.1.50 python server.py
```

The HTTP API has no authentication or TLS. Anyone who can reach its port can control
the display. Restrict access with the network firewall and never forward port 5000
to the Internet. Requests are limited to 10 MiB.

BLE provisioning is disabled by default. On a controlled bench, temporarily run:

```sh
DISPLAYDOORSILL_ENABLE_BLE_PROVISIONING=1 python ble.py
```

BLE does not authenticate the phone or require an encrypted, paired connection.
Nearby devices can submit Wi-Fi credentials, and an attacker may impersonate the
Pi. Use only a disposable lab network and stop `ble.py` after setup. NetworkManager
stores the resulting connection profile on the Pi; protect the device and its storage.

Configuration is supplied through process environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DISPLAYDOORSILL_HOST` | `127.0.0.1` | HTTP bind address |
| `DISPLAYDOORSILL_DATA_DIR` | `~/.local/share/DisplayDoorSill/images` | Uploaded backgrounds |
| `DISPLAYDOORSILL_BACKGROUND` | empty | Optional local fallback image path |
| `DISPLAYDOORSILL_ENABLE_BLE_PROVISIONING` | disabled | Set to `1` for experimental BLE |

Run `bash setup_autoboot.sh` as the Pi desktop user to install the display service.
It uses sudo for its own systemd units and leaves legacy and unrelated services
alone. BLE is installed and started only if you explicitly set the opt-in variable
when running the installer. Local service overrides belong in
`/etc/default/displaydoorsill`; do not commit that file or Wi-Fi credentials.

## Development checks

```sh
python -m unittest discover -s tests -v
gitleaks git . --redact
```

The GitHub security workflow scans the complete reachable Git history and runs the
Python regression tests. Dependabot checks GitHub Actions, Gradle, and Python dependencies.
Do not commit signing keys, credentials, local IDE state, runtime images, logs, or
source archives. These are covered by `.gitignore`.

## Publication and licensing

No project-wide open-source license has been selected. Individual files with upstream
license notices, including the generated Gradle wrapper, retain those notices. Confirm
ownership or permission for all contributed code before granting a project-wide license. The public version removes the
vendor SDK integration, vendor media, and store metadata. This project is independent
and carries no vehicle-manufacturer affiliation or endorsement.
