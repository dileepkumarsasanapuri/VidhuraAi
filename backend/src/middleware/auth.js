const jwt = require("jsonwebtoken");
const User = require("../models/User");
const mongoose = require("mongoose");

module.exports = async function auth(req, res, next) {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ error: "Unauthorized" });
  }

  const token = authHeader.split(" ")[1];

  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);

    let user = null;
    if (mongoose.connection && mongoose.connection.readyState === 1) {
      user = await User.findById(payload.id).select("-passwordHash");
    } else if (global.__CLARITYSURE_INMEM_USERS) {
      const candidate = global.__CLARITYSURE_INMEM_USERS.get(payload.id);
      if (candidate) {
        const { passwordHash, ...rest } = candidate;
        user = rest;
      }
    }

    if (!user) {
      return res.status(401).json({ error: "Invalid auth token" });
    }

    req.user = user;
    next();
  } catch (error) {
    console.error("Auth middleware error:", error.message);
    return res.status(401).json({ error: "Invalid or expired auth token" });
  }
};
