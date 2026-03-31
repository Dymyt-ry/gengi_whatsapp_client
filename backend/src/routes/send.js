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

  // OPRAVENÁ STRUKTURA PRO EVOLUTION API
  axios.post(url, {
    number: chatId,
    options: {
      delay: 0,
      presence: "composing"
    },
    textMessage: {
      text: text
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
      var msg = cache.addSentMessage(chatId, text, messageId);
      res.json({ sent: true, message: msg });
    })
    .catch(function (err) {
      // DETAILNÍ VÝPIS CHYBY Z EVOLUTION API
      console.error('\n--- EVOLUTION API ERROR ---');
      if (err.response && err.response.data) {
        console.error('Status:', err.response.status);
        console.error('Detail chyby:', JSON.stringify(err.response.data, null, 2));
      } else {
        console.error('Obecná chyba:', err.message);
      }
      console.error('URL, kam se to poslalo:', url);
      console.error('Chat ID (number):', chatId);
      console.error('---------------------------\n');

      res.status(502).json({ error: 'Failed to send message via Evolution API' });
    });
});

module.exports = router;