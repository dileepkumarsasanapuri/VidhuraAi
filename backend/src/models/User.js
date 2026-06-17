const mongoose = require("mongoose");

const defaultSettings = {
  profile: {
    name: "",
    email: "",
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
};

const UserSchema = new mongoose.Schema(
  {
    name: {
      type: String,
      required: true,
      trim: true,
    },
    email: {
      type: String,
      required: true,
      unique: true,
      lowercase: true,
      trim: true,
    },
    passwordHash: {
      type: String,
    },
    googleId: {
      type: String,
      default: null,
    },
    avatar: {
      type: String,
      default: "",
    },
    settings: {
      type: Object,
      default: () => ({
        profile: {
          name: "",
          email: "",
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
      }),
    },
  },
  {
    timestamps: true,
  }
);

module.exports = mongoose.models.User || mongoose.model("User", UserSchema);
