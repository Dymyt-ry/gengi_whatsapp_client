var express = require('express');
var axios = require('axios');
var router = express.Router();

router.post('/', function (req, res) {
  var chatId        = req.body.chatId;
  var messageId     = req.body.messageId;
  var originalFromMe = req.body.originalFromMe || false;
  var emoji         = req.body.emoji || '';

  if (!chatId || !messageId) {
    return res.status(400).json({ error: 'chatId and messageId are required' });
  }

  var url = process.env.EVO_API_URL
    + '/message/sendReaction/'
    + process.env.EVO_INSTANCE_NAME;

  axios.post(url, {
    key: {
      remoteJid: chatId,
      fromMe: originalFromMe,
      id: messageId
    },
    reaction: emoji
  }, {
    headers: { apikey: process.env.EVO_API_KEY, 'Content-Type': 'application/json' }
  })
  .then(function () {
    res.json({ success: true });
  })
  .catch(function (err) {
    console.error('sendReaction error:', err.message);
    res.status(502).json({ error: 'Failed to send reaction' });
  });
});

module.exports = router;
