var express = require('express');
var axios = require('axios');
var multer = require('multer');
var router = express.Router();

var upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 }
});

router.post('/', upload.single('image'), function (req, res) {
  if (!req.file)        return res.status(400).json({ error: 'image file is required' });
  if (!req.body.number) return res.status(400).json({ error: 'number is required' });

  var url = process.env.EVO_API_URL
    + '/message/sendMedia/'
    + process.env.EVO_INSTANCE_NAME;

  axios.post(url, {
    number: req.body.number.trim(),
    mediatype: 'image',
    mimetype: req.file.mimetype || 'image/jpeg',
    media: req.file.buffer.toString('base64')
  }, {
    headers: { apikey: process.env.EVO_API_KEY, 'Content-Type': 'application/json' }
  })
  .then(function (response) {
    var messageId = response.data && response.data.key && response.data.key.id;
    res.json({ success: true, messageId: messageId || null });
  })
  .catch(function (err) {
    console.error('sendMedia error:', err.message);
    if (err.response) console.error('EVO response:', JSON.stringify(err.response.data));
    res.status(502).json({ error: 'Failed to send media' });
  });
});

module.exports = router;
