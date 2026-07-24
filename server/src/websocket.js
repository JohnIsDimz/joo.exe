const { v4: uuidv4 } = require('uuid');
const database = require('./models/database');

// Track online users
const onlineUsers = new Map(); // socketId -> { userId, role, familyCode, name }

function initialize(io) {
  io.on('connection', (socket) => {
    console.log(`[WS] New connection: ${socket.id}`);

    // Authenticate user via WebSocket
    socket.on('auth', (data) => {
      const { userId, role, familyCode, name } = data;
      
      onlineUsers.set(socket.id, { userId, role, familyCode, name, socketId: socket.id });
      
      // Join family room
      socket.join(`family:${familyCode}`);
      
      // Join user's personal room
      socket.join(`user:${userId}`);
      
      // Notify family members
      io.to(`family:${familyCode}`).emit('user:online', {
        userId,
        name,
        role
      });

      // Update last active
      const db = database.getDb();
      db.run('UPDATE users SET last_active = CURRENT_TIMESTAMP WHERE id = ?', [userId]);

      console.log(`[WS] ${name} (${role}) authenticated in family ${familyCode}`);
    });

    // Location update from kid
    socket.on('location:update', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user) return;

      const { latitude, longitude, accuracy, batteryLevel } = data;
      const db = database.getDb();
      const locationId = uuidv4();

      db.run(
        'INSERT INTO locations (id, user_id, latitude, longitude, accuracy, battery_level) VALUES (?, ?, ?, ?, ?, ?)',
        [locationId, user.userId, latitude, longitude, accuracy || 0, batteryLevel || 0]
      );

      // Send to parents in the family
      io.to(`family:${user.familyCode}`).emit('location:updated', {
        userId: user.userId,
        name: user.name,
        latitude,
        longitude,
        accuracy,
        batteryLevel,
        timestamp: new Date().toISOString()
      });
    });

    // Flashlight control
    socket.on('flashlight:on', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data;
      io.to('user:' + targetUserId).emit('flashlight:on', { requestedBy: user.userId, parentName: user.name });
      console.log('[WS] Flashlight ON by ' + user.name + ' on ' + targetUserId);
    });

    socket.on('flashlight:off', (data) => {
      const user = onlineUsers.get(socket.id);
      if (!user || user.role !== 'parent') return;
      const { targetUserId } = data;
      io.to('user:' + targetUserId).emit('flashlight:off', { requestedBy: user.userId });
    });
