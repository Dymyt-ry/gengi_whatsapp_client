var express = require('express');
var axios = require('axios');
var cache = require('../cache');
var router = express.Router();

function resolveJid(jid, callback) {
  // Check in-memory mapping first
  var cached = cache.resolveLid(jid);
  if (cached) {
    return callback(null, cached);
  }

  var url = process.env.EVO_API_URL + '/chat/findContact/' + process.env.EVO_INSTANCE_NAME;
  axios.get(url, {
    params: { number: jid },
    headers: { 'apikey': process.env.EVO_API_KEY }
  })
    .then(function (response) {
      var data = response.data;
      // Response may be array or single object
      var contact = Array.isArray(data) ? data[0] : data;
      var resolved = null;
      if (contact && contact.id) {
        // id is typically "5511999999999@s.whatsapp.net"
        resolved = contact.id;
      } else if (contact && contact.remoteJid) {
        resolved = contact.remoteJid;
      }
      if (resolved && resolved.indexOf('@lid') === -1) {
        cache.storeLidMapping(jid, resolved);
        callback(null, resolved);
      } else {
        callback(new Error('Could not resolve @lid to real phone'), null);
      }
    })
    .catch(function (err) {
      callback(err, null);
    });
}

function processMessage(data, rawBody) {
  if (!data || !data.key) return;
  var key = data.key;
  var rawJid = key.remoteJid;
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

  function store(chatId) {
    cache.upsertMessage({
      id: messageId,
      chatId: chatId,
      fromMe: fromMe,
      pushName: pushName,
      text: text,
      timestamp: timestamp
    });
  }

  if (rawJid && rawJid.indexOf('@lid') !== -1) {
    console.log("=== INCOMING LID PAYLOAD DUMP ===");
    console.log(JSON.stringify(rawBody, null, 2));
    console.log("=================================");
    resolveJid(rawJid, function (err, resolved) {
      if (err || !resolved) {
        console.warn('Could not resolve @lid jid:', rawJid, err ? err.message : '');
        store(rawJid); // fall back to @lid as chatId
      } else {
        store(resolved);
      }
    });
  } else {
    store(rawJid);
  }
}

router.post('/', function (req, res) {
  var event = req.body;

  if (event && event.event === 'messages.upsert') {
    processMessage(event.data, event);
  }

  res.status(200).json({ received: true });
});

module.exports = router;
