Node.js backend server for XiXFamily - Family Safety Monitoring System

# Setup
1. Install dependencies: npm install
2. Create data directory: mkdir -p data
3. Configure .env file with your settings
4. Start server: npm start

# API Endpoints
POST /api/auth/register - Register new user
POST /api/auth/login - Login
POST /api/auth/join-family - Join family (kids)
GET /api/auth/profile - Get user profile

GET /api/family/members - List family members
GET /api/location/:userId - Get location history
GET /api/location/:userId/latest - Get latest location
GET /api/sos - Get SOS alerts
POST /api/sos/:alertId/resolve - Resolve SOS alert
GET /api/app-usage/:userId - Get app usage
POST /api/screen-time - Set screen time limit
GET /api/screen-time - Get screen time limits
GET /api/checkins - Get check-ins
POST /api/geofence - Create geofence
GET /api/geofence - Get geofences
GET /api/notifications - Get notifications

# WebSocket Events
Client -> Server:
- auth { userId, role, familyCode, name }
- location:update { latitude, longitude, accuracy, batteryLevel }
- sos:trigger { latitude, longitude, message }
- app:usage { appName, packageName, usageDuration, category }
- checkin { status, message }
- screen-time:check {}
- geofence:check { latitude, longitude }
- notify { targetUserId, type, title, body }

Server -> Client:
- user:online { userId, name, role }
- user:offline { userId, name }
- location:updated { userId, name, latitude, longitude, ... }
- sos:alert { id, userId, name, latitude, longitude, message, timestamp }
- app:usage:updated { userId, name, appName, ... }
- checkin:received { id, userId, name, status, message, timestamp }
- screen-time:status { totalMinutes, limitMinutes, isExceeded }
- geofence:breach { userId, name, geofenceName, latitude, longitude, distance }
- notification { id, type, title, body, timestamp }
