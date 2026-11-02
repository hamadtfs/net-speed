# Net Speed

React Native Android app that shows **realtime network speed in the status bar** — useful on Pixel phones, which do not include this as a system option.

## Features

- Live download / upload speed in the status bar (overlay near Wi‑Fi icons)
- Ongoing notification with Speed, Signal, Mobile today, Wi‑Fi today
- Daily Mobile / Wi‑Fi usage history (resets at midnight) + monthly total
- Live speed chart, peak / average stats, session timer
- Auto-start on boot, battery unrestricted prompt
- One-line speed format: `11KB/s`, `1.2MB/s` (no B/s)

## Requirements

- Android 7+ (Pixel 7 recommended)
- Node 18+
- Android SDK / USB debugging for install from source

## Run from source

```bash
npm install
npm start
```

In another terminal:

```bash
npm run android
```

## First launch on Pixel

1. Tap **Start monitoring** and allow notifications
2. Turn on **Show next to Wi‑Fi icon** and allow **Display over other apps**
   - If Android shows “App was denied access”, open **App info → ⋮ → Allow restricted settings**
3. Use **◀ Left / Right ▶** to align the overlay
4. Optionally enable **Start on boot** and unrestricted battery use

## Project layout

| Path | Purpose |
| --- | --- |
| `App.tsx` | Main UI |
| `src/NetworkSpeedMonitor.ts` | JS ↔ native bridge |
| `src/SpeedChart.tsx` | Live sparkline |
| `android/.../speed/` | Foreground service, overlay, daily usage store |

## Note

Third-party apps cannot draw into the system status-bar icon tray like OEM ROMs. This app uses a supported overlay + notification approach.
