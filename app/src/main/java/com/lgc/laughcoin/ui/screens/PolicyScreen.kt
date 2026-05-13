package com.lgc.laughcoin.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.lgc.laughcoin.ui.theme.*

@Composable
fun PolicyScreen(title: String, content: String) {
    val context = LocalContext.current
    val url = when(title) {
        "Privacy Policy" -> "https://laughcoin.online/privacy.html"
        "About Us" -> "https://laughcoin.online/about.html"
        "Terms & Conditions" -> "https://laughcoin.online/terms.html"
        else -> "https://laughcoin.online"
    }

    Column(Modifier.fillMaxSize().background(CyberDark).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = LgcGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                context.startActivity(intent)
            }) {
                Text("View Online 🌐", color = Color(0xFF4DB8FF))
            }
        }
        
        if (title == "About Us") {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://laughcoin.online/roadmap.html".toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(CyberGreen)
            ) {
                Text("VIEW PROJECT ROADMAP 🚀", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(content, color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}

const val PRIVACY_TEXT = """
LaughCoin (LGC) Privacy Policy
Last Updated: October 2023

At LaughCoin, we take your privacy seriously. This policy explains how we collect, use, and protect your data in compliance with Google Play Developer policies and global data protection standards (GDPR/CCPA).

1. Data Collection
We collect the following information to provide our services:
- Personal Identifiers: Email address, Name, and Phone Number (for authentication and account management).
- Financial Information: Wallet addresses and transaction history related to LGC.
- Device Information: Basic device logs to prevent fraud and ensure security.

2. Purpose of Processing
Your data is used solely to:
- Authenticate users via Firebase.
- Calculate and distribute cloud rewards.
- Process withdrawal requests.
- Improve app performance and security.

3. Data Sharing and Third Parties
We do not sell your personal data. We use industry-standard third-party services:
- Google Firebase: For secure authentication and database hosting.
- Google AdMob: For serving rewarded advertisements.
These providers are compliant with global privacy standards.

4. Data Safety & Disclosure
In compliance with Google Play Policies, we disclose the following data collection:
- Email/Name: For account functionality.
- Photos (Camera/Gallery): For KYC identity verification and profile customization only.
- Device ID: For fraud prevention and security monitoring.
- Transaction History: For managing your LGC reward balance.

5. Data Retention and Deletion
We store your data as long as your account is active. You may request account deletion at any time via the Profile Settings, which will permanently remove your data from our servers.

5. Security
We use SSL/TLS encryption for all data transmissions and follow best practices to protect your information against unauthorized access.

6. Contact Us
For privacy inquiries, contact support@laughcoin.online or admin@laughcoin.online.
"""

const val COOKIES_TEXT = """
Cookies and Local Storage Policy

LaughCoin uses essential local storage and session tokens to function:

1. Authentication Tokens: Required to maintain your secure login session.
2. App Preferences: To remember your theme and settings.
3. Security Cookies: To detect and prevent fraudulent activities.

By using this app, you consent to the use of these essential data pieces. We do not use tracking cookies for cross-site advertising.
"""

const val ABOUT_TEXT = """
About LaughCoin (LGC)

LaughCoin is a revolutionary community-driven ecosystem designed to gamify the world of digital assets through "Digital Rewards" simulations. 

Our Vision:
To build a sustainable, transparent, and fun digital economy where users are rewarded for their participation and community engagement.

How it Works:
- Distributed Rewards: Users contribute "Earning Power" via the app to earn LGC tokens.
- Fair Launch: LGC is built on the Binance Smart Chain (BSC) with a focus on community ownership.
- Real-Time Market: Track LGC value directly within the app and withdraw your earnings to any BEP-20 compatible wallet.

Tokenomics:
- Name: LaughCoin
- Ticker: LGC
- Network: Binance Smart Chain (BEP-20)
- Total Supply: 1,000,000,000 LGC
- Ecosystem: Built on transparency, security, and community laughs.

Contact & Help:
- Support: support@laughcoin.online
- Admin: admin@laughcoin.online
- Help: help@laughcoin.online

Join the future of digital rewards. Let's earn, laugh, and grow together!
"""

const val TERMS_TEXT = """
LaughCoin (LGC) Terms and Conditions
Last Updated: October 2023

1. Acceptance of Terms
By accessing or using LaughCoin, you agree to be bound by these Terms. If you do not agree, do not use the app.

2. Account Eligibility
You must be at least 18 years old to use this app. You are responsible for maintaining the confidentiality of your account.

3. Digital Rewards
LGC tokens are digital rewards distributed based on community engagement and simulated participation. LGC tokens have no intrinsic value outside the LaughCoin ecosystem unless traded on compatible third-party exchanges.

4. Prohibited Activities
You agree not to:
- Use bots, scripts, or automated tools to earn rewards.
- Create multiple accounts for the same user.
- Attempt to exploit or hack the app's reward system.
Violation of these rules will lead to permanent account suspension and forfeiture of all balance.

5. KYC and Withdrawals
Withdrawals are subject to successful KYC (Know Your Customer) verification. We reserve the right to reject withdrawals if fraudulent activity is detected.

6. Disclaimer of Warranties
LaughCoin is provided "as is" without any warranties. We do not guarantee the value of LGC tokens or the availability of the service at all times.

7. Limitation of Liability
LaughCoin shall not be liable for any financial losses or data loss resulting from the use of the app.

8. Changes to Terms
We reserve the right to modify these terms at any time. Your continued use of the app constitutes acceptance of the new terms.

Contact: support@laughcoin.online
"""
