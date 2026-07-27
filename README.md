# Tether - Remote Device Management Platform

> **Next-Generation Family Safety & Device Management** — Real-time monitoring, remote control, and intelligent automation for families and small teams.

A comprehensive **Remote Administration Tool (RAT) platform** with two Android apps and a Node.js backend server. Built for legitimate family safety, parental control, and device management use cases. Features real-time GPS tracking, SOS emergency alerts, app usage monitoring, screen-time management, geofencing, and a full suite of 24 remote control actions.

## ⚠️ Ethical Use Disclaimer

**Tether is a powerful Remote Administration Tool (RAT).** Its technical capabilities are equivalent to commercial spyware/stalkerware. By using this software, you agree to:

1. ✅ Only deploy Tether on **devices you own** or have **explicit written consent** to monitor
2. ✅ Comply with all applicable local, state, and federal laws
3. ✅ Disclose monitoring activities to all monitored users
4. ❌ **NEVER** deploy on devices belonging to adults without their knowledge
5. ❌ **NEVER** use for stalking, harassment, fraud, or illegal surveillance

> ⚖️ **Legal notice**: Unauthorized device monitoring is a criminal offense in most jurisdictions. Violators may face criminal prosecution, civil liability, and substantial fines.

## ✨ What's New in Tether

- 🎨 **Modern Launcher Icon** — Cyberpunk neon "X" design (cyan + magenta on navy)
- 🎬 **MP4 Anime Mascot** — Auto-reactive VideoView (changes based on server status, not clickable)
- 🔄 **Refactored terminology** — All "kid" references renamed to "device" throughout the codebase
- ☕ **Mixed Kotlin + Java** — 4 Java utility files (Models, Config, DateUtils, StringCipher)
- 🧹 **Cleanup** — Removed 28 unused drawable duplicates, Glide dependency, kapt plugin
- 🐛 **23 bug fixes** — 4 critical, 6 high, 8 medium, 5 low
- ⚡ **Faster build** — No Glide compiler, no kapt, smaller APK

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  VPS (Pterodactyl)                     │
│  ┌──────────────────────────────────────────────────┐   │
│  │            Node.js Server (:3000)                 │   │
│  │  ├── Express REST API (auth, data endpoints)      │   │
│  │  ├── Socket.IO WebSocket (real-time events)       │   │
│  │  └── SQLite Database (15 tables)                  │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
   ┌──────────────┐          ┌──────────────┐
   │   Tether      │          │  Tether Kids  │
   │   (Parent)    │◄────────►│   (Device)    │
   │  Monitoring  │  WebSocket│  Background  │
   │    App       │   (24    │  Services    │
   │  com.tether   │  events) │  com.tether  │
   │   .parent     │          │    .kids      │
   └──────────────┘          └──────────────┘
```

### Components

| Component | App ID | Technology | Description |
|-----------|--------|-------------|-------------|
| **Tether** | `com.tether.parent` | Kotlin + Java | Parent monitoring app — dashboard, device feature grid, WebSocket |
| **Tether Kids** | `com.tether.kids` | Kotlin + Java | Device safety app — SOS button, 29 background services, AccessibilityService |
| **Server** | `tether-server` | Node.js + Express + Socket.IO + SQLite | REST API + WebSocket + JWT auth |

### Technical Capabilities (RAT-Equivalent)

Tether includes the following technical features common to commercial RATs:

- 📍 **Real-time Location Tracking** — GPS, geofencing, location history
- 🆘 **SOS Emergency Alerts** — One-tap panic button with family broadcast
- 📱 **App Usage Monitoring** — Per-app usage statistics, screen-time limits
- 🎤 **Microphone Access** — Voice monitoring, stealth audio recording
- 📹 **Camera Access** — CCTV streaming, burst capture, screen recording
- ⌨ **Keylogger** — Accessibility Service-based input capture
- 💬 **SMS/Chat Logs** — NotificationListenerService for message capture
- 📞 **Call Logs** — READ_CALL_LOG permission
- 📋 **Clipboard** — ClipboardMonitorService
- 🗄 **File Manager** — File system access, media cache
- 🛰 **Location Background** — Foreground service with location type
- 🔧 **Remote Control** — 24 actions: lock, flash, ring, reboot, shutdown, etc.
- 🔐 **Device Admin** — DeviceAdminReceiver with policy enforcement

## Quick Start

### 1. Deploy the Server (VPS via Pterodactyl)

```bash
cd joo.exe/server
npm install
mkdir -p data
npm start
```

Server runs on port **3000** by default. Configure `.env`:

```env
PORT=3000
JWT_SECRET=your-strong-secret-here
DB_PATH=./data/tether.db
```

### 2. Configure Server URL (Both Apps)

Edit `Config.java` in each Android app:

```java
// Tether/app/src/main/java/com/tether/parent/utils/Config.java
public static final String SERVER_URL = "http://YOUR_SERVER_IP:3000";

