require('dotenv').config();

var express = require('express');
var cors = require('cors');
var authMiddleware = require('./middleware/auth');
var webhookRouter = require('./routes/webhook');
var chatsRouter = require('./routes/chats');
var chatRouter = require('./routes/chat');
var sendRouter      = require('./routes/send');
var mediaRouter     = require('./routes/media');
var sendMediaRouter = require('./routes/sendMedia');
var reactionRouter  = require('./routes/reaction');

var app = express();
var PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Webhook endpoint is unauthenticated (called by Evolution API)
app.use('/webhook', webhookRouter);

// All other routes require auth
app.use('/chats', authMiddleware, chatsRouter);
app.use('/chat', authMiddleware, chatRouter);
app.use('/send', authMiddleware, sendRouter);
app.use('/api/media',              authMiddleware, mediaRouter);
app.use('/api/messages/sendMedia',  authMiddleware, sendMediaRouter);
app.use('/api/messages/reaction',   authMiddleware, reactionRouter);

app.get('/status', function (req, res) {
  res.json({ status: 'ok' });
});

app.listen(PORT, function () {
  console.log('BBWA backend listening on port ' + PORT);
});

module.exports = app;
