package com.lgc.laughcoin.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lgc.laughcoin.ui.theme.*
import java.util.*

@Composable
fun WalletScreen() {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current
    
    var balance by remember { mutableDoubleStateOf(0.0) }
    var walletAddress by remember { mutableStateOf("") }
    var kycStatus by remember { mutableStateOf("unverified") }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    balance = snap.getDouble("balance") ?: 0.0
                    kycStatus = snap.getString("kycStatus") ?: "unverified"
                }
            }
        }
    }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
    )

    Column(
        modifier = Modifier.fillMaxSize().background(gradientBg).padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MY WALLET 💰", color = LgcGold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your Giggles are worth Gold!", color = Color.Cyan, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color(0xFF0D1B2A).copy(0.8f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, Brush.linearGradient(listOf(LgcGold, CyberGreen)))
        ) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOTAL LAUGH BALANCE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(String.format(Locale.US, "%.2f LGC", balance), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Surface(color = CyberGreen.copy(0.2f), shape = RoundedCornerShape(8.dp)) {
                    Text("Verified & Ready", color = CyberGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color.Black.copy(0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("WITHDRAW LGC", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Gas Fee: 0.005 BNB to 0xc5c...BDE8B (Tap to copy)", 
                    color = Color.LightGray, 
                    fontSize = 10.sp, 
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("LGC Fee Address", "0xc5c73D012375dE05cE2C3f8c7D89512d4f1BDE8B"))
                        Toast.makeText(context, "Fee Address Copied!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        
        if (kycStatus != "verified") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(StopRed.copy(0.1f)),
                border = BorderStroke(1.dp, StopRed.copy(0.5f))
            ) {
                Text(
                    "⚠️ KYC Tier 2 Required. Go to Profile > Verify Identity.",
                    color = StopRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = walletAddress,
            onValueChange = { walletAddress = it },
            label = { Text("BEP-20 Wallet Address", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, 
                unfocusedTextColor = Color.White,
                focusedBorderColor = LgcGold,
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                /* DISABLED FOR PRE-LISTING PHASE */
                Toast.makeText(context, "Withdrawals will start after listing to main exchanges!", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
            enabled = true,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("WITHDRAWALS STARTING SOON 🔒", color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Text(
            "Note: LGC Withdrawals will be enabled globally once the token is listed on major exchanges (Tier 1). Stay tuned for announcements!",
            color = LgcGold,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
            border = BorderStroke(1.dp, Color.White.copy(0.05f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("🪙 TOKEN INFORMATION", color = LgcGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Contract Address:", color = Color.Gray, fontSize = 9.sp)
                Text(
                    "0x86c6e869e5e8c488fd6c940f551ff424cc9de130", 
                    color = Color.White, 
                    fontSize = 10.sp,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("LGC Contract", "0x86c6e869e5e8c488fd6c940f551ff424cc9de130"))
                        Toast.makeText(context, "Contract Copied!", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://www.dextools.io/app/bnb/pair-explorer/0x377aa7fdde1074acef347c9d720c7273d3b1b56c".toUri())
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
        
        Spacer(Modifier.height(32.dp))
        
        Text("TRANSACTION HISTORY", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))
        
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Signup Welcome", color = Color.LightGray, fontSize = 11.sp)
                Text("Just now", color = Color.Gray, fontSize = 9.sp)
            }
            Text("+5.00 LGC", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Community Bonus", color = Color.LightGray, fontSize = 11.sp)
                Text("Just now", color = Color.Gray, fontSize = 9.sp)
            }
            Text("+10.00 LGC", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
