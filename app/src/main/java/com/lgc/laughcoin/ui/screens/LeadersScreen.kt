package com.lgc.laughcoin.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lgc.laughcoin.ui.theme.*
import java.util.*

@Composable
fun LeadersScreen() {
    val db = FirebaseFirestore.getInstance()
    var topUsers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var topReferrers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) } 
    var debugStatus by remember { mutableStateOf("Initializing...") }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(selectedTab) {
        isLoading = true
        debugStatus = "Fetching from Firestore..."
        val query = if (selectedTab == 0) {
            db.collection("users").orderBy("balance", Query.Direction.DESCENDING)
        } else {
            db.collection("users").orderBy("referrals", Query.Direction.DESCENDING)
        }
        
        query.limit(20).get()
            .addOnSuccessListener { result ->
                val data = result.documents.mapNotNull { it.data }
                if (selectedTab == 0) topUsers = data else topReferrers = data
                isLoading = false
                debugStatus = "Success: Found ${data.size} users"
            }
            .addOnFailureListener { e -> 
                Log.e("Firestore", "Error fetching leaderboard", e)
                isLoading = false 
                debugStatus = "Error: ${e.localizedMessage}"
                android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(CyberDark).padding(16.dp)
    ) {
        Text("GLOBAL RANKING", color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("Status: $debugStatus", color = Color.White.copy(0.5f), fontSize = 10.sp)
        
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("Top Balance", selectedTab == 0) { selectedTab = 0 }
            TabButton("Top Referrers", selectedTab == 1) { selectedTab = 1 }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = LgcGold)
            }
        } else {
            val list = if (selectedTab == 0) topUsers else topReferrers
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No rankings available yet", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(list) { index, user ->
                        LeaderboardItem(index + 1, user)
                    }
                }
            }
        }
    }
}


@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) LgcGold else CyberBlue,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(text, color = if (isSelected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun LeaderboardItem(rank: Int, userData: Map<String, Any>) {
    val balance = (userData["balance"] as? Number)?.toDouble() ?: 0.0
    val refs = (userData["referrals"] as? Number)?.toLong() ?: 0L
    val email = userData["email"] as? String ?: "Anonymous"
    val username = userData["username"] as? String ?: email.split("@").getOrNull(0) ?: "User"
    val level = (userData["rewardLevel"] as? Number)?.toInt() ?: 1
    val inviteCode = userData["inviteCode"] as? String ?: "—"

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(CyberBlue.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, if(rank <= 3) LgcGold.copy(0.4f) else Color.White.copy(0.05f))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(35.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = when(rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "#$rank"
                    },
                    color = if(rank <= 3) LgcGold else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = if(rank <= 3) 20.sp else 12.sp
                )
            }
            
            Spacer(Modifier.width(8.dp))
            
            Column {
                Text(username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Code: $inviteCode • Refs: $refs • Level $level", color = CyberGreen, fontSize = 10.sp)
            }
            
            Spacer(Modifier.weight(1f))
            
            Column(horizontalAlignment = Alignment.End) {
                Text(String.format(Locale.US, "%.2f", balance), color = LgcGold, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("LGC", color = LgcGold, fontSize = 9.sp)
            }
        }
    }
}
