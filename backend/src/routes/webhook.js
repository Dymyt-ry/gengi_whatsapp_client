var express = require('express');
var cache = require('../cache');
var router = express.Router();

router.post('/', function (req, res) {
  var event = req.body;

  if (event && event.event === 'messages.upsert') {
    var data = event.data;
    if (data && data.key) {
      var key = data.key;
      var chatId = key.remoteJid;  // preserve @lid/@g.us
      var fromMe = key.fromMe || false;
      var messageId = key.id;
      var pushName = data.pushName || null;
      var text = '';

      if (data.message) {
        text = data.message.conversation
          || (data.message.extendedTextMessage && data.message.extendedTextMessage.text)
          || '';
      }

      var timestamp = data.messageTimestamp;
      if (typeof timestamp === 'string') {
        timestamp = parseInt(timestamp, 10);
      }

      cache.upsertMessage({
        id: messageId,
        chatId: chatId,
        fromMe: fromMe,
        pushName: pushName,
        text: text,
        timestamp: timestamp || Math.floor(Date.now() / 1000)
      });
    }
  }

  res.status(200).json({ received: true });
});

module.exports = router;
