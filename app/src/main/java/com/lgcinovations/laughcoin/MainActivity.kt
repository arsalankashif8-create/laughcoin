package com.lgcinovations.laughcoin

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import com.lgcinovations.laughcoin.ui.screens.*
import com.lgcinovations.laughcoin.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*
import android.Manifest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Initialize Remote Config
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf(
            "current_version_code" to 1L,
            "update_url" to "https://laughcoin.online/laughcoin.apk",
            "force_update" to false
        ))
        remoteConfig.fetchAndActivate()

        // Initialize App Check (Debug for local, Play Integrity for production)
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
            )
        } else {
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)

            notificationManager.createNotificationChannel(
                android.app.NotificationChannel(
                    "laughcoin_broadcasts",
                    "Announcements",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "LaughCoin announcements and news"
                    enableLights(true)
                    enableVibration(true)
                }
            )

            notificationManager.createNotificationChannel(
                android.app.NotificationChannel(
                    "laughcoin_kyc",
                    "KYC Verification",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Identity verification status updates"
                    enableLights(true)
                    enableVibration(true)
                }
            )

            notificationManager.createNotificationChannel(
                android.app.NotificationChannel(
                    "laughcoin_withdrawals",
                    "Withdrawals",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Withdrawal approval and rejection notifications"
                    enableLights(true)
                    enableVibration(true)
                }
            )
        }

        setContent { LaughcoinTheme { MainAppScreen() } }
    }
}

sealed class Screen(val route: String, val title: String, val icon: String) {
    object Auth : Screen("auth", "LOGIN", "🔐")
    object Home : Screen("home", "HOME", "🏠")
    object Rewards : Screen("rewards", "REWARDS", "🚀")
    object Leaders : Screen("leaders", "LEADERS", "🏆")
    object Profile : Screen("profile", "PROFILE", "👤")
    object Wallet : Screen("wallet", "WALLET", "💰")
    object Privacy : Screen("privacy", "PRIVACY", "📜")
    object Cookies : Screen("cookies", "COOKIES", "🍪")
    object Terms : Screen("terms", "TERMS", "⚖️")
    object About : Screen("about", "ABOUT", "ℹ️")
    object KYC : Screen("kyc", "KYC", "🆔")
    object Inbox : Screen("inbox", "INBOX", "📥")
    object Admin : Screen("admin", "ADMIN", "🛠️")
    object AdInterstitial : Screen("ad_view", "AD", "📢")
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    
    // --- 🎁 GLOBAL STREAK REWARD POPUP ---
    var showStreakPopup by remember { mutableStateOf<Double?>(null) }

