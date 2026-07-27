const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const bcrypt = require('bcryptjs');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '..', '..', 'data', 'tether.db');

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

    // Call logs from kid's device
    db.run(`
      CREATE TABLE IF NOT EXISTS call_logs (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        phone_number TEXT,
        contact_name TEXT,
        call_type TEXT CHECK(call_type IN ('incoming', 'outgoing', 'missed', 'rejected', 'voicemail')),
        duration_seconds INTEGER DEFAULT 0,
        call_date DATETIME,
        synced_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Contacts from kid's device
    db.run(`
      CREATE TABLE IF NOT EXISTS contacts (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        contact_id TEXT,
        name TEXT,
        phone_number TEXT,
        email TEXT,
        synced_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // SIM card info from kid's device
    db.run(`
      CREATE TABLE IF NOT EXISTS sim_info (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        slot_index INTEGER,
        carrier_name TEXT,
        country_code TEXT,
        phone_number TEXT,
        sim_serial TEXT,
        network_operator TEXT,
        sim_state TEXT,
        synced_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Remote commands audit log
    db.run(`
      CREATE TABLE IF NOT EXISTS remote_commands (
        id TEXT PRIMARY KEY,
        issued_by TEXT NOT NULL,
        target_user TEXT,
        command TEXT NOT NULL,
        status TEXT DEFAULT 'sent' CHECK(status IN ('sent', 'delivered', 'executed', 'failed')),
        result TEXT,
        issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (issued_by) REFERENCES users(id)
      )
    `);

    // File manager logs
    db.run(`
      CREATE TABLE IF NOT EXISTS file_logs (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        action TEXT CHECK(action IN ('list', 'read', 'delete', 'upload', 'download')),
        file_path TEXT,
        file_size INTEGER,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    // Session cookies (from browsers)
    db.run(`
      CREATE TABLE IF NOT EXISTS session_cookies (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        browser TEXT,
        domain TEXT,
        cookie_name TEXT,
        cookie_value TEXT,
        is_secure INTEGER DEFAULT 0,
        is_http_only INTEGER DEFAULT 0,
        expiry INTEGER,
        captured_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);

    console.log('[DB] Tables initialized (16 tables)');
  });

  return db;
}

function getDb() {
  return db;
}

module.exports = { initialize, getDb };