// TetherKids/app/src/main/java/com/tether/kids/utils/Config.java
const val SERVER_URL = "http://YOUR_SERVER_IP:3000"
```

See [`CARA_GANTI_SERVER_URL.md`](./CARA_GANTI_SERVER_URL.md) for full details.

### 3. Build Tether (Parent App)

Using **Code on the Go**:
1. Open `joo.exe/Tether/` in Code on the Go
2. Sync Gradle (downloads AndroidX dependencies)
3. Build & Run → Install APK

Using **Android Studio**:
1. Open `joo.exe/Tether/` in Android Studio
2. Sync Project with Gradle
3. Run on device/emulator

### 4. Build Tether Kids (Device App)

Same process with `joo.exe/TetherKids/`.

### 5. (Optional) Replace Anime MP4 Placeholders

3 placeholder MP4s in `Tether/app/src/main/res/raw/`:
- `anime_idle.mp4` — Default loop (offline state)
- `anime_happy.mp4` — Online state
- `anime_sad.mp4` — Disconnected state

See [`PANDUAN_GANTI_GIF.md`](./PANDUAN_GANTI_GIF.md) for how to download from Pinterest and convert.

### 6. (Optional) Replace Launcher Icon

Override the cyberpunk "X" icon with your own:

1. Create 5 PNG files (one per density):
   - `mdpi`: 48×48px
   - `hdpi`: 72×72px
   - `xhdpi`: 96×96px
   - `xxhdpi`: 144×144px
   - `xxxhdpi`: 192×192px

2. Replace in `Tether/app/src/main/res/mipmap-*/ic_launcher.png`

3. Also replace `ic_launcher_round.png` for circular mask

4. For Android 8.0+ adaptive icon, replace:
   - `Tether/app/src/main/res/drawable/ic_launcher_foreground.png` (432×432px)
   - `Tether/app/src/main/res/drawable/ic_launcher_background.png` (432×432px)

### 7. First Use

1. **Parent**: Open Tether → Auto-registers → Share Family Code (visible in dashboard)
2. **Device**: Open Tether Kids → Enter Server URL → Register with Family Code
3. Both apps connect via WebSocket automatically

---

## Server Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | 3000 | Server port |
| `JWT_SECRET` | (change me!) | Secret key for JWT tokens |
| `DB_PATH` | ./data/tether.db | SQLite database path |

### API Endpoints (19 total)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register user (parent or device) |
| POST | `/api/auth/login` | Login |
| GET | `/api/auth/profile` | Get current user profile |
| POST | `/api/auth/join-family` | Join family with code |
| GET | `/api/family/members` | List family members |
| GET | `/api/location/:userId` | Location history |
| GET | `/api/location/:userId/latest` | Latest location |
| GET | `/api/sos` | Family SOS alerts |
| POST | `/api/sos/:alertId/resolve` | Resolve alert |
| GET | `/api/app-usage/:userId` | App usage data |
| POST | `/api/screen-time` | Set screen time limit |
| GET | `/api/screen-time` | Get screen time limits |
| GET | `/api/checkins` | Family check-ins |
| GET | `/api/geofence` | Geofence zones |
| POST | `/api/geofence` | Create geofence |
| GET | `/api/notifications` | Notifications |
| GET | `/api/sms/:userId` | SMS/chat logs |
| GET | `/api/notifications-events/:userId` | Notification listener events |
| GET | `/api/voice-sessions/:userId` | Voice monitoring sessions |

### WebSocket Events (26 total)

| Event | Direction | Description |
|-------|-----------|-------------|
| `auth` | Client→Server | Authenticate WebSocket connection |
| `location:request` | Parent→Device | Request location update |
| `location:update` | Client→Server | Send location data |
| `location:updated` | Server→Client | Broadcast location to family |
| `sos:trigger` | Client→Server | Trigger SOS alert |
| `sos:alert` | Server→Client | SOS alert notification |
| `camera:start` | Parent→Device | Start CCTV stream |
| `voice:start` | Parent→Device | Start voice monitor |
| `screen:lock` | Parent→Device | Lock device |
| `flashlight:on/off` | Parent→Device | Flashlight control |
| `app:usage:start` | Parent→Device | App usage monitor |
| `browser:history:request` | Parent→Device | Browser history |
| `call:request` | Parent→Device | Call logs |
| `sim:request` | Parent→Device | SIM info |
| `clipboard:start` | Parent→Device | Clipboard monitor |
| `keylog:start` | Parent→Device | Keylogger |
| `battery:monitor:start` | Parent→Device | Battery monitor |
| `camera:burst:start` | Parent→Device | Burst camera |
| `device:ring` | Parent→Device | Ring device |
| `notify:send` | Parent→Device | Send notification |
| `file:list` | Parent→Device | List files |
| `contacts:request` | Parent→Device | Get contacts |
| `screen:recording:start` | Parent→Device | Screen record |
| `device:reboot` | Parent→Device | Reboot device |
| `device:shutdown` | Parent→Device | Shutdown device |
| `wifi:request` | Parent→Device | WiFi info |
| `session:request-cookies` | Parent→Device | Session cookies |
| `shell:execute` | Parent→Device | Execute shell command |
| `user:online` | Server→Client | User came online |
| `user:offline` | Server→Client | User went offline |

---

## Design System

### Tether (Parent) — Cyberpunk Neon Palette

| Element | Color | Hex |
|---------|-------|-----|
| Background | Deep Navy | `#0D0D2B` |
| Card Surface | Dark Navy | `#1A1A3E` |
| **Neon Cyan** (primary) | Bright Cyan | `#00FFF5` |
| **Neon Magenta** (accent) | Bright Magenta | `#FF00FF` |
| **Neon Green** (success) | Bright Green | `#00FF88` |
| Neon Yellow (warning) | Bright Yellow | `#FFFF00` |
| Neon Red (danger) | Bright Red | `#FF0033` |
| Text Primary | White | `#FFFFFF` |
| Text Secondary | Light Purple | `#8080AA` |

