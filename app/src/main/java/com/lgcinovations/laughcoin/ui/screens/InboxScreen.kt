package com.lgcinovations.laughcoin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.lgcinovations.laughcoin.ui.theme.*

@Composable
fun InboxScreen() {
    val db = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        db.collection("users")
            .orderBy("lastLoginDate", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { result ->
                users = result.documents.mapNotNull { it.data }
            }
    }

    Column(Modifier.fillMaxSize().background(CyberDark).padding(16.dp)) {
        Text("NOTIFICATIONS", color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        
        if (users.isEmpty()) {
            @Suppress("DEPRECATION")
            Text("No new activities.", color = Color.Gray)
        } else {
            LazyColumn {
                items(users.size) { index ->
                    val user = users[index]
                    val email = user["email"] as? String ?: "Unknown"
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(CyberBlue.copy(0.5f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📩", fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("New Login/Signup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(email, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
