# HiOS Island 2.0

A floating live-island experiment tuned for TECNO Spark 8C / Android 11.

## 2.0 foundation
- Active media-session discovery through Android NotificationListenerService.
- Reads track title, artist, playback state, duration and position.
- Uses MediaController transport controls for play/pause and previous/next when exposed by the active session.
- Retains notification previews, battery state and timers.
- Falls back to notification action intents when an app does not expose an active media session.

## Development stages
1. Reliable core: lifecycle recovery, permission refresh, notification filtering and defensive service handling.
2. Island UI: notch-aware compact/expanded states, touch targets, smooth transitions and Spark 8C safe-area tuning.
3. Live activities: charging, timers, stopwatch, media progress and notification-specific cards.
4. Call experience: show call-state information where Android exposes it; preserve system call UI and permission boundaries.
5. Settings: app filters, animation controls, haptics, privacy controls and battery-aware updates.

Notification text and media metadata are processed locally; this app does not upload them to a server.