    // --- 🔐 PROFILE SYNC & DAILY STREAK ---
    LaunchedEffect(auth.currentUser) {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val bonusPending = snapshot.getBoolean("bonusPending") ?: false
                    
                    // --- 🛡️ CHECK EMAIL VERIFICATION FOR BONUSES ---
                    user.reload().addOnCompleteListener { 
                        if (user.isEmailVerified && bonusPending) {
                            val galaActive = isGalaActive()
                            val signupBonus = if (galaActive) 100.0 else 5.0
                            val refBonus = 100.0
                            val referredBy = snapshot.getString("referredBy") ?: ""

                            val updates = hashMapOf<String, Any>(
                                "bonusPending" to false,
                                "balance" to signupBonus,
                                "totalRewards" to signupBonus
                            )

                            db.collection("users").document(user.uid).update(updates)
                            
                            // Give referral bonuses only after verification
                            if (referredBy.isNotEmpty()) {
                                // Referrer bonus
                                db.collection("users").document(referredBy).update(
                                    "balance", FieldValue.increment(refBonus),
                                    "totalRewards", FieldValue.increment(refBonus),
                                    "referrals", FieldValue.increment(1)
                                )
                                // New user ref bonus
                                db.collection("users").document(user.uid).update(
                                    "balance", FieldValue.increment(refBonus),
                                    "totalRewards", FieldValue.increment(refBonus)
                                )
                            }
                            Toast.makeText(context, "Welcome! Rewards Added! 🎉", Toast.LENGTH_LONG).show()
                        }
                    }

                    val lastLogin = snapshot.getTimestamp("lastLoginDate")?.toDate()?.time ?: 0L
                    val now = System.currentTimeMillis()
                    val oneDay = 24 * 60 * 60 * 1000
                    
                    if (now - lastLogin >= oneDay) {
                        val currentStreak = snapshot.getLong("streakCount") ?: 0L
                        val isContinuation = now - lastLogin < 2 * oneDay
                        val newStreak = if (isContinuation) currentStreak + 1 else 1L
                        
                        db.collection("users").document(user.uid).update(
                            "lastLoginDate", Timestamp.now(),
                            "streakCount", newStreak,
                            "balance", FieldValue.increment(1.0),
                            "totalRewards", FieldValue.increment(1.0)
                        )
                        Toast.makeText(context, "Daily Bonus! +1 LGC 🔥", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            // --- ⏱️ PERIODIC ACTIVITY SYNC (Every 2 min) ---
            while(true) {
                db.collection("users").document(user.uid).update("lastSeen", System.currentTimeMillis())
                delay(120000)
            }
        }
    }

    // --- 🚀 REAL-TIME UPDATE CHECKER ---
    var showUpdatePopup by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf("https://laughcoin.online/laughcoin.apk") }
    var isForceUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersionCode = remoteConfig.getLong("current_version_code")
                updateUrl = remoteConfig.getString("update_url")
                isForceUpdate = remoteConfig.getBoolean("force_update")
                
