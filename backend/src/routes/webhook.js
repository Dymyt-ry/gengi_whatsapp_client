var express = require('express');
var cache = require('../cache');
var router = express.Router();

function stripSuffix(jid) {
  if (!jid) return jid;
  return jid.replace(/@s\.whatsapp\.net$/, '').replace(/@lid$/, '');
}

function processMessage(data) {
  if (!data || !data.key) return;
  var key = data.key;
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
  timestamp = timestamp || Math.floor(Date.now() / 1000);

  // v2: prefer *Alt fields which carry real phone numbers instead of @lid pseudonyms
  var rawChatId = key.remoteJidAlt || key.remoteJid;
  var chatId = stripSuffix(rawChatId);

  cache.upsertMessage({
    id: messageId,
    chatId: chatId,
    fromMe: fromMe,
    pushName: pushName,
    text: text,
    timestamp: timestamp
  });
}

router.post('/', function (req, res) {
  var event = req.body;

  if (event && event.event === 'messages.upsert') {
    processMessage(event.data);
  }

  res.status(200).json({ received: true });
});

module.exports = router;
