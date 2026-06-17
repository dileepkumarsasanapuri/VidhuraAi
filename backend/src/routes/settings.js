const express = require("express");
const router = express.Router();
const auth = require("../middleware/auth");

router.get("/", auth, (req, res) => {
  res.json({ success: true, data: req.user.settings });
});

router.put("/", auth, async (req, res) => {
  req.user.settings = {
    ...req.user.settings,
    ...req.body,
  };

  await req.user.save();
  res.json({ success: true, data: req.user.settings });
});

module.exports = router;
