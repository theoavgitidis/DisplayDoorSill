# Security

DisplayDoorSill is an experimental display controller. Sharing the source does not
make the running prototype suitable for untrusted networks or vehicle deployment.

## Runtime limits

- HTTP is unauthenticated and unencrypted. The safe default binds only to loopback.
  An explicit LAN bind is for an isolated lab network with firewall restrictions.
  Do not expose it through port forwarding, a public tunnel, or an Internet host.
- BLE provisioning is opt-in and has no peer authentication or mandatory encryption.
  A nearby attacker can impersonate either endpoint or change the Wi-Fi configuration.
  Use disposable lab credentials and stop provisioning immediately after setup.
- The Android app permits cleartext HTTP to the Pi. Do not use it to send sensitive
  display content over an untrusted network.
- Wi-Fi credentials and HTTP bodies must never be written to logs. Do not share
  NetworkManager profiles, Android bug reports, or Pi journals without reviewing them.
- Physical display hardware and Bluetooth behavior require testing on the intended
  Raspberry Pi and Android devices. There is no vehicle safety certification.

Before production use, add authenticated encrypted transport, authenticated BLE
pairing or a short physical provisioning window, rate limiting, hardened image
processing, and device-specific validation.

## Reporting a problem

Do not put credentials or an exploitable security report in a public issue. Use
GitHub's private vulnerability reporting on the repository Security tab if available.
If it is unavailable, contact the maintainer through a private channel you already
trust. Revoke exposed credentials before attempting any history cleanup.

## Repository hygiene

Keep local configuration, SDK tokens, signing material, runtime data, logs, and archives
out of Git. Run `gitleaks git . --redact` before sharing a clone or publishing a branch.
Scan archives separately because Git scanners do not inspect all compressed content.
After any history rewrite, create a fresh clone; merging an old clone can restore
removed files. GitHub may retain unreachable commits in cached views even after a
rewrite. An audit of reachable history cannot promise removal from those caches or
from other people's clones.