                val currentVersionCode = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
                    }
                } catch (e: Exception) { 0L }

                if (latestVersionCode > currentVersionCode) {
                    showUpdatePopup = true
                }
            }
        }
    }

    if (showUpdatePopup) {
        AlertDialog(
            onDismissRequest = { if (!isForceUpdate) showUpdatePopup = false },
            title = { Text("Update Available! 🚀") },
            text = { Text("A new version of LaughCoin is available with new rewards and features. Download now to keep earning!") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, updateUrl.toUri())
                    context.startActivity(intent)
                }, colors = ButtonDefaults.buttonColors(LgcGold)) {
                    Text("DOWNLOAD APK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!isForceUpdate) {
                    TextButton(onClick = { showUpdatePopup = false }) { Text("LATER", color = Color.Gray) }
                }
            },
            containerColor = CyberBlue,
            titleContentColor = LgcGold,
            textContentColor = Color.White
        )
    }
    
    // --- 🔐 PERMISSIONS HANDLER (Runs for all users) ---
    val showPermissionPopup = remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val notifyGranted = if (Build.VERSION.SDK_INT >= 33) {
            perms[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            false
        }
        if (notifyGranted) {
            Toast.makeText(context, "Notifications Enabled! 🔔", Toast.LENGTH_SHORT).show()
        }
        showPermissionPopup.value = false
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                delay(2000)
                showPermissionPopup.value = true
            }
        }
    }

    if (showPermissionPopup.value) {
        AlertDialog(
            onDismissRequest = { showPermissionPopup.value = false },
            title = { Text("Permissions Required") },
            text = { Text("LaughCoin needs Notification and Camera access to provide rewards and secure identity verification.") },
            confirmButton = {
                Button(onClick = {
                    val list = mutableListOf(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT >= 33) {
                        list.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(list.toTypedArray())
                }, colors = ButtonDefaults.buttonColors(LgcGold)) { Text("Allow", color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionPopup.value = false }) { Text("Later") }
            },
            containerColor = CyberBlue,
            titleContentColor = LgcGold,
            textContentColor = Color.White
        )
    }
    
    // PERSISTENT LOGIN: Check if user is already logged in
    val startDestination = remember { 
        if (FirebaseAuth.getInstance().currentUser != null) Screen.Home.route else Screen.Auth.route 
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isAuth = currentDestination?.route == Screen.Auth.route

    // --- 📢 IN-APP BROADCAST LISTENER ---
    val showBroadcast = remember { mutableStateOf<Map<String, Any>?>(null) }
    
    // --- 🛡️ EMAIL VERIFICATION OVERLAY ---
    val user = auth.currentUser
    var isEmailVerified by remember { mutableStateOf(user?.isEmailVerified ?: true) }
    
    LaunchedEffect(user) {
        while (user != null && !isEmailVerified) {
            user.reload().addOnCompleteListener { 
                isEmailVerified = user.isEmailVerified
            }
            delay(5000) // Check every 5 seconds
        }
    }

    if (user != null && !isEmailVerified) {
        Box(
            modifier = Modifier.fillMaxSize().background(CyberDark).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(CyberBlue),
                border = BorderStroke(2.dp, LgcGold),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📧 Verify Your Email", color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "We sent a verification link to ${user.email}. Please verify your email to unlock your signup rewards and start earning.",
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { user.sendEmailVerification(); Toast.makeText(context, "Link Resent!", Toast.LENGTH_SHORT).show() },
                        colors = ButtonDefaults.buttonColors(LgcGold)
                    ) { Text("RESEND EMAIL", color = Color.Black) }
                    
                    TextButton(onClick = { auth.signOut() }) {
                        Text("Logout", color = Color.Gray)
                    }
                }
            }
        }
    }

    LaunchedEffect(FirebaseAuth.getInstance().currentUser) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // Subscribe to "all" topic for push notifications
            FirebaseMessaging.getInstance().subscribeToTopic("all").addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to all topic")
                }
            }
            
            // Save FCM token so Cloud Functions can send personal notifications
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token != null) {
                    Log.d("FCM_TOKEN", "Current Token: $token")
                    FirebaseFirestore.getInstance().collection("users").document(user.uid)
                        .update("fcmToken", token)
                        .addOnFailureListener {
                            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                .set(hashMapOf("fcmToken" to token), SetOptions.merge())
                        }
                }
            }

            FirebaseFirestore.getInstance().collection("broadcasts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snap, _ ->
                    val latest = snap?.documents?.firstOrNull()?.data
                    if (latest != null) {
                        val ts = latest["timestamp"] as? Timestamp
                        val now = System.currentTimeMillis()
                        // Lenient check: Show if sent in the last hour to handle clock sync issues
                        if (ts != null && (now - ts.toDate().time) < 3600000) {
                            showBroadcast.value = latest
                        }
                    }
                }
        }
    }

    if (showBroadcast.value != null) {
        AlertDialog(
            onDismissRequest = { /* Handle dismiss if needed */ },
            title = { Text(showBroadcast.value!!["title"] as? String ?: "Announcement", fontWeight = FontWeight.Bold) },
            text = { Text(showBroadcast.value!!["body"] as? String ?: "") },
            confirmButton = { 
                Button(
                    onClick = { showBroadcast.value = null },
                    colors = ButtonDefaults.buttonColors(LgcGold)
                ) { Text("CLOSE", color = Color.Black) } 
            },
            containerColor = CyberBlue,
            titleContentColor = LgcGold,
            textContentColor = Color.White
        )
    }

    Scaffold(
        containerColor = CyberDark,
        topBar = { if (!isAuth) RealTimePriceSlider() },
        bottomBar = {
            if (!isAuth) {
                NavigationBar(containerColor = Color(0xFF050C1A)) {
                    val items = listOf(Screen.Home, Screen.Rewards, Screen.Leaders, Screen.Wallet, Screen.Profile)
                    items.forEach { screen ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = { 
                                // Reset timer for the ad screen and navigate
                                navController.navigate("${Screen.AdInterstitial.route}/${screen.route}")
                            },
                            icon = { Text(screen.icon, fontSize = 20.sp) },
                            label = { Text(screen.title, fontSize = 10.sp, color = Color.White) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination, Modifier.padding(padding)) {
            composable(Screen.Auth.route) { AuthScreen(navController) { navController.navigate(Screen.Home.route) { popUpTo(0) } } }
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Rewards.route) { CloudRewardsScreen { showStreakPopup = it } }
            composable(Screen.Leaders.route) { LeadersScreen() }
            composable(Screen.Wallet.route) { WalletScreen() }
            composable(Screen.Profile.route) { ProfileScreen(navController) { navController.navigate(Screen.Auth.route) { popUpTo(0) } } }
            composable(Screen.Privacy.route) { PolicyScreen("Privacy Policy", PRIVACY_TEXT) }
            composable(Screen.Cookies.route) { PolicyScreen("Cookies Policy", COOKIES_TEXT) }
            composable(Screen.Terms.route) { PolicyScreen("Terms & Conditions", TERMS_TEXT) }
            composable(Screen.About.route) { PolicyScreen("About Us", ABOUT_TEXT) }
            composable(Screen.KYC.route) { KYCScreen() }
            composable(Screen.Inbox.route) { InboxScreen() }
            composable(Screen.Admin.route) { AdminScreen() }
            composable("${Screen.AdInterstitial.route}/{targetRoute}") { backStackEntry ->
                val target = backStackEntry.arguments?.getString("targetRoute") ?: Screen.Home.route
                AdInterstitialScreen(navController, target)
            }
        }
    }

    showStreakPopup?.let { rewardAmount ->
        val isAdReward = rewardAmount == 1.0 || rewardAmount == 0.5
        AlertDialog(
            onDismissRequest = { showStreakPopup = null },
            title = { Text(if (isAdReward) "Reward Claimed! 📺" else "Daily Bonus Claimed! 🎁", fontWeight = FontWeight.Black) },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isAdReward) "📺 AD WATCHED" else "🔥 STREAK ACTIVE", color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("You received", color = Color.Gray)
                    Text("+${rewardAmount} LGC", color = LgcGold, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text(if (isAdReward) "Keep watching to earn more!" else "Keep it up to earn more!", color = Color.Gray, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showStreakPopup = null },
                    colors = ButtonDefaults.buttonColors(LgcGold),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("AWESOME!", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            containerColor = CyberBlue,
            titleContentColor = LgcGold,
            textContentColor = Color.White
        )
    }
}

@Composable
fun AdInterstitialScreen(navController: androidx.navigation.NavController, targetRoute: String) {
    var timeLeft by remember { mutableIntStateOf(10) }
    val adLink = "https://interventioncopiedloitering.com/zex8afkiwf?key=a5339c624f9cc5ca5d27a83b4a14deda"
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        navController.navigate(targetRoute) {
            popUpTo(Screen.AdInterstitial.route) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.9f))
            .clickable(enabled = false) {}, 
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SPONSORED AD", color = LgcGold, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Surface(
                    color = if(timeLeft > 0) Color.Gray else LgcGold,
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        if (timeLeft <= 0) {
                            navController.navigate(targetRoute) {
                                popUpTo(Screen.AdInterstitial.route) { inclusive = true }
                            }
                        }
                    }
                ) {
                    Text(
                        text = if(timeLeft > 0) "CLOSE IN ${timeLeft}s" else "CLOSE AD ✕",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            
            // Full Screen Ad Content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, adLink.toUri())
                        context.startActivity(intent)
                    },
                colors = CardDefaults.cardColors(CyberBlue),
                border = BorderStroke(2.dp, LgcGold),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = android.webkit.WebViewClient()
                                loadUrl(adLink)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Overlay to ensure clicks go to the sponsor link if needed, 
                    // or just let the WebView handle it.
                }
            }

            Spacer(Modifier.height(20.dp))
            
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, adLink.toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(LgcGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("VISIT SPONSOR TO SYNC REWARDS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(10.dp))
            Text("Your session is being verified...", color = Color.Gray, fontSize = 10.sp)
        }
    }
}


