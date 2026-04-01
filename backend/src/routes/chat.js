var express = require('express');
var cache = require('../cache');
var router = express.Router();

router.get('/:id', function (req, res) {
  res.json(cache.getMessages(req.params.id));
});

router.post('/:id/rename', function (req, res) {
  var chatId = req.params.id;
  var name = req.body && req.body.name;
  if (!name || !name.trim()) {
    return res.status(400).json({ error: 'name is required' });
  }
  cache.renameChat(chatId, name.trim());
  res.json({ renamed: true, chatId: chatId, name: name.trim() });
});

module.exports = router;
