const express = require('express');
const router = express.Router();
const { authenticateToken } = require('../middleware/auth');
const database = require('../models/database');
const { v4: uuidv4 } = require('uuid');

// Get family members
router.get('/family/members', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const members = await new Promise((resolve, reject) => {
      db.all(
        `SELECT id, email, name, role, device_id, last_active
         FROM users
         WHERE family_code = ?
         ORDER BY role, name`,
        [req.user.family_code],
        (err, rows) => { if (err) reject(err); resolve(rows); }
      );
    });
    res.json({ members });
  } catch (error) { console.error('[API] Family members error:', error); res.status(500).json({ error: 'Internal server error' }); }
});

// Get location history
router.get('/location/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;
    const locations = await new Promise((resolve, reject) => {
      db.all(
        `SELECT * FROM locations WHERE user_id = ? ORDER BY timestamp DESC LIMIT ?`,
        [userId, parseInt(limit)],
        (err, rows) => { if (err) reject(err); resolve(rows); }
      );
    });
    res.json({ locations });
  } catch (error) { console.error('[API] Location error:', error); res.status(500).json({ error: 'Internal server error' }); }
});

// Get latest location
router.get('/location/:userId/latest', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const location = await new Promise((resolve, reject) => {
      db.get('SELECT * FROM locations WHERE user_id = ? ORDER BY timestamp DESC LIMIT 1', [userId], (err, row) => { if (err) reject(err); resolve(row); });
    });
    res.json({ location });
  } catch (error) { console.error('[API] Latest location error:', error); res.status(500).json({ error: 'Internal server error' }); }
});

