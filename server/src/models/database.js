const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const bcrypt = require('bcryptjs');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '..', '..', 'data', 'xixfamily.db');

let db;

function initialize() {
  db = new sqlite3.Database(DB_PATH, (err) => {
    if (err) {
      console.error('[DB] Error opening database:', err.message);
      process.exit(1);
    }
    console.log('[DB] Connected to SQLite database');
  });

  db.serialize(() => {
    // Enable WAL mode for better performance
    db.run('PRAGMA journal_mode=WAL');
    db.run('PRAGMA foreign_keys=ON');

    // Users table (both parents and kids)
    db.run(`
      CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        email TEXT UNIQUE NOT NULL,
        password TEXT NOT NULL,
        name TEXT NOT NULL,
        role TEXT NOT NULL CHECK(role IN ('parent', 'kid')),
        family_code TEXT NOT NULL,
        device_id TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        last_active DATETIME
      )
    `);

    // Family groups
    db.run(`
      CREATE TABLE IF NOT EXISTS families (
        code TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        parent_id TEXT NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (parent_id) REFERENCES users(id)
      )
    `);

    // Location history
    db.run(`
      CREATE TABLE IF NOT EXISTS locations (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        accuracy REAL,
        battery_level REAL,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // App usage records
    db.run(`
      CREATE TABLE IF NOT EXISTS app_usage (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        app_name TEXT NOT NULL,
        package_name TEXT NOT NULL,
        usage_duration INTEGER DEFAULT 0,
        category TEXT,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // SOS alerts
    db.run(`
      CREATE TABLE IF NOT EXISTS sos_alerts (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        latitude REAL,
        longitude REAL,
        message TEXT,
        status TEXT DEFAULT 'active' CHECK(status IN ('active', 'resolved')),
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        resolved_at DATETIME,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Check-ins
    db.run(`
      CREATE TABLE IF NOT EXISTS checkins (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        status TEXT DEFAULT 'ok' CHECK(status IN ('ok', 'safe', 'help')),
        message TEXT,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Geofence zones
    db.run(`
      CREATE TABLE IF NOT EXISTS geofences (
        id TEXT PRIMARY KEY,
        family_code TEXT NOT NULL,
        name TEXT NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        radius INTEGER NOT NULL DEFAULT 100,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (family_code) REFERENCES families(code)
      )
    `);

    // Screen time limits
    db.run(`
      CREATE TABLE IF NOT EXISTS screen_time_limits (
        id TEXT PRIMARY KEY,
        family_code TEXT NOT NULL,
        kid_id TEXT NOT NULL,
        daily_limit_minutes INTEGER DEFAULT 120,
        FOREIGN KEY (family_code) REFERENCES families(code),
        FOREIGN KEY (kid_id) REFERENCES users(id)
      )
    `);

    // Notifications log
    db.run(`
      CREATE TABLE IF NOT EXISTS notifications (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        type TEXT NOT NULL,
        title TEXT,
        body TEXT,
        app_name TEXT,
        read INTEGER DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // SMS / Chat logs from kid's device
    db.run(`
      CREATE TABLE IF NOT EXISTS sms_logs (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        sender TEXT,
        message TEXT,
        app_name TEXT DEFAULT 'SMS',
        type TEXT DEFAULT 'inbox' CHECK(type IN ('inbox', 'sent')),
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Voice monitoring sessions
    db.run(`
      CREATE TABLE IF NOT EXISTS voice_sessions (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        requested_by TEXT,
        status TEXT DEFAULT 'active' CHECK(status IN ('active', 'stopped')),
        started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        stopped_at DATETIME,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Notification events (captured from NotificationListenerService)
    db.run(`
      CREATE TABLE IF NOT EXISTS notification_events (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        app_name TEXT,
        title TEXT,
        text_content TEXT,
        package_name TEXT,
        posted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // App blocklist (blocked apps for kids)
    db.run(`
      CREATE TABLE IF NOT EXISTS app_blocklist (
        id TEXT PRIMARY KEY,
        family_code TEXT NOT NULL,
        kid_id TEXT NOT NULL,
        app_name TEXT NOT NULL,
        package_name TEXT NOT NULL,
        blocked_by TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (kid_id) REFERENCES users(id)
      )
    `);

    // WiFi/network logs from kid's device
    db.run(`
      CREATE TABLE IF NOT EXISTS network_logs (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        ssid TEXT,
        bssid TEXT,
        signal_strength INTEGER,
        ip_address TEXT,
        is_connected INTEGER DEFAULT 1,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Media files (gallery list from kid's device)
    db.run(`
      CREATE TABLE IF NOT EXISTS media_cache (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        file_name TEXT NOT NULL,
        file_path TEXT,
        file_size INTEGER,
        mime_type TEXT,
        media_type TEXT DEFAULT 'image' CHECK(media_type IN ('image', 'video')),
        captured_at TEXT,
        synced_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    console.log('[DB] Tables initialized');
  });

  return db;
}

function getDb() {
  return db;
}

module.exports = { initialize, getDb };
