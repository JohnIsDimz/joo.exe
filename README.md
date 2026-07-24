# XiXFamily - Family Safety Monitoring System

> **Neobrutalism Minimalism** — Square corners, strong black borders, flat color blocking, bold typography.

A complete family safety monitoring system with two Android apps and a Node.js backend server.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    VPS (Pterodactyl)                     │
│  ┌──────────────────────────────────────────────────┐   │
│  │            Node.js Server (:3000)                 │   │
│  │  ├── Express REST API (auth, data endpoints)      │   │
│  │  ├── Socket.IO WebSocket (real-time events)       │   │
│  │  └── SQLite Database (users, locations, SOS, etc) │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ┌──────────┐  ┌──────────┐  ┌──────────┐
   │  Parents  │  │   Kids   │  │   More   │
   │ XiXFamily │  │KidsFamily│  │  Family  │
   │   App     │  │   App    │  │ Members  │
   └──────────┘  └──────────┘  └──────────┘
```

### Components

| Component | Technology | Description |
|-----------|-----------|-------------|
| **XiXFamily** | Kotlin + Java | Parent monitoring app - dashboard, locations, SOS alerts, app usage |
| **KidsFamily** | Kotlin + Java | Kid's safety app - SOS button, check-in, location sharing |
| **Server** | Node.js + Socket.IO | REST API + WebSocket real-time communication, SQLite storage |

### Features

- **Real-time Location Tracking** - Kids share live location with parents via WebSocket
- **SOS Emergency Alerts** - One-tap SOS with notification to all family members
- **Check-in System** - Kids check in with status (OK / Safe / Need Help)
- **App Usage Monitoring** - Parents see which apps kids are using
- **Screen Time Management** - Set and monitor daily screen time limits
- **Geofencing** - Create safe zones and get alerts when kids leave
- **Family Management** - Invite members with family code
- **Foreground Service** - Persistent connection and location tracking
- **Boot Recovery** - Auto-restart services after device reboot

---

## Quick Start

### 1. Deploy the Server (VPS via Pterodactyl)

```bash
# Upload to your VPS
cd xixfamily/server

# Install dependencies
npm install

# Create data directory
mkdir -p data

