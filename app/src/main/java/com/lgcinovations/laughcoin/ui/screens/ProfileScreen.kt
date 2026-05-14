package com.lgcinovations.laughcoin.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import com.lgcinovations.laughcoin.Screen
import com.lgcinovations.laughcoin.ui.theme.*
import android.Manifest
import androidx.compose.ui.text.style.TextAlign
import java.util.Locale
import java.text.SimpleDateFormat

@Composable
fun ProfileScreen(navController: NavHostController, onLogout: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val storage = FirebaseStorage.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current
    
    var email by remember { mutableStateOf(auth.currentUser?.email ?: "") }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf("") }
    var joinDate by remember { mutableStateOf<Timestamp?>(null) }
    var referrals by remember { mutableLongStateOf(0L) }
    var isEditing by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }

    val profileImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploading = true
            // Use a folder structure to match security rules (profiles/UID/filename)
            val timestamp = System.currentTimeMillis()
            val fileName = "profiles/${uid}/image_$timestamp.jpg"
            val ref = storage.reference.child(fileName)
            
            val uploadTask = ref.putFile(uri)
            uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUrl = task.result.toString()
                    db.collection("users").document(uid).update("profileImageUrl", downloadUrl)
                        .addOnSuccessListener {
                            profileImageUrl = downloadUrl
                            isUploading = false
                            Toast.makeText(context, "Profile Photo Updated!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            isUploading = false
                            Toast.makeText(context, "Failed to update profile data", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    isUploading = false
                    Toast.makeText(context, "Upload Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val profileCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.let { intent ->
                IntentCompat.getParcelableExtra(intent, "data", Bitmap::class.java)
            }
            if (bitmap != null) {
                isUploading = true
                val bytes = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
                val data = bytes.toByteArray()
                val timestamp = System.currentTimeMillis()
                val ref = storage.reference.child("profiles/${uid}/camera_$timestamp.jpg")
                
                val uploadTask = ref.putBytes(data)
                uploadTask.continueWithTask { task ->
                    if (!task.isSuccessful) task.exception?.let { throw it }
                    ref.downloadUrl
                }.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        profileImageUrl = task.result.toString()
                        db.collection("users").document(uid).update("profileImageUrl", profileImageUrl)
                        isUploading = false
                        Toast.makeText(context, "Photo Captured!", Toast.LENGTH_SHORT).show()
                    } else {
                        isUploading = false
                        Toast.makeText(context, "Upload Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Camera failed to return image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).get().addOnSuccessListener { snap ->
                if (snap.exists()) {
                    name = snap.getString("username") ?: snap.getString("name") ?: email.split("@")[0]
                    phone = snap.getString("phone") ?: ""
                    profileImageUrl = snap.getString("profileImageUrl") ?: ""
                    inviteCode = snap.getString("inviteCode") ?: uid.take(8).uppercase()
                    joinDate = snap.getTimestamp("joinDate")
                    referrals = snap.getLong("referrals") ?: 0L
                }
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val formattedDate = joinDate?.toDate()?.let { dateFormatter.format(it) } ?: "N/A"

    Column(Modifier.fillMaxSize().background(CyberDark).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        @Suppress("DEPRECATION")
        Text("Account Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(24.dp))
        
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = CyberBlue, border = BorderStroke(2.dp, LgcGold)) {
                Box(contentAlignment = Alignment.Center) {
                    if (isUploading) CircularProgressIndicator(color = LgcGold)
                    else if (profileImageUrl.isNotEmpty()) {
                        AsyncImage(model = profileImageUrl, contentDescription = "Profile", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else Text("👤", fontSize = 40.sp)
                }
            }
            var showImagePicker by remember { mutableStateOf(false) }
            IconButton(onClick = { showImagePicker = true }, modifier = Modifier.size(32.dp).background(LgcGold, CircleShape)) {
                Text("📸", fontSize = 14.sp)
            }
            
            if (showImagePicker) {
                AlertDialog(
                    onDismissRequest = { showImagePicker = false },
                    title = { Text("Update Photo") },
                    text = { Text("Choose a source for your profile picture.") },
                    confirmButton = {
                        Button(onClick = { 
                            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                            profileCameraLauncher.launch(intent)
                            showImagePicker = false 
                        }) { Text("Camera") }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            profileImageLauncher.launch("image/*")
                            showImagePicker = false 
                        }) { Text("Gallery") }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isEditing) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                db.collection("users").document(uid).update("name", name, "phone", phone).addOnSuccessListener { isEditing = false }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(CyberGreen)) {
                Text("SAVE", color = Color.Black)
            }
        } else {
            Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(email, color = Color.Gray, fontSize = 14.sp)
            
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Joined: $formattedDate", color = LgcGold.copy(0.7f), fontSize = 11.sp)
                Spacer(Modifier.width(12.dp))
                Text("Referrals: $referrals", color = CyberGreen.copy(0.7f), fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth()) { Text("EDIT PROFILE", color = Color.White) }
        }

        Spacer(Modifier.height(24.dp))
        ProfileMenuItem("Notifications", "🔔") { navController.navigate(Screen.Inbox.route) }
        // Admin Access for specified emails
        if (email == "arsalankashif8@gmail.com" || email == "ibraheemarsalan6@gmail.com") {
            ProfileMenuItem("Admin Dashboard", "🛠️") { navController.navigate(Screen.Admin.route) }
        }
        ProfileMenuItem("Verify Identity (KYC)", "🆔") { navController.navigate(Screen.KYC.route) }
        
        Spacer(Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(CyberBlue.copy(0.5f)),
            border = BorderStroke(1.dp, LgcGold.copy(0.3f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("🎁 REFERRAL PROGRAM", color = LgcGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("Get 100 LGC for every friend who joins!", color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Text("YOUR INVITE CODE:", color = Color.White, fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inviteCode, color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val fullLink = "https://laughcoin.online/?ref=$inviteCode"
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LGC Invite Link", fullLink))
                            Toast.makeText(context, "Link Copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(LgcGold),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("COPY LINK", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val locationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                Toast.makeText(context, "Location Access Granted! 📍", Toast.LENGTH_SHORT).show()
            }
        }

        ProfileMenuItem("Location Access (Verify)", "📍") {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        TeamSection(uid)

        Spacer(Modifier.height(24.dp))
        Button(onClick = { FirebaseAuth.getInstance().signOut(); onLogout() }, colors = ButtonDefaults.buttonColors(StopRed), modifier = Modifier.fillMaxWidth()) { Text("LOGOUT") }
        
        TextButton(onClick = { 
            val intent = Intent(Intent.ACTION_VIEW, "https://laughcoin.online".toUri())
            context.startActivity(intent)
        }) {
            Text("Official Website 🌐", color = CyberGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        var showDeleteDialog by remember { mutableStateOf(false) }
        
        // Footer Links
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("About", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.clickable { navController.navigate(Screen.About.route) })
            Text("  •  ", color = Color.Gray, fontSize = 10.sp)
            Text("Privacy", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.clickable { navController.navigate(Screen.Privacy.route) })
            Text("  •  ", color = Color.Gray, fontSize = 10.sp)
            Text("Terms", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.clickable { navController.navigate(Screen.Terms.route) })
            Text("  •  ", color = Color.Gray, fontSize = 10.sp)
            Text("Contact", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.clickable { navController.navigate(Screen.About.route) })
        }

        TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Delete Account", color = Color.Gray, fontSize = 12.sp)
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Account?") },
                text = { Text("This will permanently remove your balance and data. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val user = FirebaseAuth.getInstance().currentUser
                            user?.delete()?.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    db.collection("users").document(uid).delete()
                                    onLogout()
                                } else {
                                    Toast.makeText(context, "Please re-login to delete account", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(StopRed)
                    ) { Text("DELETE") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("CANCEL") }
                },
                containerColor = CyberBlue,
                titleContentColor = StopRed,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
fun ProfileMenuItem(title: String, icon: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }, colors = CardDefaults.cardColors(CyberBlue)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.width(16.dp))
            Text(title, color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun TeamSection(uid: String) {
    val db = FirebaseFirestore.getInstance()
    var teamMembers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").whereEqualTo("referredBy", uid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        teamMembers = snap.documents.map { doc ->
                            mapOf(
                                "email" to (doc.getString("email") ?: "Unknown"),
                                "balance" to (doc.getDouble("balance") ?: 0.0),
                                "username" to (doc.getString("username") ?: doc.getString("name") ?: "User")
                            )
                        }
                    }
                    isLoading = false
                }
        } else {
            isLoading = false
        }
    }
    
    if (isLoading) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LgcGold)
        }
    } else if (teamMembers.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text("👥 MY TEAM (${teamMembers.size})", color = LgcGold, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(CyberBlue.copy(0.3f)),
            border = BorderStroke(1.dp, Color.White.copy(0.05f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Member", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.weight(1.5f))
                    Text("Earnings", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                HorizontalDivider(color = Color.White.copy(0.05f), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                
                teamMembers.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1.5f)) {
                            Text(member["username"].toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(member["email"].toString(), color = Color.Gray, fontSize = 10.sp)
                        }
                        Text(
                            "${String.format(Locale.US, "%.2f", member["balance"])} LGC",
                            color = CyberGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    } else {
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(CyberBlue.copy(0.2f)),
            border = BorderStroke(1.dp, Color.White.copy(0.05f))
        ) {
            Text(
                "Invite friends to grow your team and earn more rewards! 🚀",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
