package com.lgc.laughcoin.ui.screens

import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.navigation.NavHostController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lgc.laughcoin.R
import com.lgc.laughcoin.Screen
import com.lgc.laughcoin.handleReferral
import com.lgc.laughcoin.ui.theme.CyberDark
import com.lgc.laughcoin.ui.theme.LgcGold
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(navController: NavHostController, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var hasAgreed by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(CyberDark)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(id = R.drawable.laugh_logo), "", Modifier.size(90.dp).clip(androidx.compose.foundation.shape.CircleShape))
        Text(if(isSignUp) "New Account" else "Digital Rewards Dashboard", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email, 
            onValueChange = { email = it }, 
            label = { Text("Email") }, 
            modifier = if (isSignUp) Modifier.size(0.dp) else Modifier.fillMaxWidth(), 
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        if (!isSignUp) Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, 
            onValueChange = { password = it }, 
            label = { Text("Password") }, 
            modifier = if (isSignUp) Modifier.size(0.dp) else Modifier.fillMaxWidth(), 
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        if (!isSignUp) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = {
                    if (email.isBlank()) {
                        Toast.makeText(context, "Enter your email first", Toast.LENGTH_SHORT).show()
                    } else {
                        auth.sendPasswordResetEmail(email.trim()).addOnSuccessListener {
                            Toast.makeText(context, "Reset link sent to your email", Toast.LENGTH_LONG).show()
                        }.addOnFailureListener {
                            Toast.makeText(context, "Error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Forgot Password?", color = Color(0xFF4DB8FF), fontSize = 12.sp)
                }
            }
        }

        if(isSignUp) {
            Text("To ensure security and instant rewards, only Google Sign-Up is currently available for new accounts.", color = Color.Gray, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = inviteCode, onValueChange = { inviteCode = it }, label = { Text("Invite Code (Optional)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Checkbox(hasAgreed, { hasAgreed = it }, colors = CheckboxDefaults.colors(checkedColor = LgcGold))
            Text("I agree to T&C", color = Color.Gray, fontSize = 11.sp)
        }

        if (!isSignUp) {
            Button(
                onClick = {
                    if (!hasAgreed) {
                        Toast.makeText(context, "Please agree to T&C", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    isLoading = true
                    auth.signInWithEmailAndPassword(email.trim(), password.trim())
                        .addOnSuccessListener { 
                            isLoading = false
                            onLoginSuccess() 
                        }
                        .addOnFailureListener { 
                            isLoading = false
                            Toast.makeText(context, "Login Failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LgcGold),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                else Text("LOGIN", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("OR", color = Color.Gray, fontSize = 12.sp)

        OutlinedButton(
            onClick = {
                if (!hasAgreed) {
                    Toast.makeText(context, "Please agree to T&C", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                isLoading = true
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId("293022314378-0t2j1t24aiqj03gcl166fnsqj2iiff1f.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                scope.launch {
                    try {
                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential
                        
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val idToken = googleIdTokenCredential.idToken
                            if (idToken.isEmpty()) {
                                isLoading = false
                                Toast.makeText(context, "Invalid Google ID Token", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                            auth.signInWithCredential(firebaseCredential)
                                .addOnSuccessListener { result ->
                                    val isNewUser = result.additionalUserInfo?.isNewUser ?: false
                                    if (isNewUser) {
                                        // For Google users, we set bonusPending to true and let handleReferral create the doc.
                                        // The reward logic in MainActivity will pick it up since Google accounts are pre-verified.
                                        handleReferral(result.user, result.user?.uid ?: "", inviteCode.trim(), result.user?.email ?: "")
                                    }
                                    isLoading = false
                                    onLoginSuccess() 
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Log.e("Auth", "Firebase Auth Failure", e)
                                    Toast.makeText(context, "Firebase Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Unexpected credential type: ${credential.type}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        Log.e("Auth", "Google Sign-In Error", e)
                        val errorMsg = if (e.localizedMessage?.contains("canceled", ignoreCase = true) == true) {
                            "Sign-in Canceled"
                        } else if (e.localizedMessage?.contains("no account", ignoreCase = true) == true) {
                            "No Google accounts found"
                        } else {
                            "Google Sign-In failed: ${e.localizedMessage}"
                        }
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.Gray),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            else Text(if(isSignUp) "JOIN WITH GOOGLE" else "Continue with Google", fontWeight = FontWeight.Medium)
        }

        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if(isSignUp) "Already have an account? Login" else "New here? Create Account", color = Color(0xFF4DB8FF))
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("By continuing, you agree to our ", color = Color.Gray, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("Privacy Policy", color = Color(0xFF4DB8FF), fontSize = 10.sp, modifier = Modifier.clickable { navController.navigate(Screen.Privacy.route) })
            Text(" , ", color = Color.Gray, fontSize = 10.sp)
            Text("Cookies", color = Color(0xFF4DB8FF), fontSize = 10.sp, modifier = Modifier.clickable { navController.navigate(Screen.Cookies.route) })
            Text(" & ", color = Color.Gray, fontSize = 10.sp)
            Text("Terms", color = Color(0xFF4DB8FF), fontSize = 10.sp, modifier = Modifier.clickable { navController.navigate(Screen.Terms.route) })
        }
    }
}