# Start server
npm start
```

The server runs on port **3000** by default. Configure in `.env`.

> ⚠️ **Important**: Set a strong `JWT_SECRET` in production!

### 2. Build XiXFamily (Parent App)

Using **Code on the Go** on your Android device:

1. Open Code on the Go app
2. Create new project → Import from folder
3. Select `xixfamily/XiXFamily/` directory
4. Build & Run → Install APK

Or using Android Studio on desktop:
1. Open `xixfamily/XiXFamily/` in Android Studio
2. Sync Gradle
3. Run on device/emulator

### 3. Build KidsFamily (Kid's App)

Same process:
1. Open `xixfamily/KidsFamily/` in Code on the Go or Android Studio
2. Build & Run

### 4. First Use

1. **Parent**: Open XiXFamily → Enter Server URL → Register as Parent → Share Family Code
2. **Kid**: Open KidsFamily → Enter Server URL → Register with Family Code from parent
3. Both apps connect to the server via WebSocket automatically

---

## Server Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | 3000 | Server port |
| `JWT_SECRET` | (change me!) | Secret key for JWT tokens |
| `DB_PATH` | ./data/xixfamily.db | SQLite database path |

### Deploy on Pterodactyl

1. Create a new Node.js egg/server on your Pterodactyl panel
2. Set startup command: `npm start`
3. Upload files to the server directory
4. Configure environment variables in Pterodactyl settings
5. Start the server

### API Endpoints

See `xixfamily/server/README.md` for complete API documentation.

### WebSocket Events

Real-time events via Socket.IO:

| Event | Direction | Description |
|-------|-----------|-------------|
| `auth` | Client→Server | Authenticate WebSocket connection |
| `location:update` | Client→Server | Send location data |
| `location:updated` | Server→Client | Broadcast location to family |
| `sos:trigger` | Client→Server | Trigger SOS alert |
| `sos:alert` | Server→Client | SOS alert notification |
| `app:usage` | Client→Server | Report app usage |
| `checkin` | Client→Server | Send check-in |
| `checkin:received` | Server→Client | Broadcast check-in |
| `user:online` | Server→Client | User came online |
| `user:offline` | Server→Client | User went offline |

---

## Design System: Neobrutalism Minimalism

### Principles Applied

| Principle | Implementation |
|-----------|---------------|
| **Square Corners** | All elements use `android:radius="0dp"` |
| **Strong Black Borders** | 2-4dp black strokes on all interactive elements |
| **Flat Color Blocking** | Solid fills, no gradients |
| **Offset Shadows** | Layer-list drawables with 4dp black offset |
| **Bold Typography** | All-caps labels, heavy letter-spacing, bold weights |
| **High Contrast** | Black text on white/bright backgrounds |
| **Minimalist Structure** | Generous whitespace, clear hierarchy |

### Color Palette

```
Primary Blue:    #1A73E8
Alert Red:       #E53935
Success Green:   #0F9D58
Warning Yellow:  #F9AB00
Background:      #FFFBF0 (warm off-white)
Text:            #000000
```

---

## Project Structure

```
xixfamily/
├── server/                    # Node.js backend
│   ├── src/
│   │   ├── index.js          # Entry point
│   │   ├── websocket.js      # Socket.IO handlers
│   │   ├── routes/
│   │   │   ├── auth.js       # Auth endpoints
│   │   │   └── api.js        # Data API endpoints
│   │   ├── middleware/
│   │   │   └── auth.js       # JWT middleware
│   │   └── models/
│   │       └── database.js   # SQLite setup & queries
│   ├── package.json
│   └── README.md
│
├── XiXFamily/                 # Parent Android App
│   └── app/src/main/
│       ├── java/com/xixfamily/parent/
│       │   ├── data/          # Models
│       │   ├── network/       # Socket, API client
│       │   ├── service/       # WebSocket service, BootReceiver
│       │   ├── utils/         # Prefs, Date utils
│       │   └── ui/
│       │       ├── auth/      # Splash, Auth activities
│       │       ├── dashboard/ # Main activity
│       │       ├── monitoring/ # Fragments (Dashboard, Locations, SOS, Usage)
│       │       └── settings/  # Settings fragment
│       └── res/
│           ├── drawable/      # Neobrutalism button/card/input/badge drawables
│           ├── layout/        # All activity & fragment layouts
│           ├── menu/          # Bottom nav menu
│           └── values/        # Colors, themes, strings, dimensions
│
└── KidsFamily/                # Kids Android App
    └── app/src/main/
        ├── java/com/xixfamily/kids/
        │   ├── data/          # Models
        │   ├── network/       # Socket, API client
        │   ├── service/       # Location service, BootReceiver
        │   ├── utils/         # Prefs
        │   └── ui/
        │       ├── auth/      # Auth activity
        │       └── main/      # Main activity with SOS, check-in
        └── res/
            ├── drawable/      # Kid-friendly neobrutalism drawables
            ├── layout/        # Activity layouts
            └── values/        # Colors, themes, strings
```

---

## Security & Privacy

- **Transparency**: KidsFamily shows a persistent notification when location sharing is active
- **Consent**: Both apps require explicit user permissions and login
- **Encryption**: JWT-based authentication for all API calls
- **Data Isolation**: Each family's data is isolated by family code
- **No Stealth**: This is NOT spyware — it's a transparent family safety tool

## Requirements

- Android 7.0 (API 24) or higher
- Google Play Services (for location)
- Internet connection
- Node.js 18+ on server

## License

XiXFamily is a family safety tool. Use responsibly and with full consent of all family members.
