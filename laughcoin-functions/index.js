const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * 📢 Admin Broadcast Function
 * Sends a push notification to all users and saves to Firestore.
 */
exports.sendBroadcast = functions.region("asia-southeast1").https.onCall(async (data, context) => {
  // 1. Security Check: Must be signed in
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Must be signed in.");
  }

  // 2. Security Check: Must be an Admin
  const ADMIN_EMAILS = ["arsalankashif8@gmail.com", "ibraheemarsalan6@gmail.com"];
  const callerEmail = context.auth.token.email || "";
  if (!ADMIN_EMAILS.includes(callerEmail)) {
    throw new functions.https.HttpsError("permission-denied", "Admins only.");
  }

  const { title, body } = data;
  if (!title || !body) {
    throw new functions.https.HttpsError("invalid-argument", "Title and Body are required.");
  }

  try {
    // 3. Save to Firestore for the In-App Popup
    await admin.firestore().collection("broadcasts").add({
      title: title.trim(),
      body: body.trim(),
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      sentBy: callerEmail
    });

    // 4. Send Push Notification to "all" topic
    const message = {
      topic: "all",
      notification: {
        title: title.trim(),
        body: body.trim()
      },
      android: {
        priority: "high",
        notification: {
          sound: "default",
          channel_id: "laughcoin_broadcasts"
        }
      },
      data: { screen: "inbox" }
    };

    const response = await admin.messaging().send(message);
    console.log("Broadcast sent successfully:", response);

    return { success: true, messageId: response };
  } catch (error) {
    console.error("Broadcast failed:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * 🆔 KYC Status Notification
 * Automatically sends a push when an Admin updates a user's kycStatus.
 */
exports.sendKycNotification = functions.region("asia-southeast1").firestore
  .document("users/{userId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    if (before.kycStatus === after.kycStatus) return null;

    const newStatus = after.kycStatus;
    const userId = context.params.userId;
    const fcmToken = after.fcmToken;

    if (!fcmToken) return null;

    let title = "";
    let body = "";

    if (newStatus === "verified") {
      title = "✅ KYC Verified!";
      body = "Your identity is verified. You can now withdraw your LGC balance!";
    } else if (newStatus === "rejected") {
      title = "❌ KYC Rejected";
      body = "Your documents were not accepted. Please resubmit in Profile settings.";
    } else {
      return null;
    }

    try {
      await admin.messaging().send({
        token: fcmToken,
        notification: { title, body },
        android: {
          priority: "high",
          notification: { sound: "default", channel_id: "laughcoin_kyc" }
        }
      });
    } catch (err) {
      console.error("KYC notification failed:", err);
    }
    return null;
  });

/**
 * 💰 Withdrawal Status Notification
 * Automatically sends a push when an Admin updates a withdrawal status.
 */
exports.sendWithdrawalNotification = functions.region("asia-southeast1").firestore
  .document("withdrawals/{withdrawalId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    if (before.status === after.status) return null;

    const uid = after.uid;
    const newStatus = after.status;
    const amount = parseFloat(after.amount || 0).toFixed(2);

    const userDoc = await admin.firestore().collection("users").doc(uid).get();
    const fcmToken = userDoc.data()?.fcmToken;

    if (!fcmToken) return null;

    let title = "";
    let body = "";

    if (newStatus === "completed") {
      title = "💰 Withdrawal Approved!";
      body = `Your withdrawal of ${amount} LGC has been sent to your wallet.`;
    } else if (newStatus === "rejected") {
      title = "❌ Withdrawal Rejected";
      body = "Your withdrawal was rejected. Contact support for more info.";
    } else {
      return null;
    }

    try {
      await admin.messaging().send({
        token: fcmToken,
        notification: { title, body },
        android: {
          priority: "high",
          notification: { sound: "default", channel_id: "laughcoin_withdrawals" }
        }
      });
    } catch (err) {
      console.error("Withdrawal notification failed:", err);
    }
    return null;
  });

/**
 * 💰 AUTO-CREATE WALLET NOTIFICATIONS
 * Triggers when transactions happen - auto-adds to users/{uid}/notifications
 */

// TIP SENT
exports.onTipCreated = functions.firestore
  .document("tips/{tipId}")
  .onCreate(async (snap, context) => {
    try {
      const tip = snap.data();
      const receiverId = tip.receiverId;
      const senderId = tip.senderId;
      const amount = tip.amount || 0;
      const message = tip.message || `Tip received`;
      const timestamp = tip.ts || admin.firestore.Timestamp.now();

      await admin.firestore()
        .collection("users").doc(receiverId)
        .collection("notifications").add({
          type: "tip",
          amount: amount,
          message: message || `Received ${amount} LGC tip`,
          timestamp: timestamp,
          read: false,
          sourceId: context.params.tipId,
          senderId: senderId,
        });

      console.log(`✅ Tip notification created for user ${receiverId}`);
    } catch (error) {
      console.error("❌ Error creating tip notification:", error);
    }
  });

// REWARD EARNED
exports.onRewardCreated = functions.firestore
  .document("rewards/{rewardId}")
  .onCreate(async (snap, context) => {
    try {
      const reward = snap.data();
      const userId = reward.userId;
      const amount = reward.amount || 0;
      const rewardType = reward.type || "daily_login";
      const timestamp = reward.timestamp || admin.firestore.Timestamp.now();

      let message = `${amount} LGC earned`;
      if (rewardType === "daily_login") {
        message = `Daily login bonus earned`;
      } else if (rewardType === "referral") {
        message = `Referral reward earned`;
      }

      await admin.firestore()
        .collection("users").doc(userId)
        .collection("notifications").add({
          type: "reward",
          amount: amount,
          message: message,
          timestamp: timestamp,
          read: false,
          sourceId: context.params.rewardId,
          rewardType: rewardType,
        });

      console.log(`✅ Reward notification created for user ${userId}`);
    } catch (error) {
      console.error("❌ Error creating reward notification:", error);
    }
  });

// WITHDRAWAL APPROVED
exports.onWithdrawalApproved = functions.firestore
  .document("withdrawals/{withdrawalId}")
  .onUpdate(async (change, context) => {
    try {
      const before = change.before.data();
      const after = change.after.data();

      if (before.status !== "approved" && after.status === "approved") {
        const userId = after.userId;
        const amount = after.amount || 0;
        const timestamp = admin.firestore.Timestamp.now();

        await admin.firestore()
          .collection("users").doc(userId)
          .collection("notifications").add({
            type: "withdrawal",
            amount: amount,
            message: `Withdrawal approved - ${amount} LGC`,
            timestamp: timestamp,
            read: false,
            sourceId: context.params.withdrawalId,
            walletAddress: after.walletAddress,
          });

        console.log(`✅ Withdrawal notification created for user ${userId}`);
      }
    } catch (error) {
      console.error("❌ Error creating withdrawal notification:", error);
    }
  });

// GIFT SENT
exports.onGiftCreated = functions.firestore
  .document("gifts/{giftId}")
  .onCreate(async (snap, context) => {
    try {
      const gift = snap.data();
      const receiverId = gift.receiverId;
      const senderId = gift.senderId;
      const amount = gift.amount || 0;
      const timestamp = gift.timestamp || admin.firestore.Timestamp.now();

      await admin.firestore()
        .collection("users").doc(receiverId)
        .collection("notifications").add({
          type: "gift",
          amount: amount,
          message: `Gift received - ${amount} LGC`,
          timestamp: timestamp,
          read: false,
          sourceId: context.params.giftId,
          senderId: senderId,
        });

      console.log(`✅ Gift notification created for user ${receiverId}`);
    } catch (error) {
      console.error("❌ Error creating gift notification:", error);
    }
  });

// BATTLE GIFT SENT
exports.onBattleGiftCreated = functions.firestore
  .document("battle_gifts/{battleGiftId}")
  .onCreate(async (snap, context) => {
    try {
      const battleGift = snap.data();
      const receiverId = battleGift.receiverId;
      const senderId = battleGift.senderId;
      const amount = battleGift.amount || 0;
      const timestamp = battleGift.timestamp || admin.firestore.Timestamp.now();

      await admin.firestore()
        .collection("users").doc(receiverId)
        .collection("notifications").add({
          type: "battle_gift",
          amount: amount,
          message: `Battle gift received - ${amount} LGC`,
          timestamp: timestamp,
          read: false,
          sourceId: context.params.battleGiftId,
          senderId: senderId,
          battleId: battleGift.battleId,
        });

      console.log(`✅ Battle gift notification created for user ${receiverId}`);
    } catch (error) {
      console.error("❌ Error creating battle gift notification:", error);
    }
  });

// DEPOSIT COMPLETED
exports.onDepositCompleted = functions.firestore
  .document("deposits/{depositId}")
  .onUpdate(async (change, context) => {
    try {
      const before = change.before.data();
      const after = change.after.data();

      if (before.status !== "completed" && after.status === "completed") {
        const userId = after.userId;
        const amount = after.amount || 0;
        const timestamp = admin.firestore.Timestamp.now();

        await admin.firestore()
          .collection("users").doc(userId)
          .collection("notifications").add({
            type: "deposit",
            amount: amount,
            message: `Deposit confirmed - ${amount} LGC received`,
            timestamp: timestamp,
            read: false,
            sourceId: context.params.depositId,
          });

        console.log(`✅ Deposit notification created for user ${userId}`);
      }
    } catch (error) {
      console.error("❌ Error creating deposit notification:", error);
    }
  });
