package com.lgcinovations.laughcoin.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lgcinovations.laughcoin.R
import com.lgcinovations.laughcoin.isGalaActive
import com.lgcinovations.laughcoin.ui.theme.*
import java.util.*

@Composable
fun HomeScreen(externalBalance: Double = 0.0) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val userEmail = auth.currentUser?.email?.split("@")?.get(0) ?: "User"
    val context = LocalContext.current

    var balance by remember { mutableDoubleStateOf(externalBalance) }
    // Sync whenever globalBalance from MainAppScreen updates
    LaunchedEffect(externalBalance) { if (externalBalance > 0.0) balance = externalBalance }
    var totalRewards by remember { mutableDoubleStateOf(0.0) }
    var recentTxns by remember { mutableStateOf(listOf<Pair<String,String>>()) }
    var taps by remember { mutableLongStateOf(0L) }
    var level by remember { mutableIntStateOf(1) }
    var referrals by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(1) }
    var inviteCode by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(userEmail) }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    balance = snap.getDouble("balance") ?: 0.0
                    val dbTotal = snap.getDouble("totalRewards") ?: balance
                        // Load recent rewards from notifications subcollection
                        db.collection("users").document(uid)
                            .collection("notifications")
                            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(5)
                            .get()
                            .addOnSuccessListener { docs ->
                                if (!docs.isEmpty) {
                                    recentTxns = docs.map { d ->
                                        val msg = d.getString("message") ?: d.getString("title") ?: "Reward"
                                        val amt = d.getDouble("amount") ?: d.getDouble("lgc") ?: 0.0
                                        Pair(msg, "+${String.format("%.4f", amt)} LGC")
                                    }
                                } else {
                                    // Fallback: build from user doc fields
                                    val txns = mutableListOf<Pair<String,String>>()
                                    val b = snap.getDouble("balance") ?: 0.0
                                    val signupBonus = snap.getDouble("signupBonus") ?: 0.0
                                    val loginBonus  = snap.getDouble("loginBonus")  ?: 0.0
                                    val refBonus    = snap.getDouble("referralBonus") ?: 0.0
                                    if (b > 0)          txns.add(Pair("💰 Current Balance",     "+${String.format("%.4f", b)} LGC"))
                                    if (signupBonus > 0) txns.add(Pair("🎁 Signup Bonus",       "+${String.format("%.4f", signupBonus)} LGC"))
                                    if (loginBonus > 0)  txns.add(Pair("📅 Daily Login Bonus",  "+${String.format("%.4f", loginBonus)} LGC"))
                                    if (refBonus > 0)    txns.add(Pair("👥 Referral Bonus",     "+${String.format("%.4f", refBonus)} LGC"))
                                    if (txns.isEmpty())  txns.add(Pair("⛏️ Mining Active",      "Keep tapping!"))
                                    recentTxns = txns
                                }
                            }
                            .addOnFailureListener {
                                // If query fails show balance info
                                val b = snap.getDouble("balance") ?: 0.0
                                recentTxns = listOf(
                                    Pair("💰 Total Balance", "+${String.format("%.4f", b)} LGC"),
                                    Pair("⛏️ Keep Mining!", "Tap to earn more LGC")
                                )
                            }
                    totalRewards = if (dbTotal < balance) {
                        db.collection("users").document(uid).update("totalRewards", balance)
                        balance
                    } else {
                        dbTotal
                    }
                    taps = snap.getLong("totalTaps") ?: 0L
                    level = snap.getLong("rewardLevel")?.toInt() ?: 1
                    referrals = snap.getLong("referrals")?.toInt() ?: 0
                    streak = snap.getLong("streakCount")?.toInt() ?: 0
                    inviteCode = snap.getString("inviteCode") ?: uid.take(8).uppercase()
                    username = snap.getString("username") ?: userEmail
                }
            }
        }
    }

    val nextGoal = when(level) {
        1 -> 50.0
        2 -> 150.0
        3 -> 400.0
        else -> 1000.0
    }

    Column(
        modifier = Modifier.fillMaxSize().background(CyberDark).verticalScroll(rememberScrollState())
    ) {
        if (isGalaActive()) GalaBanner()
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(id = R.drawable.laugh_logo), "", Modifier.size(35.dp).clip(CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("LAUGHCOIN", color = LgcGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Surface(color = CyberBlue, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.Gray.copy(0.3f))) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(CyberGreen, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(String.format(Locale.US, "%.2f LGC", balance), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("👋 $username", color = CyberGreen, fontSize = 12.sp)
                    Text(
                        text = "🔗 Invite Link: laughcoin.online/?ref=$inviteCode", 
                        color = Color.Gray, 
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            val fullLink = "https://laughcoin.online/?ref=$inviteCode"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LGC Invite Link", fullLink))
                            Toast.makeText(context, "Link Copied!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth()) {
                StatCard(Modifier.weight(1f), String.format(Locale.US, "%.2f", balance), "LGC Balance")
                Spacer(Modifier.width(8.dp))
                StatCard(Modifier.weight(1f), String.format(Locale.US, "%.2f", totalRewards), "Total Rewards")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                StatCard(Modifier.weight(1f), taps.toString(), "Total Taps")
                Spacer(Modifier.width(8.dp))
                StatCard(Modifier.weight(1f), referrals.toString(), "Referrals")
            }

            Spacer(Modifier.height(16.dp))

            Text("🔥 EARN MORE LGC", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            TaskCard("Join WhatsApp Group", "+10 LGC", "https://chat.whatsapp.com/KkENBLjYQ3146uP5na6lcu", "wa_task")
            TaskCard("Join Telegram Channel", "+10 LGC", "https://t.me/laughcoinupdate", "tg_task")
            
            Spacer(Modifier.height(16.dp))
            Text("📱 SOCIAL CHALLENGES", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            
            SocialTaskCard(
                title = "Facebook Post & Group",
                reward = "+250 LGC",
                icon = "👥",
                taskId = "fb_post_task",
                rewardAmount = 250.0,
                instructions = "Make a post about LaughCoin on your Facebook page and in 1 crypto group. Tag #LaughCoin."
            )
            
            SocialTaskCard(
                title = "X (Twitter) Tagging",
                reward = "+300 LGC",
                icon = "🐦",
                taskId = "x_post_task",
                rewardAmount = 300.0,
                instructions = "Post about LaughCoin on X, tag 3 friends and use #LaughCoin #LGC."
            )
            
            SocialTaskCard(
                title = "Video Content (TikTok/YT)",
                reward = "+500 LGC",
                icon = "🎬",
                taskId = "video_post_task",
                rewardAmount = 500.0,
                instructions = "Create a short video (TikTok, Reels, or YT Short) about LaughCoin and post it."
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
                border = BorderStroke(1.dp, Color.White.copy(0.05f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("⚡ REWARD RATE", color = LgcGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${String.format(Locale.US, "%.1f", 13.2 * level)} LGC/hr", color = LgcGold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Tap logo in Rewards to earn instantly", color = Color.Gray, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
                border = BorderStroke(1.dp, Color.White.copy(0.05f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📊 REWARD LEVEL", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("😂 Laugh Level $level", color = Color.White, fontSize = 12.sp)
                        Text("${balance.toInt()} / ${nextGoal.toInt()} LGC", color = LgcGold, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(
                        progress = { (balance / nextGoal).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).padding(vertical = 4.dp).clip(CircleShape),
                        color = LgcGold,
                        trackColor = Color.Gray.copy(0.2f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
                border = BorderStroke(1.dp, Color.White.copy(0.05f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("📅 DAILY STREAK", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("$streak Days 🔥", color = CyberGreen, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CyberBlue),
                border = BorderStroke(1.dp, LgcGold.copy(0.2f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🪙 TOKEN INFORMATION", color = LgcGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Contract Address:", color = Color.Gray, fontSize = 9.sp)
                    Text(
                        "0x03307436d6532Eb1A514E08e225ac99879D88185", 
                        color = Color.White, 
                        fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LGC Contract", "0x03307436d6532Eb1A514E08e225ac99879D88185"))
                            Toast.makeText(context, "Contract Copied!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://www.dextools.io/app/bnb/pair-explorer/0x1ec1ef12394cd2fC1f0D9303e756c4C56Ea117A6".toUri())
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(Color(0xFF00D1FF)),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("VIEW ON DEXTOOLS 📊", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
                border = BorderStroke(1.dp, Color.White.copy(0.05f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("📋 RECENT REWARDS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    if (recentTxns.isEmpty()) {
                        Text("No transactions yet", color = Color.Gray, fontSize = 11.sp)
                    } else {
                        recentTxns.forEach { txn ->
                            TransactionItem(txn.first, txn.second)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun TaskCard(title: String, reward: String, url: String, taskId: String) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current
    var isDone by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get().addOnSuccessListener { 
                @Suppress("UNCHECKED_CAST")
                val completedTasks = it.get("completedTasks") as? List<String> ?: emptyList()
                isDone = completedTasks.contains(taskId)
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Membership 🛡️", fontWeight = FontWeight.Bold) },
            text = { 
                Text("Our system audits group memberships before processing withdrawals. If you are found to have claimed the reward without joining, your account and LGC balance may be permanently forfeited.\n\nDo you confirm you have joined the group?") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        db.collection("users").document(uid).update(
                            "balance", FieldValue.increment(10.0),
                            "totalRewards", FieldValue.increment(10.0),
                            "completedTasks", FieldValue.arrayUnion(taskId)
                        ).addOnSuccessListener { 
                            isDone = true
                            Toast.makeText(context, "Verification Pending! +10 LGC Reward!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(CyberGreen)
                ) { Text("I HAVE JOINED", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("CANCEL", color = Color.Gray) }
            },
            containerColor = CyberBlue,
            titleContentColor = LgcGold,
            textContentColor = Color.White
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(if(isDone) Color.Gray.copy(0.1f) else CyberBlue.copy(0.5f)),
        border = BorderStroke(1.dp, if(isDone) Color.Transparent else LgcGold.copy(0.2f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if(taskId.contains("wa")) "💬" else "📢", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(reward, color = LgcGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                    if (!isDone) {
                        showConfirmDialog = true
                    }
                },
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.buttonColors(if(isDone) Color.DarkGray else CyberGreen),
                enabled = !isDone,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(if(isDone) "DONE" else "JOIN & CLAIM", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun StatCard(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
        border = BorderStroke(1.dp, Color.White.copy(0.05f))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = LgcGold, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.Gray, fontSize = 9.sp)
        }
    }
}

@Composable
fun TransactionItem(title: String, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(title, color = Color.LightGray, fontSize = 11.sp)
            Text("Just now", color = Color.Gray, fontSize = 9.sp)
        }
        Text(amount, color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
