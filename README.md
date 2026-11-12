# Really Decent Portal

[繁體中文](README.zh-Hant.md)

Really Decent Portal is an Android Remote Desktop client for tablet setups that use a physical trackpad, keyboard, mouse, or S Pen. It is based on FreeRDP and aFreeRDP.

## Input features

- **Physical trackpads.** Use primary and secondary clicks, smooth vertical and horizontal two-finger scrolling, diagonal scrolling when the device reports both axes, and double-tap-and-hold to drag. These controls work in both touchscreen modes.
- **Pinch to zoom.** On Android 14 or later, compatible trackpads can pinch to zoom remote content in steps. The app sends Ctrl and mouse-wheel input, so the remote application determines the zoom levels and may reflow the page.
- **S Pen.** The app forwards hover, tip contact, pressure, and the barrel button through RDP pen input. Hover tracking, pen contact, and the side button were tested on a Samsung tablet in both touchscreen modes. Pen behavior depends on the remote Windows session and application.
- **Touch, keyboard, and mouse.** Switch the screen between direct touch and an on-screen trackpad. Physical keyboards support modifiers, key repeat, and shortcuts. The optional Android shortcut service forwards supported system-level shortcuts. Physical mice support scrolling, dragging, and side buttons that can navigate browser history in supported browsers.

New connections default to full-screen resolution, 200% remote desktop scaling, and hidden Android system bars. If a connection ends, the dialog displays the reason. **Copy error details** includes the full error codes and diagnostic information.

## Install and connect

The **0.1.7 beta** targets ARM64 devices running Android 10 (API 29) or later. The app is available in English and Traditional Chinese. [Download 0.1.7 beta](https://github.com/muen1019/Really-Decent-Portal/releases/tag/v0.1.7) · [Release notes](RELEASE_NOTES.md)

1. Download and install `Really-Decent-Portal-0.1.7-arm64-v8a.apk`. Compare its SHA-256 against the matching `SHA256SUMS.txt`.
2. Add a remote PC and enter its address, port, and sign-in details.

The app uses the package name `io.github.muen1019.reallydecentportal`. An existing build signed with the same release key can be upgraded in place. Input compatibility across devices and remote apps, and stability in long sessions, are focus areas for the beta.

## Source and licenses

The modified source is in [`source/`](source/). The native core is based on [FreeRDP 3.33.1](https://github.com/FreeRDP/FreeRDP/releases/tag/3.33.1). The matching `Really-Decent-Portal-0.1.7-source.zip` includes the source, dependency versions, and build instructions. To build from this repository, point `-SourceRoot` to `source/`. [Build instructions](SOURCE_OFFER/build/BUILD.md) · [Corresponding source](SOURCE_OFFER/SOURCE_LOCATION.md)

Licenses apply by file and component. [License scope](LICENSE.md) · [Third-party notices](THIRD_PARTY_NOTICES.en.md) · [Original license texts](LICENSES/) · [Runtime component and source map](CURRENT_RUNTIME_LICENSE_MAP.json)
