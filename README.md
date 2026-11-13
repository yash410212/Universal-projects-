# HiOS Island 2.1

A responsive live-island overlay experiment tuned for TECNO Spark 8C / Android 11.

## 2.1 visual and interaction upgrade
- Adaptive compact and expanded widths based on available display width.
- Layered translucent gradient surfaces, subtle glass-like border and elevated depth.
- Animated entrance, expansion, collapse and action feedback.
- Dedicated activity card hierarchy for media, notifications, timers and charging.
- Media progress indicator and media-session transport controls.
- Timer shortcuts, battery state and swipe gestures retained.
- Defensive overlay permission check and receiver registration for Android 13+.

## Platform notes
Android 11 does not expose a universal, low-cost way for a third-party overlay to blur arbitrary apps underneath it. The interface therefore uses layered translucent glass styling rather than promising true live background blur. Some OEM power managers may still stop background services; users may need to allow HiOS Island to run in the background.

Notification previews and media metadata are processed locally. The app does not upload this information to a server.

## Roadmap
1. 2.1: adaptive glass UI and interaction foundation.
2. 2.2: robust notification queue, per-app filters and richer live activity states.
3. 2.3: settings for appearance, animation, haptics and battery-aware behavior.
4. Future: optional artwork rendering and deeper media-session lifecycle handling.

Build with `gradle assembleDebug`. CI success confirms compilation, not device-level behavior.
