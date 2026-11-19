# Locaport

Locaport is a local file transfer application for Android. It serves the
device's shared storage over HTTP, HTTPS and WebDAV, allowing phones, tablets
and computers on the same network to browse, upload and download files without
a cable, cloud account or companion client.

The application supports Wi-Fi, the phone's own hotspot, USB tethering and
Ethernet. The minimum supported Android version is 11.

## Features

- **Browser file manager.** Browse, download and upload files from any modern
  browser. Supports drag-and-drop upload, rename, delete, folder creation,
  filtering, sorting and list or grid views.
- **WebDAV access.** Mount the device in Windows Explorer or macOS Finder and
  use it as a network drive. The application provides a ready-to-use path for
  Windows.
- **Network discovery.** The device advertises itself over mDNS using a
  configurable device name, so it appears on the network without a manually
  entered IP address.
- **Access control.** QR code or address pairing, a configurable username, and
  either a random PIN per session or a fixed six-digit PIN.
- **Operational controls.** Read-only mode, idle shutdown, screen-lock
  shutdown, a foreground service and a Quick Settings tile.
- **Optional encryption.** An HTTPS listener with a self-signed certificate is
  available alongside the default HTTP listener.

## Requirements

- Android 11 or newer.
- All files access permission.
- Both devices connected to the same local network.

## Installation

Download the latest APK from the
[Releases](https://github.com/heyharshitrai/locaport/releases) page.

On first launch, grant the All files access permission. Android does not allow
the application to serve shared storage without it. Connect the phone and the
other device to the same network, then select **Start server**.

## Connecting

The main screen displays the active browser address and, when the HTTP listener
is enabled, a Windows Explorer path.

- **Browser:** open the displayed address on any device on the same network.
- **Windows:** in File Explorer, use *Map network drive* or *Add a network
  location* with the displayed path (`\\<ip>@8080\DavWWWRoot`). The HTTP address
  can also be added as a network location.
- **macOS:** in Finder, choose *Go > Connect to Server* and enter the HTTP
  address.
- **Android or iOS:** use a file manager that supports WebDAV.

HTTP is the default browser transport. Self-signed HTTPS certificates trigger a
warning page in every major browser, and public certificate authorities cannot
issue certificates for private IP addresses. The HTTPS listener remains
available on port 8443 for encrypted access; the certificate warning must be
accepted once. WebDAV uses Digest authentication over HTTP, so the PIN is not
transmitted in clear text.

## Configuration

- **Username and fixed PIN.** A random PIN is generated for each session by
  default. A fixed six-digit PIN can be enabled in Settings.
- **Device name.** Used for mDNS discovery and displayed in the browser header.
- **HTTP port.** The HTTPS port is fixed at 8443.
- **Static IP.** Android does not provide an API for applications to assign a
  static IP address. Reserve an address on the router or configure it in the
  system Wi-Fi settings; Locaport uses the address assigned to the device.

Credential changes take effect the next time the server starts.

## Security

Locaport is designed for a trusted local network and should not be exposed
directly to the internet.

- The server binds to the selected local address rather than `0.0.0.0`.
- HTTPS uses Basic authentication inside the TLS channel. Plain HTTP uses
  Digest authentication so the PIN is not transmitted. Basic authentication is
  still accepted over HTTP for clients that require it.
- Failed authentication attempts are tracked per IP address. Eight failures
  result in a five-minute lockout.
- Request paths are resolved against the shared root and rejected if they
  escape it, including encoded traversal and symbolic links.
- The TLS certificate is generated on the device and stored in a PKCS#12 file
  protected by a random password.

## Build

Requirements: JDK 21, Android SDK 35 with build-tools 35, and Gradle 8.9.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk

gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

`local.properties` must point to the Android SDK:

```properties
sdk.dir=/home/<user>/Android/Sdk
```

Release builds read signing details from `keystore.properties` in the
repository root. The file is excluded from version control. Use
`keystore.properties.example` as a template and provide the keystore path and
passwords. Without this file, the release APK is unsigned.

## Project structure

```
app/src/main/java/app/locaport/files/
├── LocaportApp.java              dynamic colour
├── server/
│   ├── LocaportServer.java       NanoHTTPD server: HTTP, HTTPS, WebDAV
│   ├── LocaportService.java      foreground service and lifecycle
│   ├── WebUi.java                browser interface
│   ├── Auth.java                 Basic and Digest authentication, lockout
│   ├── FileOps.java              path validation, copy, delete
│   ├── Tls.java                  self-signed certificate
│   └── Mdns.java                 mDNS advertisement
├── ui/                           main screen, settings, tile, notification
└── util/                         preferences, address selection, QR
```

Supported WebDAV methods: `OPTIONS`, `GET`, `HEAD`, `PUT`, `DELETE`,
`PROPFIND`, `PROPPATCH`, `MKCOL`, `COPY`, `MOVE`, `LOCK`, `UNLOCK`.

## Limitations

- WebDAV support covers Explorer, Finder and Cyberduck. Custom properties sent
  with `PROPPATCH` are acknowledged but not stored.
- mDNS does not cross VLANs and is blocked on some guest networks. The QR code
  and address remain available.
- The HTTPS listener uses a self-signed certificate and produces a browser
  warning on first use.
- Android does not allow an application to assign a static IP address.

## Credits

Developed by Harshit Rai.

Project: [github.com/heyharshitrai/locaport](https://github.com/heyharshitrai/locaport)

## License

Apache-2.0. See [LICENSE](LICENSE).
