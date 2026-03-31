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

  var cleanChatId = chatId.trim();
  var cleanText = text.trim();

  if (cleanChatId.indexOf('@lid') !== -1) {
    return res.status(400).json({ error: 'Cannot send to @lid contacts - phone number unknown' });
  }

  var number;
  if (cleanChatId.indexOf('@g.us') !== -1) {
    number = cleanChatId; // groups need full ID with @g.us suffix
  } else {
    number = cleanChatId.replace('@s.whatsapp.net', '');
  }

  var url = process.env.EVO_API_URL + '/message/sendText/' + process.env.EVO_INSTANCE_NAME;

  axios.post(url, {
    number: number,
    options: {
      delay: 1200,
      presence: "composing",
      linkPreview: false
    },
    textMessage: {
      text: cleanText
    }
  }, {
    headers: {
      'apikey': process.env.EVO_API_KEY,
      'Content-Type': 'application/json'
    }
  })
    .then(function (response) {
      var messageId = null;
      if (response.data && response.data.key) {
        messageId = response.data.key.id;
      }
      var msg = cache.addSentMessage(cleanChatId, cleanText, messageId);
      res.json({ sent: true, message: msg });
    })
    .catch(function (err) {
      console.error('\n--- EVOLUTION API ERROR ---');
      if (err.response && err.response.data) {
        console.error('Status:', err.response.status);
        console.error('Detail chyby:', JSON.stringify(err.response.data, null, 2));
      } else {
        console.error('Obecná chyba:', err.message);
      }
      console.error('URL:', url);
      console.error('Chat ID:', cleanChatId);
      console.error('Text:', cleanText);
      console.error('---------------------------\n');

      res.status(502).json({ error: 'Failed to send message via Evolution API' });
    });
});

module.exports = router;