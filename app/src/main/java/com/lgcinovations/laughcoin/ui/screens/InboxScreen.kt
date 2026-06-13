package com.lgcinovations.laughcoin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lgcinovations.laughcoin.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

data class NotificationItem(
    val id: String = "",
    val type: String = "", // "tip", "reward", "withdrawal", "gift", "battle_gift"
    val amount: Double = 0.0,
    val message: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false
)

@Composable
fun InboxScreen() {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, _ ->
                    notifications = snapshot?.documents?.mapNotNull { doc ->
                        NotificationItem(
                            id = doc.id,
                            type = doc.getString("type") ?: "",
                            amount = (doc.getDouble("amount") ?: doc.getLong("amount")?.toDouble() ?: 0.0),
                            message = doc.getString("message") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            read = doc.getBoolean("read") ?: false
                        )
                    } ?: emptyList()
                }
        }
    }

    Column(Modifier.fillMaxSize().background(CyberDark).padding(16.dp)) {
        Text("WALLET NOTIFICATIONS", color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No transactions yet.\nYour wallet activity will appear here.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn {
                items(notifications) { notif ->
                    NotificationCard(notif)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun NotificationCard(notif: NotificationItem) {
    val (icon, typeLabel, amountColor) = when (notif.type) {
        "tip" -> Triple("💰", "Tip Received", LgcGold)
        "reward" -> Triple("🎁", "Reward Earned", Color(0xFF00FF00))
        "withdrawal" -> Triple("💳", "Withdrawal", Color(0xFFFF9800))
        "gift" -> Triple("🎀", "Gift Received", Color(0xFFFF69B4))
        "battle_gift" -> Triple("⚔️", "Battle Gift", Color(0xFFFF1493))
        else -> Triple("📬", "Transaction", Color.White)
    }
    
    val timeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
    val timeString = timeFormat.format(Date(notif.timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CyberBlue.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, if (notif.read) Color.Gray else LgcGold)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(typeLabel, color = amountColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(notif.message, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                    Text(timeString, color = Color.DarkGray, fontSize = 10.sp)
                }
            }
            Text(
                "+${String.format("%.2f", notif.amount)} LGC",
                color = amountColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
