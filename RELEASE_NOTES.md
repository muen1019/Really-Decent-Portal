# Really Decent Portal 0.1.7 Beta

[繁體中文](RELEASE_NOTES.zh-Hant.md)

GitHub beta for ARM64 devices running Android 10 (API 29) or later.

## Input highlights

- Physical trackpads support primary and secondary clicks, smooth two-finger scrolling on both axes, and double-tap-and-hold dragging. On Android 14 or later, compatible trackpads retain the pinch sensitivity tuned in 0.1.6. Pinch zoom adjusts remote content in steps.
- RDP pen input carries S Pen hover, tip contact, pressure, and barrel button events. The remote session and application determine the pen behavior.

## Changes in 0.1.7

- Updated the native connection core to FreeRDP 3.33.1 and the bundled OpenSSL to 4.0.3.
- Removed raw clipboard text from Android and JNI logs.
- Connection failures and disconnects show a readable reason. **Copy error details** provides the full error codes and diagnostic information.

## Downloads

This version pairs the signed `Really-Decent-Portal-0.1.7-arm64-v8a.apk` with `Really-Decent-Portal-0.1.7-source.zip`, `SHA256SUMS.txt`, and `RELEASE_ARTIFACTS.json`. The source archive includes the build instructions and applicable license texts.

On a Samsung tablet with Book Cover Keyboard, the beta passed hands-on checks for pinch zoom, ordinary scrolling, and a Ctrl shortcut. The tablet also reconnected to the same remote session after a brief Wi-Fi interruption. Compatibility across devices and remote apps, and stability over long sessions, are still being evaluated.