@Composable
fun RealTimePriceSlider() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var priceText by remember { mutableStateOf("LGC/BNB: Fetching... • 5 LGC Signup Bonus! • 5 LGC Per Referral! • Join the Ecosystem! • ") }
    val client = OkHttpClient()

    LaunchedEffect(Unit) {
        while(true) {
            try {
                val request = Request.Builder()
                    .url("https://api.dexscreener.com/latest/dex/pairs/bsc/0x377aa7fdde1074acef347c9d720c7273d3b1b56c")
                    .build()
                
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body.string()
                        val data = Gson().fromJson(body, Map::class.java)
                        val pair = (data["pair"] as? Map<*, *>)
                        val priceUsd = pair?.get("priceUsd") as? String
                        val priceChange = (pair?.get("priceChange") as? Map<*, *>)?.get("h24")?.toString()

                        if (priceUsd != null) {
                            val changeText = if (priceChange != null) " ($priceChange%)" else ""
                            priceText = "LGC/BNB: $$priceUsd$changeText • 5 LGC Signup Bonus! • 5 LGC Per Referral! • Join the Ecosystem! • "
                        }
                    }
                }
            } catch (_: Exception) {
            }
            delay(60000) 
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            scrollState.animateScrollTo(scrollState.maxValue, tween(30000, easing = LinearEasing))
            scrollState.scrollTo(0)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF050C1A))
            .padding(vertical = 6.dp)
            .horizontalScroll(scrollState, false)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, "https://www.dextools.io/app/bnb/pair-explorer/0x377aa7fdde1074acef347c9d720c7273d3b1b56c".toUri())
                context.startActivity(intent)
            }
    ) {
        repeat(10) {
            Text(text = priceText, color = LgcGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}

fun formatTime(ms: Long): String {
    val h = ms / 3600000; val m = (ms / 60000) % 60; val s = (ms / 1000) % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

fun isYesterday(last: Long, now: Long): Boolean {
    val diff = now - last
    return diff in (24 * 3600000)..(48 * 3600000)
}

fun handleReferral(firebaseUser: com.google.firebase.auth.FirebaseUser?, uid: String, inviteCode: String, email: String) {
    val db = FirebaseFirestore.getInstance()
    val username = firebaseUser?.displayName ?: email.split("@")[0]

    // --- 🛡️ ROBUST EMAIL VERIFICATION ---
    firebaseUser?.sendEmailVerification()?.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            Log.d("AUTH", "Verification email sent successfully to $email")
        } else {
            Log.e("AUTH", "Failed to send verification email", task.exception)
        }
    }

    val data = hashMapOf(
        "email" to email,
        "username" to username,
        "balance" to 0.0, // No immediate bonus
        "totalRewards" to 0.0,
        "rewardLevel" to 1,
        "referrals" to 0L,
        "totalTaps" to 0L,
        "lastEarningStart" to null,
        "streakCount" to 1L,
        "lastLoginDate" to Timestamp.now(),
        "joinDate" to Timestamp.now(),
        "kycStatus" to "unverified",
        "completedTasks" to emptyList<String>(),
        "inviteCode" to uid.take(8).uppercase(),
        "adTapCount" to 0L,
        "referredBy" to "",
        "bonusPending" to true // Flag to give bonus after email verification
    )
    
    if (inviteCode.isNotBlank() && inviteCode.length >= 8) {
        db.collection("users").whereEqualTo("inviteCode", inviteCode).get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                val refDoc = snapshot.documents[0]
                data["referredBy"] = refDoc.id
                db.collection("users").document(uid).set(data, SetOptions.merge())
            } else {
                db.collection("users").document(uid).set(data, SetOptions.merge())
            }
        }.addOnFailureListener {
            db.collection("users").document(uid).set(data, SetOptions.merge())
        }
    } else {
        db.collection("users").document(uid).set(data, SetOptions.merge())
    }
}

data class EmojiState(val char: String, val x: Float, val y: Float, val startTime: Long)

fun isGalaActive(): Boolean = true

const val COOKIES_TEXT = """
Cookies and Local Storage Policy

LaughCoin uses essential local storage and session tokens to function:

1. Authentication Tokens: Required to maintain your secure login session.
2. App Preferences: To remember your theme and settings.
3. Security Cookies: To detect and prevent fraudulent activities.

By using this app, you consent to the use of these essential data pieces. We do not use tracking cookies for cross-site advertising.
"""
