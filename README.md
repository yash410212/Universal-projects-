# HiOS Island 2.2

A responsive live-island overlay experiment tuned for TECNO Spark 8C / Android 11.

## 2.2 polish and system integration
- Notification listener restores the latest active notification when access connects.
- Notification previews carry their originating content action; tapping the expanded notification card opens the source notification when its PendingIntent is available.
- Notification removal is matched by notification key so dismissing an unrelated notification does not clear the currently displayed preview.
- Media session callbacks publish metadata and playback-state changes while a session is active.
- Setup screen re-checks overlay and notification-listener permissions when returning from Android Settings.
- Existing adaptive glass UI, compact/expanded layout, media controls, timer shortcuts and battery state are retained.

## Platform and privacy notes
- Notification access is optional for starting the overlay, but required for notification previews and media-session integration.
- Android 11 does not expose a universal, low-cost way for third-party overlays to blur arbitrary apps underneath. The island uses layered translucent styling instead of claiming true live background blur.
- Notification text and media metadata are processed locally. This app does not upload them to a server.
- OEM battery management may restrict background execution; allow the app to run in the background if the system stops it.

## Build and verification
Build with `gradle assembleDebug`. CI success confirms compilation and artifact upload, not device-level correctness. Test notification click-through, notification dismissal, media-session switching, permission refresh, overlay positioning and service stability on the target phone.
