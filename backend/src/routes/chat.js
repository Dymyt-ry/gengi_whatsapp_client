var express = require('express');
var cache = require('../cache');
var router = express.Router();

router.get('/:id', function (req, res) {
  res.json(cache.getMessages(req.params.id));
});

module.exports = router;
