package com.example.maccproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    // Firebase Auth Instance
    private lateinit var auth: FirebaseAuth

    // UI Elements
    private lateinit var btnSignIn: SignInButton
    private lateinit var btnLogout: Button
    private lateinit var btnShowLogs: Button
    private lateinit var btnArm: Button
    private lateinit var tvStatus: TextView

    // Permissions
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = Firebase.auth

        // Link UI
        btnSignIn = findViewById(R.id.btnSignIn)
        btnLogout = findViewById(R.id.btnLogout)
        btnShowLogs = findViewById(R.id.btnShowLogs)
        btnArm = findViewById(R.id.btnArmSystem)
        tvStatus = findViewById(R.id.tvStatus)

        // GOOGLE SIGN IN CLIENT
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        // BUTTON LISTENERS

        btnSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }

        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        btnShowLogs.setOnClickListener {
            startActivity(Intent(this, UserDashboardActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Arm System Click
        btnArm.setOnClickListener {
            if (checkPermissions()) {
                // Launch the Monitoring Activity
                val intent = Intent(this, MonitoringActivity::class.java)
                startActivity(intent)
            } else {
                // Ask for permissions
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 100)
            }
        }


        updateUI(auth.currentUser)
    }

    // GOOGLE SIGN IN LOGIC

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    updateUI(auth.currentUser)
                } else {
                    Toast.makeText(this, "Auth Failed", Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }
    }

    // LOGOUT LOGIC

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Yes") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        // Sign out from Firebase
        auth.signOut()

        // Sign out from Google Client
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            updateUI(null)
        }
    }


    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // LOGGED IN
            tvStatus.text = "Welcome, ${user.displayName}\nSystem Ready."

            tvStatus.setTextColor(getColor(R.color.neon_green))
            btnSignIn.visibility = View.GONE
            btnLogout.visibility = View.VISIBLE
            btnShowLogs.visibility = View.VISIBLE

            btnArm.isEnabled = true
        } else {
            // LOGGED OUT
            tvStatus.text = "Status: PLEASE SIGN IN"

            tvStatus.setTextColor(getColor(R.color.neon_red))
            btnSignIn.visibility = View.VISIBLE
            btnLogout.visibility = View.GONE
            btnShowLogs.visibility = View.GONE

            btnArm.isEnabled = false
        }
    }

    // PERMISSION LOGIC

    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}