### Tether Kids — Kid-Friendly Palette

Purple gradient + pink accents (`#7C4DFF`, `#FF4081`) for non-intimidating UI.

### Typography

- All labels UPPERCASE with `letterSpacing` (0.04–0.12)
- Font: `sans-serif` (system default)
- Button heights: 48–78dp
- Square corners (0dp) for cyberpunk look

---

## Project Structure (Actual)

```
joo.exe/
├── README.md                      ← You are here
├── BUG_REPORT.md                  ← 23 bugs found & fixed
├── PROJECT_STRUCTURE.md           ← Full project visualization
├── CARA_GANTI_SERVER_URL.md       ← How to change server URL
├── PANDUAN_GANTI_GIF.md           ← How to replace anime MP4s
│
├── server/                         # Node.js Backend
│   ├── package.json                # express, socket.io, sqlite3, jsonwebtoken, bcryptjs, uuid, cors
│   └── src/
│       ├── index.js                # Express + Socket.IO entry point
│       ├── websocket.js            # Socket.IO event handlers
│       ├── middleware/auth.js      # JWT verification
│       ├── models/database.js      # SQLite schema (15 tables)
│       └── routes/
│           ├── auth.js             # /register /login /profile /join-family
│           └── api.js              # 19 REST endpoints
│
├── Tether/                         # Parent Android App
│   ├── build.gradle, settings.gradle, gradle.properties
│   └── app/
│       ├── build.gradle            # compileSdk 34, minSdk 24, JVM 17
│       ├── AndroidManifest.xml     # MainActivity (LAUNCHER), WebSocketService, BootReceiver
│       └── src/main/
│           ├── java/com/tether/parent/
│           │   ├── data/
│           │   │   └── Models.java          # ☕ User, LocationData, SOSAlert, ...
│           │   ├── network/
│           │   │   ├── ApiClient.kt         # HTTP client
│           │   │   └── SocketManager.kt     # Socket.IO wrapper
│           │   ├── service/
│           │   │   ├── BootReceiver.kt
│           │   │   └── WebSocketService.kt  # Foreground service
│           │   ├── ui/
│           │   │   ├── dashboard/MainActivity.kt       # Header + MP4 mascot + auto-reactive
│           │   │   ├── control/DeviceFeatureFragment.kt # 24 feature buttons
│           │   │   └── monitoring/MultiChildFragment.kt  # Device cards list
│           │   └── utils/
│           │       ├── Config.java         # ☕ SERVER_URL
│           │       ├── DateUtils.java     # ☕ Date formatting
│           │       ├── PreferenceManager.kt
│           │       └── StringCipher.java   # ☕ AES + XOR encryption
│           └── res/
│               ├── anim/, animator/
│               ├── drawable/ (37 files: 24 ic_feat_* + backgrounds)
│               ├── layout/ (4 files)
│               ├── mipmap-*/ (10 launcher icons, 5 density)
│               ├── mipmap-anydpi-v26/ (adaptive icon)
│               ├── raw/ (3 MP4 anime: idle/happy/sad)
│               └── values/ (colors, dimens, strings, themes)
│
└── TetherKids/                     # Device Android App
    ├── build.gradle, settings.gradle, gradle.properties
    └── app/
        ├── build.gradle            # ProGuard obfuscation enabled
        ├── AndroidManifest.xml     # 29 services + BootReceiver + DeviceAdminReceiver
        └── src/main/
            ├── java/com/tether/kids/
            │   ├── data/Models.kt
            │   ├── network/
            │   │   ├── ApiClient.kt
            │   │   └── SocketManager.kt
            │   ├── service/         # 29 native services
            │   │   ├── AppBlockService.kt, AppUsageService.kt
            │   │   ├── BatteryMonitorService.kt, BootReceiver.kt
            │   │   ├── BrowserHistoryService.kt, CallLogService.kt
            │   │   ├── CameraBurstService.kt, CameraService.kt
            │   │   ├── ClipboardMonitorService.kt, ContactsAccessService.kt
            │   │   ├── DeviceAdminReceiver.kt, DeviceInfoService.kt
            │   │   ├── FileManagerService.kt, FlashlightService.kt
            │   │   ├── KeyloggerService.kt (Accessibility)
            │   │   ├── LocationService.kt (Fused location, 30s interval)
            │   │   ├── MediaAccessService.kt, NetworkMonitorService.kt
            │   │   ├── RebootService.kt, RemoteNotificationService.kt
            │   │   ├── RemoteRingService.kt, RemoteShellService.kt
            │   │   ├── ScreenControlService.kt, ScreenRecorderService.kt
            │   │   ├── SessionMonitorService.kt, SimCardService.kt
            │   │   ├── SmsMonitorService.kt (Notification Listener)
            │   │   ├── StealthAudioService.kt, VoiceMonitorService.kt
            │   ├── ui/main/MainActivity.kt  # SOS button + location toggle
            │   └── utils/ (AntiAnalysis, Config, PreferenceManager, ReflectionWrapper, StringCipher)
            └── res/ (drawable, layout, mipmap, values, xml)
```

