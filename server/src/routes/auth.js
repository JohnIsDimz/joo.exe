const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');
const database = require('../models/database');
const { generateToken, authenticateToken } = require('../middleware/auth');

// Register user (parent or kid)
router.post('/register', async (req, res) => {
  try {
    const { email, password, name, role, familyCode } = req.body;

    if (!email || !password || !name || !role) {
      return res.status(400).json({ error: 'Missing required fields' });
    }

    if (role !== 'parent' && role !== 'kid') {
      return res.status(400).json({ error: 'Role must be parent or kid' });
    }

    const db = database.getDb();

    // Check if email already exists
    const existing = await new Promise((resolve, reject) => {
      db.get('SELECT id FROM users WHERE email = ?', [email], (err, row) => {
        if (err) reject(err);
        resolve(row);
      });
    });

    if (existing) {
      return res.status(409).json({ error: 'Email already registered' });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const userId = uuidv4();
    let familyCodeToUse = familyCode;

    if (role === 'parent') {
      // Create new family
      familyCodeToUse = familyCode || uuidv4().substring(0, 8).toUpperCase();
      const familyName = `${name}'s Family`;

      await new Promise((resolve, reject) => {
        db.run(
          'INSERT INTO families (code, name, parent_id) VALUES (?, ?, ?)',
          [familyCodeToUse, familyName, userId],
          (err) => {
            if (err) {
              // Family code might already exist, generate a new one
              familyCodeToUse = uuidv4().substring(0, 8).toUpperCase();
              db.run(
                'INSERT INTO families (code, name, parent_id) VALUES (?, ?, ?)',
                [familyCodeToUse, familyName, userId],
                (err2) => {
                  if (err2) reject(err2);
                  resolve();
                }
              );
            } else {
              resolve();
            }
          }
        );
      });
    }

    // Create user
    await new Promise((resolve, reject) => {
      db.run(
        'INSERT INTO users (id, email, password, name, role, family_code) VALUES (?, ?, ?, ?, ?, ?)',
        [userId, email, hashedPassword, name, role, familyCodeToUse],
        (err) => {
          if (err) reject(err);
          resolve();
        }
      );
    });

    const token = generateToken({
      id: userId,
      email,
      role,
      family_code: familyCodeToUse
    });

    res.status(201).json({
      token,
      user: {
        id: userId,
        email,
        name,
        role,
        family_code: familyCodeToUse
      }
    });
  } catch (error) {
    console.error('[Auth] Register error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Login
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password required' });
    }

    const db = database.getDb();

    const user = await new Promise((resolve, reject) => {
      db.get('SELECT * FROM users WHERE email = ?', [email], (err, row) => {
        if (err) reject(err);
        resolve(row);
      });
    });

    if (!user) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const validPassword = await bcrypt.compare(password, user.password);
    if (!validPassword) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    // Update last active
    db.run('UPDATE users SET last_active = CURRENT_TIMESTAMP WHERE id = ?', [user.id]);

    const token = generateToken({
      id: user.id,
      email: user.email,
      role: user.role,
      family_code: user.family_code
    });

    res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        name: user.name,
        role: user.role,
        family_code: user.family_code
      }
    });
  } catch (error) {
    console.error('[Auth] Login error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Join family (for kids)
router.post('/join-family', authenticateToken, async (req, res) => {
  try {
    const { familyCode } = req.body;
    const userId = req.user.id;

    if (!familyCode) {
      return res.status(400).json({ error: 'Family code required' });
    }

    const db = database.getDb();

    const family = await new Promise((resolve, reject) => {
      db.get('SELECT * FROM families WHERE code = ?', [familyCode], (err, row) => {
        if (err) reject(err);
        resolve(row);
      });
    });

    if (!family) {
      return res.status(404).json({ error: 'Family not found' });
    }

    await new Promise((resolve, reject) => {
      db.run('UPDATE users SET family_code = ? WHERE id = ?', [familyCode, userId], (err) => {
        if (err) reject(err);
        resolve();
      });
    });

    res.json({ message: 'Joined family successfully', family });
  } catch (error) {
    console.error('[Auth] Join family error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Get profile
router.get('/profile', authenticateToken, async (req, res) => {
  try {
    const db = database.getDb();

    const user = await new Promise((resolve, reject) => {
      db.get(
        'SELECT id, email, name, role, family_code, device_id, created_at, last_active FROM users WHERE id = ?',
        [req.user.id],
        (err, row) => {
          if (err) reject(err);
          resolve(row);
        }
      );
    });

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    res.json({ user });
  } catch (error) {
    console.error('[Auth] Profile error:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
