package com.lgc.laughcoin.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.lgc.laughcoin.ui.theme.*

@Composable
fun KYCScreen() {
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    var idFrontUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    var selfieUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE")
    var idFrontUrl by rememberSaveable { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE")
    var selfieUrl by rememberSaveable { mutableStateOf("") }
    
    var fullname by remember { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE")
    var idType by remember { mutableStateOf("National ID") }
    var idNum by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var walletAddress by remember { mutableStateOf("") }
    
    var isUploading by remember { mutableStateOf(false) }
    var kycStatus by remember { mutableStateOf("unverified") }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).addSnapshotListener { snap, _ ->
                kycStatus = snap?.getString("kycStatus") ?: "unverified"
            }
        }
    }

    val idLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploading = true
            val ref = storage.reference.child("kyc/$uid/id_front.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    idFrontUrl = downloadUri.toString()
                    idFrontUriStr = "done"
                    isUploading = false
                }
            }.addOnFailureListener {
                isUploading = false
                Toast.makeText(context, "Upload Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val idCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.let { intent ->
                IntentCompat.getParcelableExtra(intent, "data", Bitmap::class.java)
            }
            if (bitmap != null) {
                isUploading = true
                val bytes = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
                val data = bytes.toByteArray()
                val ref = storage.reference.child("kyc/$uid/id_front.jpg")
                ref.putBytes(data).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        idFrontUrl = downloadUri.toString()
                        idFrontUriStr = "done"
                        isUploading = false
                    }
                }.addOnFailureListener {
                    isUploading = false
                    Toast.makeText(context, "Upload Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val selfieLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploading = true
            val ref = storage.reference.child("kyc/$uid/selfie.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    selfieUrl = downloadUri.toString()
                    selfieUriStr = "done"
                    isUploading = false
                }
            }.addOnFailureListener {
                isUploading = false
                Toast.makeText(context, "Upload Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val selfieCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.let { intent ->
                IntentCompat.getParcelableExtra(intent, "data", Bitmap::class.java)
            }
            if (bitmap != null) {
                isUploading = true
                val bytes = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
                val data = bytes.toByteArray()
                val ref = storage.reference.child("kyc/$uid/selfie.jpg")
                ref.putBytes(data).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        selfieUrl = downloadUri.toString()
                        selfieUriStr = "done"
                        isUploading = false
                    }
                }.addOnFailureListener {
                    isUploading = false
                    Toast.makeText(context, "Upload Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(CyberDark).padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("IDENTITY VERIFICATION", color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(32.dp))

        when (kycStatus) {
            "pending" -> {
                Text("Reviewing documents...", color = Color.White)
            }
            "verified" -> {
                Text("✅ Verified!", color = CyberGreen, fontWeight = FontWeight.Bold)
            }
            else -> {
                OutlinedTextField(
                    value = fullname, onValueChange = { fullname = it }, 
                    label = { Text("Full Legal Name") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = idNum, onValueChange = { idNum = it }, 
                    label = { Text("ID Number") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dob, onValueChange = { dob = it }, 
                    label = { Text("Date of Birth (DD/MM/YYYY)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = country, onValueChange = { country = it }, 
                    label = { Text("Country") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = walletAddress, onValueChange = { walletAddress = it }, 
                    label = { Text("BEP-20 Wallet Address") }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(16.dp))

                if (isUploading) {
                    CircularProgressIndicator(color = LgcGold, modifier = Modifier.padding(16.dp))
                }

                var showIdPicker by remember { mutableStateOf(false) }
                KYCUploadBox("National ID (Front)", idFrontUriStr != null) { 
                    showIdPicker = true
                }
                
                if (showIdPicker) {
                    AlertDialog(
                        onDismissRequest = { showIdPicker = false },
                        title = { Text("Choose ID Photo Source") },
                        confirmButton = {
                            Button(onClick = { 
                                idCameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                                showIdPicker = false 
                            }) { Text("Camera") }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                idLauncher.launch("image/*")
                                showIdPicker = false 
                            }) { Text("Gallery") }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                var showSelfiePicker by remember { mutableStateOf(false) }
                KYCUploadBox("Selfie with ID", selfieUriStr != null) { 
                    showSelfiePicker = true
                }

                if (showSelfiePicker) {
                    AlertDialog(
                        onDismissRequest = { showSelfiePicker = false },
                        title = { Text("Choose Selfie Source") },
                        confirmButton = {
                            Button(onClick = { 
                                selfieCameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                                showSelfiePicker = false 
                            }) { Text("Camera") }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                selfieLauncher.launch("image/*")
                                showSelfiePicker = false 
                            }) { Text("Gallery") }
                        }
                    )
                }

                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        val kycData = hashMapOf(
                            "uid" to uid,
                            "email" to auth.currentUser?.email,
                            "fullname" to fullname,
                            "idtype" to "National ID",
                            "idnum" to idNum,
                            "dob" to dob,
                            "country" to country,
                            "wallet" to walletAddress,
                            "status" to "pending",
                            "timestamp" to Timestamp.now()
                        )
                        db.collection("kyc").document(uid).set(kycData)
                        db.collection("users").document(uid).update("kycStatus", "pending")
                        Toast.makeText(context, "KYC Submitted!", Toast.LENGTH_LONG).show()
                    }, 
                    modifier = Modifier.fillMaxWidth(), 
                    enabled = idFrontUriStr != null && selfieUriStr != null && !isUploading && fullname.isNotBlank() && idNum.isNotBlank()
                ) {
                    Text("SUBMIT FOR REVIEW")
                }
            }
        }
    }
}


@Composable
fun KYCUploadBox(label: String, isDone: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().height(100.dp).clickable { onClick() }, color = CyberBlue, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if(isDone) CyberGreen else Color.Gray)) {
        Box(contentAlignment = Alignment.Center) {
            Text(if(isDone) "✅ $label" else "📷 Tap to Upload $label", color = if(isDone) CyberGreen else Color.Gray)
        }
    }
}