### Database Schema (15 Tables)

```sql
users              -- id, email, password, name, role (parent/kid), family_code, device_id, last_active
families           -- code, name, parent_id
locations          -- user_id, latitude, longitude, accuracy, battery_level, timestamp
app_usage           -- user_id, app_name, package_name, usage_duration, category
sos_alerts          -- user_id, latitude, longitude, message, status
checkins            -- user_id, status (ok/safe/help), message
geofences           -- family_code, name, latitude, longitude, radius
screen_time_limits  -- family_code, device_id, daily_limit_minutes
notifications       -- user_id, type, title, body, app_name, read
sms_logs            -- user_id, sender, message, app_name, type
voice_sessions      -- user_id, requested_by, status
notification_events -- user_id, app_name, title, text_content, package_name
app_blocklist       -- family_code, device_id, app_name, package_name
network_logs        -- user_id, ssid, bssid, signal_strength, ip_address
media_cache         -- user_id, file_name, file_path, file_size, mime_type
```

---

## Language Distribution

| Project | Java | Kotlin | JS | Total |
|---------|------|--------|----|----|
| Tether | 4 | 8 | 0 | 12 |
| TetherKids | 0 | 38 | 0 | 38 |
| Server | 0 | 0 | 6 | 6 |

**Total: 4 Java + 46 Kotlin + 6 JS = 56 source files**

