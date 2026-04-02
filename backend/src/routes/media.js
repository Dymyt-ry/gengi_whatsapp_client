var express = require('express');
var axios = require('axios');
var router = express.Router();

router.get('/:messageId', function (req, res) {
  var url = process.env.EVO_API_URL
    + '/chat/getBase64FromMediaMessage/'
    + process.env.EVO_INSTANCE_NAME;

  axios.post(url,
    { message: { key: { id: req.params.messageId } } },
    { headers: { apikey: process.env.EVO_API_KEY, 'Content-Type': 'application/json' } }
  )
  .then(function (response) {
    var b64 = response.data && (response.data.base64 || response.data.data);
    if (!b64) return res.status(404).json({ error: 'No media data' });
    var comma = b64.indexOf(',');
    if (comma !== -1) b64 = b64.substring(comma + 1);
    var buffer = Buffer.from(b64, 'base64');
    res.set('Content-Type', 'image/jpeg');
    res.set('Content-Length', buffer.length);
    res.send(buffer);
  })
  .catch(function (err) {
    if (err.response && err.response.status === 404) {
      return res.status(404).json({ error: 'Media not found' });
    }
    console.error('Media fetch error:', err.message);
    res.status(500).json({ error: 'Failed to fetch media' });
  });
});

module.exports = router;
