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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.lgcinovations.laughcoin.ui.theme.*

@Composable
fun AdminScreen() {
    val db = FirebaseFirestore.getInstance()
    var pendingKyc by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        db.collection("kyc")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                pendingKyc = snap?.documents?.map { it.id to it.data!! } ?: emptyList()
            }
    }

    Column(Modifier.fillMaxSize().background(CyberDark).padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("ADMIN DASHBOARD", color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
        
        Spacer(Modifier.height(24.dp))
        
        // --- 1. BROADCAST ---
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
                        .addOnFailureListener { isSendingBroadcast = false }
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

        // --- 2 & 3. USER MANAGEMENT (ADJUST BALANCE / DELETE) ---
        Text("👤 USER MANAGEMENT", color = LgcGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        var searchEmail by remember { mutableStateOf("") }
        var foundUser by remember { mutableStateOf<Pair<String, Map<String, Any>>?>(null) }
        
        OutlinedTextField(
            value = searchEmail, onValueChange = { searchEmail = it },
            label = { Text("Search User Email") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Button(onClick = {
            db.collection("users").whereEqualTo("email", searchEmail.trim()).get().addOnSuccessListener {
                if (!it.isEmpty) {
                    val doc = it.documents[0]
                    foundUser = doc.id to doc.data!!
                } else {
                    Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                }
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(CyberGreen)) {
            Text("FIND USER", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        foundUser?.let { (uid, data) ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(CyberBlue), border = BorderStroke(1.dp, LgcGold)) {
                Column(Modifier.padding(16.dp)) {
                    Text("User: ${data["username"] ?: data["email"]}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Balance: ${data["balance"]} LGC", color = LgcGold)
                    
                    var amountToAdjust by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = amountToAdjust, onValueChange = { amountToAdjust = it },
                        label = { Text("Amount to Add/Deduct") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val amt = amountToAdjust.toDoubleOrNull() ?: 0.0
                            db.collection("users").document(uid).update("balance", FieldValue.increment(amt))
                            Toast.makeText(context, "Updated!", Toast.LENGTH_SHORT).show()
                            foundUser = null
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(CyberGreen)) { Text("ADD", color = Color.Black, fontWeight = FontWeight.Bold) }
                        
                        Button(onClick = {
                            val amt = amountToAdjust.toDoubleOrNull() ?: 0.0
                            db.collection("users").document(uid).update("balance", FieldValue.increment(-amt))
                            Toast.makeText(context, "Updated!", Toast.LENGTH_SHORT).show()
                            foundUser = null
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(StopRed)) { Text("DEDUCT", fontWeight = FontWeight.Bold) }
                    }
                    
                    Button(onClick = {
                        db.collection("users").document(uid).delete()
                        Toast.makeText(context, "Deleted!", Toast.LENGTH_SHORT).show()
                        foundUser = null
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(Color.Gray)) { Text("DELETE USER", fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // --- 4. KYC APPROVAL ---
        @Suppress("DEPRECATION")
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
                                db.collection("users").document(userId).update("kycStatus", "verified")
                                Toast.makeText(context, "Verified!", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(CyberGreen)) { Text("VERIFY", color = Color.Black, fontWeight = FontWeight.Bold) }
                            
                            Button(onClick = {
                                db.collection("kyc").document(docId).update("status", "rejected")
                                db.collection("users").document(userId).update("kycStatus", "rejected")
                                Toast.makeText(context, "Rejected", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(StopRed)) { Text("REJECT", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}
