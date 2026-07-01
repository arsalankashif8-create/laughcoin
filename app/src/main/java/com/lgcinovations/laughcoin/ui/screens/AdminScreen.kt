package com.lgcinovations.laughcoin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.lgcinovations.laughcoin.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Same Web API key already embedded in index.html - Firebase web API keys are not
// secrets (access is governed by Firebase Auth + security rules), so reusing it here
// keeps user-creation behavior identical between the web admin panel and this screen.
private const val FIREBASE_WEB_API_KEY = "AIzaSyC2cHfvRBU6B2RSPQ2yvDfnhC4ysyvZmlc"

private data class AdminStats(
    val totalUsers: Int,
    val userCapped: Boolean,
    val pendingWithdrawals: Int,
    val totalBalance: Double,
    val pendingKyc: Int
)

private data class WithdrawalRow(
    val id: String,
    val amount: Double,
    val address: String,
    val uid: String,
    val ts: Long
)

@Composable
fun AdminScreen() {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val httpClient = remember { OkHttpClient() }

    var pendingKyc by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var statsLoading by remember { mutableStateOf(false) }
    var withdrawals by remember { mutableStateOf<List<WithdrawalRow>>(emptyList()) }

    fun loadStats() {
        statsLoading = true
        db.collection("users").limit(1000).get().addOnSuccessListener { usersSnap ->
            var totalBalance = 0.0
            usersSnap.documents.forEach { totalBalance += (it.getDouble("balance") ?: 0.0) }
            db.collection("withdrawals").whereEqualTo("status", "pending").limit(100).get()
                .addOnSuccessListener { wdSnap ->
                    db.collection("kyc").whereEqualTo("status", "pending").limit(100).get()
                        .addOnSuccessListener { kycSnap ->
                            stats = AdminStats(
                                totalUsers = usersSnap.size(),
                                userCapped = usersSnap.size() >= 1000,
                                pendingWithdrawals = wdSnap.size(),
                                totalBalance = totalBalance,
                                pendingKyc = kycSnap.size()
                            )
                            statsLoading = false
                        }
                        .addOnFailureListener { statsLoading = false }
                }
                .addOnFailureListener { statsLoading = false }
        }.addOnFailureListener { statsLoading = false }
    }

    LaunchedEffect(Unit) {
        loadStats()

        db.collection("kyc")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                pendingKyc = snap?.documents?.map { it.id to it.data!! } ?: emptyList()
            }

        db.collection("withdrawals")
            .whereEqualTo("status", "pending")
            .limit(30)
            .addSnapshotListener { snap, _ ->
                withdrawals = snap?.documents?.map { d ->
                    WithdrawalRow(
                        id = d.id,
                        amount = d.getDouble("amount") ?: 0.0,
                        address = d.getString("address") ?: "No Address",
                        uid = d.getString("uid") ?: "",
                        ts = d.getLong("ts") ?: 0L
                    )
                }?.sortedByDescending { it.ts } ?: emptyList()
            }
    }

    Column(Modifier.fillMaxSize().background(CyberDark).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("ADMIN DASHBOARD", color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Black)

        Spacer(Modifier.height(24.dp))

        // --- STATS DASHBOARD ---
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📊 OVERVIEW", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { loadStats() }, enabled = !statsLoading) {
                Text(if (statsLoading) "Refreshing…" else "Refresh", color = CyberGreen, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        stats?.let { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("${s.totalUsers}${if (s.userCapped) "+" else ""}", "USERS", Modifier.weight(1f))
                StatCard("${s.pendingWithdrawals}", "PENDING W/D", Modifier.weight(1f), CyberGreen)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("${"%.0f".format(s.totalBalance)}${if (s.userCapped) "+" else ""}", "LGC ISSUED (SAMPLE)", Modifier.weight(1f))
                StatCard("${s.pendingKyc}", "PENDING KYC", Modifier.weight(1f), LgcGold)
            }
        } ?: Text(
            if (statsLoading) "Fetching latest stats…" else "No stats loaded.",
            color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(Modifier.height(32.dp))

        // --- BROADCAST ---
        Text("📢 BROADCAST NOTIFICATION", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        var msgTitle by remember { mutableStateOf("") }
        var msgBody by remember { mutableStateOf("") }
        var isSendingBroadcast by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = msgTitle, onValueChange = { msgTitle = it },
            label = { Text("Notification Title") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = msgBody, onValueChange = { msgBody = it },
            label = { Text("Message Body") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Button(
            onClick = {
                if (msgTitle.isNotBlank() && msgBody.isNotBlank()) {
                    isSendingBroadcast = true
                    FirebaseFunctions.getInstance("asia-southeast1")
                        .getHttpsCallable("sendBroadcast")
                        .call(hashMapOf("title" to msgTitle.trim(), "body" to msgBody.trim()))
                        .addOnSuccessListener {
                            msgTitle = ""; msgBody = ""; isSendingBroadcast = false
                            Toast.makeText(context, "Sent! 🚀", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            isSendingBroadcast = false
                            Toast.makeText(context, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(LgcGold),
            enabled = !isSendingBroadcast
        ) {
            if (isSendingBroadcast) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            else Text("SEND BROADCAST 🚀", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))

        // --- PENDING WITHDRAWALS ---
        Text("💸 PENDING WITHDRAWALS", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (withdrawals.isEmpty()) {
            Text("No pending withdrawals.", color = Color.Gray, fontSize = 13.sp)
        } else {
            withdrawals.forEach { w ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(CyberBlue),
                    border = BorderStroke(1.dp, LgcGold.copy(0.3f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${"%.2f".format(w.amount)} LGC", color = LgcGold, fontWeight = FontWeight.Bold)
                        }
                        Text(w.address, color = Color.White, fontSize = 12.sp)
                        Text("UID: ${w.uid}", color = Color.Gray, fontSize = 11.sp)
                        Button(
                            onClick = {
                                db.collection("withdrawals").document(w.id)
                                    .update(mapOf("status" to "completed", "completedAt" to System.currentTimeMillis()))
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Marked completed ✅", Toast.LENGTH_SHORT).show()
                                        loadStats()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(CyberGreen)
                        ) { Text("MARK AS COMPLETED", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // --- USER MANAGEMENT (ADJUST BALANCE / DELETE) ---
        Text("👤 USER MANAGEMENT", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        var searchQuery by remember { mutableStateOf("") }
        var foundUser by remember { mutableStateOf<Pair<String, Map<String, Any>>?>(null) }
        var searchError by remember { mutableStateOf<String?>(null) }

        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            label = { Text("Search by Email or UID") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        searchError?.let { Text(it, color = StopRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        Button(onClick = {
            searchError = null
            val query = searchQuery.trim()
            if (query.isEmpty()) { searchError = "Enter an email or UID"; return@Button }

            // Try as UID first, same as the web admin panel
            db.collection("users").document(query).get().addOnSuccessListener { byUid ->
                if (byUid.exists()) {
                    foundUser = byUid.id to byUid.data!!
                } else {
                    db.collection("users").whereEqualTo("email", query).limit(1).get()
                        .addOnSuccessListener { byEmail ->
                            if (!byEmail.isEmpty) {
                                val doc = byEmail.documents[0]
                                foundUser = doc.id to doc.data!!
                            } else {
                                searchError = "No user found with that email or UID"
                            }
                        }
                        .addOnFailureListener { searchError = "Error searching: ${it.message}" }
                }
            }.addOnFailureListener { searchError = "Error searching: ${it.message}" }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(CyberGreen)) {
            Text("FIND USER", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        foundUser?.let { (uid, data) ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(CyberBlue), border = BorderStroke(1.dp, LgcGold)) {
                Column(Modifier.padding(16.dp)) {
                    Text("User: ${data["username"] ?: data["email"]}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Balance: ${data["balance"]} LGC", color = LgcGold)
                    Text("KYC: ${data["kycStatus"] ?: "—"}", color = Color.Gray, fontSize = 12.sp)

                    var amountToAdjust by remember { mutableStateOf("") }
                    var adjustMode by remember { mutableStateOf("add") } // add | set | subtract

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("add" to "ADD", "set" to "SET", "subtract" to "DEDUCT").forEach { (mode, label) ->
                            val selected = adjustMode == mode
                            OutlinedButton(
                                onClick = { adjustMode = mode },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) LgcGold.copy(0.15f) else Color.Transparent,
                                    contentColor = if (selected) LgcGold else Color.Gray
                                )
                            ) { Text(label, fontSize = 11.sp) }
                        }
                    }

                    OutlinedTextField(
                        value = amountToAdjust, onValueChange = { amountToAdjust = it },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Button(onClick = {
                        val amt = amountToAdjust.toDoubleOrNull() ?: 0.0
                        val currentBal = (data["balance"] as? Number)?.toDouble() ?: 0.0
                        val update: Any = when (adjustMode) {
                            "set" -> mapOf("balance" to amt)
                            "subtract" -> mapOf("balance" to FieldValue.increment(-amt))
                            else -> mapOf("balance" to FieldValue.increment(amt))
                        }
                        @Suppress("UNCHECKED_CAST")
                        db.collection("users").document(uid).update(update as Map<String, Any>)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Balance updated!", Toast.LENGTH_SHORT).show()
                                foundUser = null
                                loadStats()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(LgcGold)) {
                        Text("APPLY", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(onClick = {
                        db.collection("users").document(uid).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Deleted!", Toast.LENGTH_SHORT).show()
                                foundUser = null
                                loadStats()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(Color.Gray)) {
                        Text("DELETE USER", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // --- CREATE / TOP-UP USER ---
        Text("➕ CREATE / TOP-UP USER", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        var newUsername by remember { mutableStateOf("") }
        var newEmail by remember { mutableStateOf("") }
        var newBalance by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var creatingUser by remember { mutableStateOf(false) }
        var createError by remember { mutableStateOf<String?>(null) }
        var createSuccess by remember { mutableStateOf<String?>(null) }

        OutlinedTextField(
            value = newUsername, onValueChange = { newUsername = it },
            label = { Text("Username") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newEmail, onValueChange = { newEmail = it },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newBalance, onValueChange = { newBalance = it },
            label = { Text("Starting Balance") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword, onValueChange = { newPassword = it },
            label = { Text("Password (min 6 chars)") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        createError?.let { Text(it, color = StopRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        createSuccess?.let { Text(it, color = CyberGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }

        Button(
            onClick = {
                createError = null; createSuccess = null
                val username = newUsername.trim()
                val email = newEmail.trim()
                val balance = newBalance.toDoubleOrNull() ?: 0.0
                val password = newPassword.trim()

                if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    createError = "Please fill in username, email and password"; return@Button
                }
                if (password.length < 6) {
                    createError = "Password must be at least 6 characters"; return@Button
                }

                creatingUser = true
                scope.launch {
                    try {
                        // 1. Check if a Firestore user doc already exists for this email — top up instead.
                        val existing = db.collection("users").whereEqualTo("email", email).limit(1).get().let { task ->
                            withContext(Dispatchers.IO) {
                                com.google.android.gms.tasks.Tasks.await(task)
                            }
                        }
                        if (!existing.isEmpty) {
                            val doc = existing.documents[0]
                            val curBal = (doc.getDouble("balance") ?: 0.0)
                            db.collection("users").document(doc.id)
                                .update(mapOf("balance" to FieldValue.increment(balance)))
                                .addOnSuccessListener {
                                    createSuccess = "User already exists — added $balance LGC. New balance: ${"%.2f".format(curBal + balance)} LGC"
                                    creatingUser = false
                                    loadStats()
                                }
                                .addOnFailureListener {
                                    createError = "Failed to top up: ${it.message}"
                                    creatingUser = false
                                }
                            return@launch
                        }

                        // 2. Create a Firebase Auth account via the Identity Toolkit REST API,
                        // same approach as the web admin panel, so we don't sign the current
                        // admin session out (which createUserWithEmailAndPassword would do).
                        val bodyJson = JSONObject().apply {
                            put("email", email)
                            put("password", password)
                            put("displayName", username)
                            put("returnSecureToken", false)
                        }
                        val body = bodyJson.toString().toRequestBody("application/json".toMediaType())
                        val request = Request.Builder()
                            .url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$FIREBASE_WEB_API_KEY")
                            .post(body)
                            .build()

                        val responseBody = withContext(Dispatchers.IO) {
                            httpClient.newCall(request).execute().use { it.body?.string() ?: "{}" }
                        }
                        val result = JSONObject(responseBody)

                        if (result.has("error")) {
                            val message = result.getJSONObject("error").optString("message", "Failed to create account")
                            createError = if (message == "EMAIL_EXISTS")
                                "Auth account exists but no Firestore doc. Ask the user to log in — their doc will be created automatically."
                            else "Error: $message"
                            creatingUser = false
                            return@launch
                        }

                        val uid = result.getString("localId")
                        val now = System.currentTimeMillis()
                        val inviteCode = uid.take(8).uppercase()

                        val newDoc = hashMapOf<String, Any>(
                            "username" to username,
                            "email" to email,
                            "inviteCode" to inviteCode,
                            "balance" to balance,
                            "totalRewards" to balance,
                            "rewardLevel" to 1,
                            "referralCount" to 0,
                            "referrals" to 0,
                            "totalRuns" to 0,
                            "upgrades" to emptyMap<String, Any>(),
                            "kycStatus" to "unverified",
                            "recentCompute" to listOf(mapOf("label" to "Admin: Data recovery balance", "amount" to balance, "ts" to now)),
                            "transactions" to listOf(mapOf("label" to "Admin: Data recovery balance", "amount" to balance, "ts" to now)),
                            "lastDaily" to 0,
                            "createdAt" to now,
                            "lastSeen" to now,
                            "createdByAdmin" to true
                        )

                        db.collection("users").document(uid).set(newDoc)
                            .addOnSuccessListener {
                                createSuccess = "User \"$username\" created! UID: $uid | Balance: $balance LGC | Login: $email"
                                newUsername = ""; newEmail = ""; newBalance = ""; newPassword = ""
                                creatingUser = false
                                loadStats()
                            }
                            .addOnFailureListener {
                                createError = "Auth account created but Firestore doc failed: ${it.message}"
                                creatingUser = false
                            }
                    } catch (e: Exception) {
                        createError = "Error: ${e.message}"
                        creatingUser = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(CyberGreen),
            enabled = !creatingUser
        ) {
            if (creatingUser) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            else Text("CREATE / TOP-UP USER", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))

        // --- KYC APPROVAL ---
        Text("🆔 PENDING KYC", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (pendingKyc.isEmpty()) {
            Text("No pending KYC.", color = Color.Gray)
        } else {
            pendingKyc.forEach { (docId, data) ->
                val email = data["email"] as? String ?: "No Email"
                val userId = data["uid"] as? String ?: ""
                val fullname = data["fullname"] as? String ?: "No Name"
                val photoId = data["photoId"] as? String ?: data["photoFront"] as? String ?: ""
                val photoSelfie = data["photoSelfie"] as? String ?: ""

                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(CyberBlue), border = BorderStroke(1.dp, LgcGold.copy(0.3f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Email: $email", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Name: $fullname", color = Color.White)

                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsyncImage(model = photoId, contentDescription = "ID Front", modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            AsyncImage(model = photoSelfie, contentDescription = "Selfie", modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                db.collection("kyc").document(docId).update("status", "approved")
                                db.collection("users").document(userId).update("kycStatus", "approved")
                                Toast.makeText(context, "Verified!", Toast.LENGTH_SHORT).show()
                                loadStats()
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(CyberGreen)) { Text("VERIFY", color = Color.Black, fontWeight = FontWeight.Bold) }

                            Button(onClick = {
                                db.collection("kyc").document(docId).update("status", "rejected")
                                db.collection("users").document(userId).update("kycStatus", "rejected")
                                Toast.makeText(context, "Rejected", Toast.LENGTH_SHORT).show()
                                loadStats()
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(StopRed)) { Text("REJECT", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = LgcGold) {
    Card(modifier, colors = CardDefaults.cardColors(CyberBlue), border = BorderStroke(1.dp, LgcGold.copy(0.15f))) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.Gray, fontSize = 10.sp)
        }
    }
}
