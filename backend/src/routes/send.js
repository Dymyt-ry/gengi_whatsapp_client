var express = require('express');
var axios = require('axios');
var cache = require('../cache');
var router = express.Router();

router.post('/', function (req, res) {
  var chatId = req.body.chatId;
  var text = req.body.text;

  if (!chatId || !text) {
    return res.status(400).json({ error: 'chatId and text are required' });
  }

  var url = process.env.EVO_API_URL + '/message/sendText/' + process.env.EVO_INSTANCE_NAME;

  axios.post(url, {
    number: chatId,
    text: text
  }, {
    headers: { apikey: process.env.EVO_API_KEY }
  })
    .then(function (response) {
      var messageId = null;
      if (response.data && response.data.key) {
        messageId = response.data.key.id;
      }
      var msg = cache.addSentMessage(chatId, text, messageId);
      res.json({ sent: true, message: msg });
    })
    .catch(function (err) {
      console.error('Error sending message:', err.message);
      res.status(502).json({ error: 'Failed to send message via Evolution API' });
    });
});

module.exports = router;
