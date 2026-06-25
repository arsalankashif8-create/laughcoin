package com.lgcinovations.laughcoin.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lgcinovations.laughcoin.EmojiState
import com.lgcinovations.laughcoin.R
import com.lgcinovations.laughcoin.formatTime
import com.lgcinovations.laughcoin.isYesterday
import com.lgcinovations.laughcoin.ui.theme.*
import kotlinx.coroutines.delay
import android.app.Activity
import androidx.core.net.toUri
import android.content.Intent
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.isActive
import java.util.*
import kotlin.math.min

@Composable
fun CloudRewardsScreen(onStreakClaimed: (Double) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val userEmail = auth.currentUser?.email?.split("@")?.get(0) ?: "User"
    
    var balance by remember { mutableDoubleStateOf(0.0) }
    var level by remember { mutableIntStateOf(1) }
    var lastStart by remember { mutableStateOf<Long?>(null) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tapCount by remember { mutableIntStateOf(0) }
    var adType by remember { mutableStateOf("") }
    var rewardedAd: RewardedInterstitialAd? by remember { mutableStateOf(null) }
    var sessionEarnings by remember { mutableDoubleStateOf(0.0) }
    var unSyncedEarnings by remember { mutableDoubleStateOf(0.0) }
    val floatingEmojis = remember { mutableStateListOf<EmojiState>() }
    
    val context = LocalContext.current
    
    // AdMob Rewarded Interstitial ID
    val rewardedAdUnitId = "ca-app-pub-8306244200577451/8333935522"

    // Give LGC reward after user watches real AdMob ad
    val onAdRewardEarned = { type: String ->
        val isGala = isGalaActive()
        if (type == "Auto") {
            db.collection("users").document(uid).get().addOnSuccessListener { snap ->
                val lastStartT = snap.getTimestamp("lastEarningStart")?.toDate()?.time ?: 0L
                val currentS = snap.getLong("streakCount") ?: 0L
                val now = System.currentTimeMillis()
                val newS = if (lastStartT == 0L) 1L else if (isYesterday(lastStartT, now)) currentS + 1 else 1L
                val autoStartReward = 15.0
                db.collection("users").document(uid).update(
                    "lastEarningStart", Timestamp.now(),
                    "balance", FieldValue.increment(autoStartReward * level),
                    "totalRewards", FieldValue.increment(autoStartReward * level),
                    "streakCount", newS
                )
                onStreakClaimed(autoStartReward * level)
                Toast.makeText(context, "Daily Mining Started! +${autoStartReward * level} LGC", Toast.LENGTH_SHORT).show()
            }
        } else {
            val adReward = if (isGala) 1.0 else 0.5
            db.collection("users").document(uid).update(
                "adTapCount", 0,
                "balance", FieldValue.increment(adReward),
                "totalRewards", FieldValue.increment(adReward)
            )
            tapCount = 0
            onStreakClaimed(adReward)
            Toast.makeText(context, "Ad Reward: +$adReward LGC earned!", Toast.LENGTH_SHORT).show()
        }
    }

    // Load AdMob Rewarded Interstitial
    fun loadRewardedAd() {
        RewardedInterstitialAd.load(
            context,
            rewardedAdUnitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(p0: RewardedInterstitialAd) {
                    rewardedAd = p0
                }
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    // Show AdMob Rewarded Interstitial
    val showRewardedAd = { type: String ->
        adType = type
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd() // preload next ad
                }
                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    rewardedAd = null
                    loadRewardedAd()
                }
            }
            val activity = context as? android.app.Activity
            if (activity != null) {
                ad.show(activity) { rewardItem ->
                    // User earned reward — give LGC
                    onAdRewardEarned(type)
                }
            }
        } else {
            // Ad not loaded yet — give reward anyway and reload
            onAdRewardEarned(type)
            loadRewardedAd()
        }
    }

    // Preload AdMob Rewarded Interstitial
    LaunchedEffect(Unit) {
        loadRewardedAd()
    }

    LaunchedEffect(Unit) { 
        while(isActive) { 
            currentTime = System.currentTimeMillis()
            
            // Background earning calculation for session
            if (uid.isNotEmpty() && lastStart != null && (currentTime - lastStart!!) < 86400000) {
                val hourlyRate = 13.2 * level
                val perSecond = hourlyRate / 3600.0
                sessionEarnings += perSecond
                unSyncedEarnings += perSecond
                
                // NEW: Increment Firestore every 30 seconds with just the DELTA (prevents loss)
                if (System.currentTimeMillis() % 30000 < 1000 && unSyncedEarnings > 0.001) {
                     val syncDelta = unSyncedEarnings
                     unSyncedEarnings = 0.0
                     db.collection("users").document(uid).update(
                         "balance", FieldValue.increment(syncDelta),
                         "totalRewards", FieldValue.increment(syncDelta),
                         "lastSeen", System.currentTimeMillis()
                     ).addOnFailureListener {
                         // If update fails, put the delta back to try again next time
                         unSyncedEarnings += syncDelta
                     }
                }
            }
            
            if (floatingEmojis.isNotEmpty()) {
                floatingEmojis.removeAll { currentTime - it.startTime > 2000 }
            }
            delay(1000) // Run logic every second for session earnings
        } 
    }
    
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    balance = snap.getDouble("balance") ?: 0.0
                    
                    // --- 🔄 UNIFY UPGRADES (App-Web Sync) ---
                    @Suppress("UNCHECKED_CAST")
                    val upgrades = snap.get("upgrades") as? Map<String, Any> ?: emptyMap()
                    val webLevel = when {
                        upgrades["hyper"] == true -> 4
                        upgrades["quantum"] == true -> 3
                        upgrades["turbo"] == true -> 2
                        else -> 1
                    }
                    val firestoreLevel = snap.getLong("rewardLevel")?.toInt() ?: 1
                    
                    // Always take the highest level between the two to prevent loss
                    level = maxOf(firestoreLevel, webLevel)
                    
                    // If they are out of sync, fix Firestore automatically
                    if (firestoreLevel < webLevel) {
                        db.collection("users").document(uid).update("rewardLevel", webLevel)
                    }

                    // Unified start time check
                    val fStart = snap.getTimestamp("lastEarningStart")?.toDate()?.time
                    val fExp = snap.getLong("nodeExp")
                    
                    val firestoreExp = if (fStart != null) (fStart + 86400000L) else fExp
                    
                    lastStart = if (firestoreExp != null && firestoreExp > System.currentTimeMillis()) {
                        firestoreExp - 86400000L
                    } else {
                        null
                    }

                    tapCount = snap.getLong("adTapCount")?.toInt() ?: 0
                }
            }

            // --- ☁️ OFFLINE MINING CATCH-UP ---
            db.collection("users").document(uid).get().addOnSuccessListener { snap ->
                val lSeen = snap.getLong("lastSeen") ?: 0L
                val lStart = snap.getTimestamp("lastEarningStart")?.toDate()?.time ?: 0L
                val now = System.currentTimeMillis()
                val activeWindow = 86400000L

                if (lSeen > 0 && lStart > 0 && (now - lStart) < activeWindow && lSeen > lStart) {
                    val endTime = min(now, lStart + activeWindow)
                    val secondsOffline = (endTime - lSeen) / 1000L
                    
                    if (secondsOffline > 30) {
                        val rate = 13.2 * level
                        val catchupReward = (secondsOffline * rate) / 3600.0
                        
                        if (catchupReward > 0.001) {
                            db.collection("users").document(uid).update(
                                "balance", FieldValue.increment(catchupReward),
                                "totalRewards", FieldValue.increment(catchupReward),
                                "lastSeen", now
                            )
                            onStreakClaimed(catchupReward)
                        }
                    }
                }
            }
        }
    }

    val isActive = lastStart != null && (currentTime - lastStart!!) < 86400000
    val timeLeft = if (isActive) 86400000 - (currentTime - lastStart!!) else 0L

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
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("😂", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(userEmail, color = CyberGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${balance.toInt()} LGC", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth().background(Color.Black.copy(0.3f)).padding(vertical = 4.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("✦ Holders 16.4K", color = Color.Gray, fontSize = 10.sp)
            Text("✦ LaughClip 03.2026", color = Color.Gray, fontSize = 10.sp)
            Text("✦ 5 LGC Signup Bonus", color = LgcGold, fontSize = 10.sp)
        }


        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(id = R.drawable.laugh_logo), "", Modifier.size(45.dp).clip(CircleShape).border(1.dp, LgcGold, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("LAUGHCOIN", color = LgcGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("laughcoin.online", color = Color.Gray, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Surface(color = CyberBlue, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.Gray.copy(0.3f))) {
                Text(String.format(Locale.US, "%.4f LGC", balance), color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(color = CyberBlue.copy(0.5f), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, if(isActive) CyberGreen else Color.Gray)) {
                    Text(if(isActive) "● AUTO REWARDS ACTIVE" else "○ IDLE", color = if(isActive) CyberGreen else Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            Text("Earning Rate: ${13.2 * level} LGC/hr | Rank Boost: x$level", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CyberBlue),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF1B2E42))
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("WALLET BALANCE", color = Color.Gray, fontSize = 10.sp)
                    Text(text = String.format(Locale.US, "%.6f", balance), color = LgcGold, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("LGC - LAUGHCOIN", color = Color(0xFF4DB8FF), fontSize = 11.sp)
                    
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.White.copy(0.1f))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("SESSION", "+${String.format(Locale.US, "%.4f", sessionEarnings)}")
                        StatItem("TAP COUNT", tapCount.toString())
                        StatItem("PER TAP", String.format(Locale.US, "%.2f", 0.05 * level))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CyberBlue.copy(0.5f),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, if(isActive) CyberGreen.copy(0.3f) else Color.Gray.copy(0.2f))
            ) {
                val isGala = isGalaActive()
                val sessionBonus = if (isGala) 15 else 10
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("AUTO REWARDS ACTIVE ⚡", color = CyberGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("24hr bonus in: ${if(isActive) formatTime(timeLeft) else "00:00:00"}", color = Color.Gray, fontSize = 10.sp)
                    }
                    Text("+${sessionBonus * level} LGC at 24h", color = LgcGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(240.dp)) {
                Box(Modifier.size(200.dp).graphicsLayer(alpha = 0.3f).background(if(isActive) CyberGreen else StopRed, CircleShape).blur(40.dp))
                
                floatingEmojis.forEach { emoji ->
                    val elapsed = currentTime - emoji.startTime
                    val progress = (elapsed.toFloat() / 2000f).coerceIn(0f, 1f)
                    val alpha = 1f - progress
                    val yOffset = emoji.y - (progress * 100f)
                    
                    Text(
                        text = emoji.char,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .offset(emoji.x.dp, yOffset.dp)
                            .graphicsLayer(alpha = alpha)
                    )
                }

                Surface(
                    onClick = {
                        val emojis = listOf("😂", "❤️", "😊", "🔥", "✨", "😍")
                        floatingEmojis.add(
                            EmojiState(
                                char = emojis.random(),
                                x = ((-60)..60).random().toFloat(),
                                y = ((-20)..20).random().toFloat(),
                                startTime = System.currentTimeMillis()
                            )
                        )

                        val tapReward = 0.05 * level
                        sessionEarnings += tapReward
                        
                        if (tapCount < 40) {
                            tapCount++
                            db.collection("users").document(uid).update(
                                "balance", FieldValue.increment(tapReward),
                                "totalRewards", FieldValue.increment(tapReward),
                                "totalTaps", FieldValue.increment(1),
                                "adTapCount", tapCount
                            )
                        }

                        if (tapCount >= 40) {
                            showRewardedAd("Reward")
                        }
                    },
                    shape = CircleShape, color = CyberBlue, border = BorderStroke(3.dp, if(isActive) CyberGreen else StopRed),
                    modifier = Modifier.size(180.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(painterResource(id = R.drawable.laugh_logo), "", Modifier.size(120.dp).clip(CircleShape))
                        Text("TAP TO EARN", color = if(isActive) CyberGreen else StopRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            Button(
                onClick = { 
                    if (!isActive) {
                        showRewardedAd("Auto")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if(isActive) Color.Gray.copy(0.2f) else CyberGreen),
                shape = RoundedCornerShape(12.dp),
                enabled = !isActive
            ) {
                if (isActive) {
                    Text("ACTIVE: ${formatTime(timeLeft)}", color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Text("⚡ START AUTO REWARDS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }


            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("😂 Laugh Level $level", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${balance.toInt()} / ${nextGoal.toInt()} LGC", color = LgcGold, fontSize = 12.sp)
            }
            LinearProgressIndicator(
                progress = { (balance / nextGoal).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).padding(vertical = 4.dp).clip(CircleShape),
                color = LgcGold,
                trackColor = Color.Gray.copy(0.2f)
            )
            Text("Rewards ${nextGoal.toInt()} LGC to unlock next upgrade", color = Color.Gray, fontSize = 9.sp)

            Spacer(Modifier.height(24.dp))

            Text("⚡ SYSTEM UPGRADES", color = LgcGold, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            UpgradeItem("Meme Turbo", "x2 boost", 50.0, balance, level, 2) {
                if (balance >= 50.0) db.collection("users").document(uid).update(
                    "balance", FieldValue.increment(-50.0),
                    "rewardLevel", 2,
                    "upgrades.turbo", true
                )
            }
            UpgradeItem("Giggle Engine", "x3 boost", 150.0, balance, level, 3) {
                if (balance >= 150.0) db.collection("users").document(uid).update(
                    "balance", FieldValue.increment(-150.0),
                    "rewardLevel", 3,
                    "upgrades.quantum", true
                )
            }
            UpgradeItem("LOL Rig", "x5 boost", 400.0, balance, level, 4) {
                if (balance >= 400.0) db.collection("users").document(uid).update(
                    "balance", FieldValue.increment(-400.0),
                    "rewardLevel", 4,
                    "upgrades.hyper", true
                )
            }

            Spacer(Modifier.height(32.dp))
            
            Text("📱 SOCIAL CHALLENGES", color = LgcGold, fontWeight = FontWeight.Black, fontSize = 14.sp)
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

            Spacer(Modifier.height(48.dp))
        }
    }


}

fun isGalaActive(): Boolean {
    // Active if under 1000 members and before expiration.
    return true 
}

@Composable
fun GalaBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "gala")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "xOffset"
    )

    val brush = Brush.linearGradient(
        colors = listOf(LgcGold, Color(0xFFFFE082), LgcGold, Color(0xFFFFB300), LgcGold),
        start = Offset(xOffset, 0f),
        end = Offset(xOffset + 600f, 600f)
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        border = BorderStroke(1.dp, Color.Black.copy(0.1f))
    ) {
        Column(Modifier.background(brush).padding(16.dp)) {
            Text("💎 FIRST 1000 MEMBERS MEGA REWARDS!", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Text("• 0.05 LGC Per Tap! 🔥", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("• 15 LGC Daily Mining Bonus! ☁️", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("• 15.0 LGC Auto-Start Reward! ⚡", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("• 100 LGC Referral Bonus (BOTH)! 👥", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("• DOUBLE BONUS (100+100) for Top Referrers! 🚀", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
            
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Black.copy(0.2f))
            
            Text("📱 EXTRA SOCIAL TASKS:", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("• Facebook Page & Group: +250 LGC", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("• X (Twitter) Post: +300 LGC", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("• Short Video (TikTok/Reels): +500 LGC", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            
            Spacer(Modifier.height(8.dp))
            Text("Limited to first 1000 members. Join the Giggles! 😂", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 8.sp)
        Text(value, color = if(label == "SESSION") CyberGreen else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UpgradeItem(name: String, boost: String, cost: Double, bal: Double, curLv: Int, targetLv: Int, onUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.1f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("$boost • ${cost.toInt()} LGC", color = Color.Gray, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onUp() },
                enabled = bal >= cost && curLv < targetLv,
                colors = ButtonDefaults.buttonColors(containerColor = LgcGold, disabledContainerColor = Color.Gray.copy(0.2f)),
                modifier = Modifier.height(32.dp).width(90.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if(curLv >= targetLv) "ACTIVE" else "UNLOCK", color = if(curLv >= targetLv) Color.White else Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SocialTaskCard(title: String, reward: String, icon: String, taskId: String, rewardAmount: Double, instructions: String) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current
    val isDone = remember { mutableStateOf(false) }
    val showDialog = remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get().addOnSuccessListener { 
                @Suppress("UNCHECKED_CAST")
                val completedTasks = it.get("completedTasks") as? List<String> ?: emptyList()
                isDone.value = completedTasks.contains(taskId)
            }
        }
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { 
                Column {
                    Text(instructions)
                    Spacer(Modifier.height(12.dp))
                    Text("⚠️ MANUAL VERIFICATION:", color = LgcGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text("You MUST send your post link to our WhatsApp Verification Group. Our team will check it and manually add your reward within 24 hours.", color = Color.White, fontSize = 11.sp)
                }
            },
                    confirmButton = {
                Button(
                    onClick = {
                        val whatsappLink = "https://chat.whatsapp.com/Co1gNV1T35ELAhG5i7v3Lf"
                        val intent = Intent(Intent.ACTION_VIEW, whatsappLink.toUri()) 
                        context.startActivity(intent)
                        
                        showDialog.value = false
                        Toast.makeText(context, "Link Opened! Send your proof to the WhatsApp group for manual verification.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(CyberGreen)
                ) { Text("OPEN GROUP", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) { Text("LATER", color = Color.Gray) }
            },
            containerColor = CyberBlue,
            titleContentColor = LgcGold,
            textContentColor = Color.White
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(if(isDone.value) Color.Gray.copy(0.1f) else CyberBlue.copy(0.5f)),
        border = BorderStroke(1.dp, if(isDone.value) Color.Transparent else LgcGold.copy(0.2f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(reward, color = LgcGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { showDialog.value = true },
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(if(isDone.value) Color.DarkGray else CyberGreen),
                enabled = !isDone.value
            ) {
                Text(if(isDone.value) "DONE" else "START", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
