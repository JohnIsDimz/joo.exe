const express = require('express');
const router = express.Router();
const { authenticateToken } = require('../middleware/auth');
const database = require('../models/database');

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
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ members });
  } catch (error) {
    console.error('[API] Family members error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get location history
router.get('/location/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;

    const locations = await new Promise((resolve, reject) => {
      db.all(
        `SELECT * FROM locations 
         WHERE user_id = ? 
         ORDER BY timestamp DESC 
         LIMIT ?`,
        [userId, parseInt(limit)],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ locations });
  } catch (error) {
    console.error('[API] Location error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get latest location for user
router.get('/location/:userId/latest', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;

    const location = await new Promise((resolve, reject) => {
      db.get(
        'SELECT * FROM locations WHERE user_id = ? ORDER BY timestamp DESC LIMIT 1',
        [userId],
        (err, row) => {
          if (err) reject(err);
          resolve(row);
        }
      );
    });

    res.json({ location });
  } catch (error) {
    console.error('[API] Latest location error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get SOS alerts for family
router.get('/sos', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();

    const alerts = await new Promise((resolve, reject) => {
      db.all(
        `SELECT s.*, u.name as user_name 
         FROM sos_alerts s 
         JOIN users u ON s.user_id = u.id 
         WHERE u.family_code = ? 
         ORDER BY s.created_at DESC 
         LIMIT 20`,
        [req.user.family_code],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ alerts });
  } catch (error) {
    console.error('[API] SOS alerts error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Resolve SOS alert
router.post('/sos/:alertId/resolve', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { alertId } = req.params;

    await new Promise((resolve, reject) => {
      db.run(
        'UPDATE sos_alerts SET status = ?, resolved_at = CURRENT_TIMESTAMP WHERE id = ?',
        ['resolved', alertId],
        (err) => {
          if (err) reject(err);
          resolve();
        }
      );
    });

    res.json({ message: 'Alert resolved' });
  } catch (error) {
    console.error('[API] Resolve alert error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get app usage for user
router.get('/app-usage/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { date } = req.query;

    let query = `SELECT * FROM app_usage WHERE user_id = ?`;
    const params = [userId];

    if (date) {
      query += ` AND DATE(timestamp) = DATE(?)`;
      params.push(date);
    }

    query += ` ORDER BY timestamp DESC LIMIT 100`;

    const usage = await new Promise((resolve, reject) => {
      db.all(query, params, (err, rows) => {
        if (err) reject(err);
        resolve(rows);
      });
    });

    res.json({ usage });
  } catch (error) {
    console.error('[API] App usage error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Set screen time limit
router.post('/screen-time', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { kidId, dailyLimitMinutes } = req.body;

    if (!kidId || !dailyLimitMinutes) {
      return res.status(400).json({ error: 'Kid ID and daily limit required' });
    }

    // Upsert screen time limit
    const existing = await new Promise((resolve, reject) => {
      db.get(
        'SELECT id FROM screen_time_limits WHERE family_code = ? AND kid_id = ?',
        [req.user.family_code, kidId],
        (err, row) => {
          if (err) reject(err);
          resolve(row);
        }
      );
    });

    if (existing) {
      await new Promise((resolve, reject) => {
        db.run(
          'UPDATE screen_time_limits SET daily_limit_minutes = ? WHERE id = ?',
          [dailyLimitMinutes, existing.id],
          (err) => {
            if (err) reject(err);
            resolve();
          }
        );
      });
    } else {
      const { v4: uuidv4 } = require('uuid');
      await new Promise((resolve, reject) => {
        db.run(
          'INSERT INTO screen_time_limits (id, family_code, kid_id, daily_limit_minutes) VALUES (?, ?, ?, ?)',
          [uuidv4(), req.user.family_code, kidId, dailyLimitMinutes],
          (err) => {
            if (err) reject(err);
            resolve();
          }
        );
      });
    }

    res.json({ message: 'Screen time limit set successfully' });
  } catch (error) {
    console.error('[API] Screen time error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get screen time limits
router.get('/screen-time', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();

    const limits = await new Promise((resolve, reject) => {
      db.all(
        `SELECT s.*, u.name as kid_name 
         FROM screen_time_limits s 
         JOIN users u ON s.kid_id = u.id 
         WHERE s.family_code = ?`,
        [req.user.family_code],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ limits });
  } catch (error) {
    console.error('[API] Screen time limits error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get check-ins
router.get('/checkins', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();

    const checkins = await new Promise((resolve, reject) => {
      db.all(
        `SELECT c.*, u.name as user_name 
         FROM checkins c 
         JOIN users u ON c.user_id = u.id 
         WHERE u.family_code = ? 
         ORDER BY c.timestamp DESC 
         LIMIT 20`,
        [req.user.family_code],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ checkins });
  } catch (error) {
    console.error('[API] Checkins error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Create geofence
router.post('/geofence', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { name, latitude, longitude, radius } = req.body;
    const { v4: uuidv4 } = require('uuid');

    if (!name || !latitude || !longitude) {
      return res.status(400).json({ error: 'Name, latitude, and longitude required' });
    }

    const geofenceId = uuidv4();
    await new Promise((resolve, reject) => {
      db.run(
        'INSERT INTO geofences (id, family_code, name, latitude, longitude, radius) VALUES (?, ?, ?, ?, ?, ?)',
        [geofenceId, req.user.family_code, name, latitude, longitude, radius || 100],
        (err) => {
          if (err) reject(err);
          resolve();
        }
      );
    });

    res.status(201).json({ geofence: { id: geofenceId, name, latitude, longitude, radius: radius || 100 } });
  } catch (error) {
    console.error('[API] Geofence error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get geofences
router.get('/geofence', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();

    const geofences = await new Promise((resolve, reject) => {
      db.all(
        'SELECT * FROM geofences WHERE family_code = ?',
        [req.user.family_code],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ geofences });
  } catch (error) {
    console.error('[API] Geofences error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get notifications
router.get('/notifications', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();

    const notifications = await new Promise((resolve, reject) => {
      db.all(
        'SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50',
        [req.user.id],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ notifications });
  } catch (error) {
    console.error('[API] Notifications error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Mark notification as read
router.post('/notifications/:id/read', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { id } = req.params;

    await new Promise((resolve, reject) => {
      db.run('UPDATE notifications SET read = 1 WHERE id = ? AND user_id = ?', [id, req.user.id], (err) => {
        if (err) reject(err);
        resolve();
      });
    });

    res.json({ message: 'Notification marked as read' });
  } catch (error) {
    console.error('[API] Mark read error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get SMS/chat logs for a specific user
router.get('/sms/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;

    const logs = await new Promise((resolve, reject) => {
      db.all(
        `SELECT * FROM sms_logs 
         WHERE user_id = ? 
         ORDER BY timestamp DESC 
         LIMIT ?`,
        [userId, parseInt(limit)],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ logs });
  } catch (error) {
    console.error('[API] SMS logs error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get notification events for a specific user
router.get('/notifications-events/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;
    const { limit = 50 } = req.query;

    const events = await new Promise((resolve, reject) => {
      db.all(
        `SELECT * FROM notification_events 
         WHERE user_id = ? 
         ORDER BY posted_at DESC 
         LIMIT ?`,
        [userId, parseInt(limit)],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ events });
  } catch (error) {
    console.error('[API] Notification events error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get voice sessions for a specific user
router.get('/voice-sessions/:userId', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();
    const { userId } = req.params;

    const sessions = await new Promise((resolve, reject) => {
      db.all(
        'SELECT * FROM voice_sessions WHERE user_id = ? ORDER BY started_at DESC LIMIT 10',
        [userId],
        (err, rows) => {
          if (err) reject(err);
          resolve(rows);
        }
      );
    });

    res.json({ sessions });
  } catch (error) {
    console.error('[API] Voice sessions error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
