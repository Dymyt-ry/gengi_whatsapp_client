var express = require('express');
var cache = require('../cache');
var axios = require('axios');
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

  // Detect reaction before text extraction
  if (data.message && data.message.reactionMessage) {
    var rm = data.message.reactionMessage;
    var targetId = rm.key && rm.key.id;
    var emoji = rm.text || '';
    var rawJid = key.remoteJidAlt || key.remoteJid;
    if (targetId) cache.addReaction(stripSuffix(rawJid), targetId, emoji);
    return;
  }

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

  // Fetch group name if not yet known
  if (rawChatId && rawChatId.indexOf('@g.us') !== -1) {
    var entry = cache.getChatEntry(chatId);
    if (!entry || !entry.name || entry.name === 'Skupina') {
      axios.get(
        process.env.EVO_API_URL + '/group/findGroupInfos/' + process.env.EVO_INSTANCE_NAME,
        { params: { groupJid: rawChatId }, headers: { apikey: process.env.EVO_API_KEY } }
      ).then(function(r) {
        if (r.data && r.data.subject) cache.updateGroupName(chatId, r.data.subject);
      }).catch(function() {});
    }
  }
}

router.post('/', function (req, res) {
  var event = req.body;

  if (event && event.event === 'messages.upsert') {
    processMessage(event.data);
  }

  res.status(200).json({ received: true });
});

module.exports = router;