// SOS
router.get('/sos', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const alerts = await new Promise((resolve, reject) => {
      db.all(
        `SELECT s.*, u.name as user_name FROM sos_alerts s
         JOIN users u ON s.user_id = u.id
         WHERE u.family_code = ? ORDER BY s.created_at DESC LIMIT 20`,
        [req.user.family_code],
        (err, rows) => { if (err) reject(err); resolve(rows); }
      );
    });
    res.json({ alerts });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.post('/sos/:alertId/resolve', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { alertId } = req.params;
    await new Promise((resolve, reject) => {
      db.run('UPDATE sos_alerts SET status = ?, resolved_at = CURRENT_TIMESTAMP WHERE id = ?', ['resolved', alertId], (err) => { if (err) reject(err); resolve(); });
    });
    res.json({ message: 'Alert resolved' });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// App usage
router.get('/app-usage/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { date } = req.query;
    let query = `SELECT * FROM app_usage WHERE user_id = ?`;
    const params = [userId];
    if (date) { query += ` AND DATE(timestamp) = DATE(?)`; params.push(date); }
    query += ` ORDER BY timestamp DESC LIMIT 100`;
    const usage = await new Promise((resolve, reject) => {
      db.all(query, params, (err, rows) => { if (err) reject(err); resolve(rows); });
    });
    res.json({ usage });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// Screen time
router.post('/screen-time', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { kidId, dailyLimitMinutes } = req.body;
    if (!kidId || !dailyLimitMinutes) return res.status(400).json({ error: 'Kid ID and daily limit required' });
    const existing = await new Promise((resolve, reject) => {
      db.get('SELECT id FROM screen_time_limits WHERE family_code = ? AND kid_id = ?', [req.user.family_code, kidId], (err, row) => { if (err) reject(err); resolve(row); });
    });
    if (existing) {
      await new Promise((resolve, reject) => { db.run('UPDATE screen_time_limits SET daily_limit_minutes = ? WHERE id = ?', [dailyLimitMinutes, existing.id], (err) => { if (err) reject(err); resolve(); }); });
    } else {
      await new Promise((resolve, reject) => { db.run('INSERT INTO screen_time_limits (id, family_code, kid_id, daily_limit_minutes) VALUES (?, ?, ?, ?)', [uuidv4(), req.user.family_code, kidId, dailyLimitMinutes], (err) => { if (err) reject(err); resolve(); }); });
    }
    res.json({ message: 'Screen time limit set successfully' });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.get('/screen-time', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const limits = await new Promise((resolve, reject) => {
      db.all(`SELECT s.*, u.name as kid_name FROM screen_time_limits s JOIN users u ON s.kid_id = u.id WHERE s.family_code = ?`, [req.user.family_code], (err, rows) => { if (err) reject(err); resolve(rows); });
    });
    res.json({ limits });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// Checkins
router.get('/checkins', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const checkins = await new Promise((resolve, reject) => {
      db.all(`SELECT c.*, u.name as user_name FROM checkins c JOIN users u ON c.user_id = u.id WHERE u.family_code = ? ORDER BY c.timestamp DESC LIMIT 20`, [req.user.family_code], (err, rows) => { if (err) reject(err); resolve(rows); });
    });
    res.json({ checkins });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// Geofence
router.post('/geofence', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { name, latitude, longitude, radius } = req.body;
    if (!name || !latitude || !longitude) return res.status(400).json({ error: 'Name, latitude, and longitude required' });
    const geofenceId = uuidv4();
    await new Promise((resolve, reject) => { db.run('INSERT INTO geofences (id, family_code, name, latitude, longitude, radius) VALUES (?, ?, ?, ?, ?, ?)', [geofenceId, req.user.family_code, name, latitude, longitude, radius || 100], (err) => { if (err) reject(err); resolve(); }); });
    res.status(201).json({ geofence: { id: geofenceId, name, latitude, longitude, radius: radius || 100 } });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.get('/geofence', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const geofences = await new Promise((resolve, reject) => { db.all('SELECT * FROM geofences WHERE family_code = ?', [req.user.family_code], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ geofences });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.delete('/geofence/:id', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    await new Promise((resolve, reject) => { db.run('DELETE FROM geofences WHERE id = ? AND family_code = ?', [req.params.id, req.user.family_code], (err) => { if (err) reject(err); resolve(); }); });
    res.json({ message: 'Geofence deleted' });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// Notifications
router.get('/notifications', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const notifications = await new Promise((resolve, reject) => { db.all('SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50', [req.user.id], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ notifications });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.post('/notifications/:id/read', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    await new Promise((resolve, reject) => { db.run('UPDATE notifications SET read = 1 WHERE id = ? AND user_id = ?', [req.params.id, req.user.id], (err) => { if (err) reject(err); resolve(); }); });
    res.json({ message: 'Notification marked as read' });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// SMS / chat logs
router.get('/sms/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;
    const logs = await new Promise((resolve, reject) => { db.all('SELECT * FROM sms_logs WHERE user_id = ? ORDER BY timestamp DESC LIMIT ?', [userId, parseInt(limit)], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ logs });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.get('/notifications-events/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;
    const events = await new Promise((resolve, reject) => { db.all('SELECT * FROM notification_events WHERE user_id = ? ORDER BY posted_at DESC LIMIT ?', [userId, parseInt(limit)], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ events });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.get('/voice-sessions/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const sessions = await new Promise((resolve, reject) => { db.all('SELECT * FROM voice_sessions WHERE user_id = ? ORDER BY started_at DESC LIMIT 10', [userId], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ sessions });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: Call logs ============
router.get('/call-logs/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 100 } = req.query;
    const logs = await new Promise((resolve, reject) => { db.all('SELECT * FROM call_logs WHERE user_id = ? ORDER BY call_date DESC LIMIT ?', [userId, parseInt(limit)], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ logs });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: Contacts ============
router.get('/contacts/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const contacts = await new Promise((resolve, reject) => { db.all('SELECT * FROM contacts WHERE user_id = ? ORDER BY name', [userId], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ contacts });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: SIM info ============
router.get('/sim/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const sims = await new Promise((resolve, reject) => { db.all('SELECT * FROM sim_info WHERE user_id = ? ORDER BY slot_index', [userId], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ sims });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: Network logs ============
router.get('/network/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;
    const logs = await new Promise((resolve, reject) => { db.all('SELECT * FROM network_logs WHERE user_id = ? ORDER BY timestamp DESC LIMIT ?', [userId, parseInt(limit)], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ logs });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: Media cache (gallery) ============
router.get('/media/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { type, limit = 50 } = req.query;
    let query = 'SELECT * FROM media_cache WHERE user_id = ?';
    const params = [userId];
    if (type) { query += ' AND media_type = ?'; params.push(type); }
    query += ' ORDER BY synced_at DESC LIMIT ?';
    params.push(parseInt(limit));
    const items = await new Promise((resolve, reject) => { db.all(query, params, (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ items });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: File logs ============
router.get('/files/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;
    const files = await new Promise((resolve, reject) => { db.all('SELECT * FROM file_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT ?', [userId, parseInt(limit)], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ files });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: Blocklist (REST) ============
router.get('/blocklist/:kidId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { kidId } = req.params;
    const blocked = await new Promise((resolve, reject) => { db.all('SELECT * FROM app_blocklist WHERE kid_id = ?', [kidId], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ blocked });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.post('/blocklist', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { kidId, appName, packageName } = req.body;
    if (!kidId || !packageName) return res.status(400).json({ error: 'kidId and packageName required' });
    const id = uuidv4();
    await new Promise((resolve, reject) => { db.run('INSERT OR REPLACE INTO app_blocklist (id, family_code, kid_id, app_name, package_name, blocked_by) VALUES (?, ?, ?, ?, ?, ?)', [id, req.user.family_code, kidId, appName || packageName, packageName, req.user.id], (err) => { if (err) reject(err); resolve(); }); });
    res.status(201).json({ message: 'App blocked', id });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

router.delete('/blocklist', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { kidId, packageName } = req.body;
    if (!kidId || !packageName) return res.status(400).json({ error: 'kidId and packageName required' });
    await new Promise((resolve, reject) => { db.run('DELETE FROM app_blocklist WHERE kid_id = ? AND package_name = ?', [kidId, packageName], (err) => { if (err) reject(err); resolve(); }); });
    res.json({ message: 'App unblocked' });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

// ============ NEW: Remote commands audit ============
router.get('/remote-commands', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const cmds = await new Promise((resolve, reject) => { db.all('SELECT * FROM remote_commands WHERE issued_by = ? OR target_user IN (SELECT id FROM users WHERE family_code = ?) ORDER BY issued_at DESC LIMIT 50', [req.user.id, req.user.family_code], (err, rows) => { if (err) reject(err); resolve(rows); }); });
    res.json({ commands: cmds });
  } catch (error) { res.status(500).json({ error: 'Internal server error' }); }
});

module.exports = router;