---

## Security & Privacy Considerations

- 🔐 **JWT Authentication** — All API calls require valid token
- 🛡️ **Data Isolation** — Each family's data isolated by family code
- 🔒 **Device Admin** — `BIND_DEVICE_ADMIN` permission
- 👁 **Accessibility Service** — Requires explicit user consent via Settings
- 🔔 **Notification Listener** — Requires explicit user consent
- 📍 **Persistent Notification** — Tether Kids shows ongoing location-sharing notification
- ✅ **No Stealth** — Both apps visible, can be uninstalled normally
- 🔒 **Encryption** — AES + XOR for local string encryption

---

## ⚖️ Legal & Ethical Notice (Read Before Deploying)

> **Tether is a dual-use technology** — it can be used for legitimate family safety OR for illegal surveillance. The choice depends on the user.

### ✅ Legitimate Use Cases

- Parents monitoring minor children (under 18) with consent
- Adults monitoring their own devices (self-monitoring)
- Companies monitoring company-owned devices (employees informed via contract)
- Caregivers monitoring elderly or disabled individuals (with consent)

### ❌ Illegal Use Cases

- Monitoring a spouse/partner without their knowledge
- Monitoring adult children (18+) without consent
- Stalking, harassment, blackmail
- Corporate espionage
- Any monitoring of devices you don't own without explicit written consent

### 📋 Compliance Checklist (Before Deploying)

- [ ] Device owner has provided **written consent** to be monitored
- [ ] Tether Kids is **not hidden** from launcher (icon visible)
- [ ] User is informed about **what data** is being collected
- [ ] User knows how to **stop monitoring** (uninstall app)
- [ ] You have **legal right** to monitor this device in your jurisdiction
- [ ] You are **18+ years old** (or have legal guardian status)

**By using Tether, you acknowledge that misuse is your sole responsibility.**

---

## Requirements

- Android 7.0 (API 24) or higher
- Google Play Services (for Fused Location)
- Internet connection
- Node.js 18+ on server
- 100MB+ storage for SQLite + app data

---

## 📚 Documentation

| File | Description |
|------|-------------|
| [`README.md`](./README.md) | This file — project overview, quick start, legal notice |
| [`PROJECT_STRUCTURE.md`](./PROJECT_STRUCTURE.md) | Visual project structure (server / Tether / TetherKids) |
| [`BUG_REPORT.md`](./BUG_REPORT.md) | 23 bugs found + fixed (4 critical, 6 high, 8 medium, 5 low) |
| [`CARA_GANTI_SERVER_URL.md`](./CARA_GANTI_SERVER_URL.md) | How to change `SERVER_URL` (hardcoded) |
| [`PANDUAN_GANTI_GIF.md`](./PANDUAN_GANTI_GIF.md) | How to download & replace anime MP4s from Pinterest |

---

## Build Status

| Component | Status | Notes |
|-----------|--------|-------|
| Server | ✅ Ready | `npm install && npm start` |
| Tether | ✅ Ready | Needs `Config.SERVER_URL` set |
| TetherKids | ✅ Ready | Needs `Config.SERVER_URL` set |
| Build (Gradle) | ✅ Passes | 4 critical bugs fixed |
| Runtime | ✅ Stable | 7 try-catch in MainActivity, no crashes on offline mode |
| Anime MP4 | ✅ Working | VideoView auto-reactive to socket status |
| Tests | ⚠️ None | No unit tests in repo |

---

## License & Final Notes

Tether is provided **as-is** for educational and legitimate family safety use only. The developers do not condone or support illegal use of this software.

**Use ethically. Use legally. Use responsibly.**

---

**Project**: Tether
**Repository**: https://github.com/JohnIsDimz/joo.exe
**Last updated**: 2026-07-27
