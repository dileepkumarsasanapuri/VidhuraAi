const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const { OAuth2Client } = require("google-auth-library");
const User = require("../models/User");
const auth = require("../middleware/auth");
const mongoose = require("mongoose");
const { v4: uuidv4 } = require("uuid");

const router = express.Router();
const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

// In-memory fallback when MongoDB is not available (dev convenience)
const inMemoryUsers = new Map();
// expose for middleware fallback when mongoose is unavailable
global.__CLARITYSURE_INMEM_USERS = inMemoryUsers;

async function findUserByEmail(email) {
  if (mongoose.connection && mongoose.connection.readyState === 1) {
    return User.findOne({ email });
  }
  return Array.from(inMemoryUsers.values()).find((u) => u.email === email) || null;
}

async function findUserById(id) {
  if (mongoose.connection && mongoose.connection.readyState === 1) {
    return User.findById(id);
  }
  return inMemoryUsers.get(id) || null;
}

async function createInMemoryUser(data) {
  const id = uuidv4();
  const user = { _id: id, ...data, createdAt: new Date(), updatedAt: new Date() };
  inMemoryUsers.set(id, user);
  return user;
}

async function saveUserDoc(user) {
  if (mongoose.connection && mongoose.connection.readyState === 1) {
    return user.save();
  }
  user.updatedAt = new Date();
  inMemoryUsers.set(user._id, user);
  return user;
}

function createJwt(user) {
  return jwt.sign({ id: user._id }, process.env.JWT_SECRET, {
    expiresIn: "7d",
  });
}

function formatUser(user) {
  const { passwordHash, __v, ...safeUser } = user.toObject ? user.toObject() : user;
  return safeUser;
}

router.post("/register", async (req, res) => {
  try {
    const { name, email, password } = req.body;

    if (!name || !email || !password) {
      return res.status(400).json({ error: "Name, email, and password are required." });
    }

    const existingUser = await findUserByEmail(email.toLowerCase().trim());
    if (existingUser) {
      return res.status(409).json({ error: "Email is already registered." });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const userData = {
      name: name.trim(),
      email: email.toLowerCase().trim(),
      passwordHash,
      settings: {
        profile: {
          name: name.trim(),
          email: email.toLowerCase().trim(),
          age: 0,
          occupation: "",
          phone: "",
        },
        healthProfile: {
          familyMembers: "",
          preExistingConditions: [],
          familyHistory: [],
          bloodGroup: "",
        },
        preferences: {
          language: "English",
          notifications: true,
          darkMode: true,
          twoFactorAuth: false,
          dataSharing: false,
        },
      },
    };

    const user = mongoose.connection && mongoose.connection.readyState === 1
      ? await User.create(userData)
      : await createInMemoryUser(userData);

    const token = createJwt(user);

    res.status(201).json({ success: true, token, user: formatUser(user) });
  } catch (error) {
    console.error("Register error:", error.message);
    res.status(500).json({ error: "Unable to register user" });
  }
});

router.post("/login", async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: "Email and password are required." });
    }

    const user = await findUserByEmail(email.toLowerCase().trim());
    if (!user || !user.passwordHash) {
      return res.status(401).json({ error: "Invalid email or password." });
    }

    const passwordMatches = await bcrypt.compare(password, user.passwordHash);
    if (!passwordMatches) {
      return res.status(401).json({ error: "Invalid email or password." });
    }

    const token = createJwt(user);
    res.json({ success: true, token, user: formatUser(user) });
  } catch (error) {
    console.error("Login error:", error.message);
    res.status(500).json({ error: "Unable to log in" });
  }
});

router.post("/google", async (req, res) => {
  try {
    const { credential } = req.body;
    if (!credential) {
      return res.status(400).json({ error: "Google credential token is required." });
    }

    const ticket = await googleClient.verifyIdToken({
      idToken: credential,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    const payload = ticket.getPayload();

    if (!payload || !payload.email) {
      return res.status(400).json({ error: "Unable to verify Google account." });
    }

    const email = payload.email.toLowerCase().trim();
    let user = await findUserByEmail(email);

    if (!user) {
      const userData = {
        name: payload.name || "Google User",
        email,
        googleId: payload.sub,
        avatar: payload.picture || "",
        settings: {
          profile: {
            name: payload.name || "",
            email,
            age: 0,
            occupation: "",
            phone: "",
          },
          healthProfile: {
            familyMembers: "",
            preExistingConditions: [],
            familyHistory: [],
            bloodGroup: "",
          },
          preferences: {
            language: "English",
            notifications: true,
            darkMode: true,
            twoFactorAuth: false,
            dataSharing: false,
          },
        },
      };

      user = mongoose.connection && mongoose.connection.readyState === 1
        ? await User.create(userData)
        : await createInMemoryUser(userData);
    } else if (!user.googleId) {
      user.googleId = payload.sub;
      user.avatar = payload.picture || user.avatar;
      await saveUserDoc(user);
    }

    const token = createJwt(user);
    res.json({ success: true, token, user: formatUser(user) });
  } catch (error) {
    console.error("Google login error:", error.message);
    res.status(500).json({ error: "Unable to authenticate with Google." });
  }
});

router.get("/me", auth, (req, res) => {
  res.json({ success: true, data: req.user });
});

module.exports = router;
