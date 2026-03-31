function authMiddleware(req, res, next) {
  var token = req.headers['x-api-token'];

  if (!token || token !== process.env.AUTH_TOKEN) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  next();
}

module.exports = authMiddleware;
