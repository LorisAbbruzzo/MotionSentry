package com.example.maccproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer // Changed from Ringtone
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.math.abs

class MonitoringActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var cameraHelper: CameraHelper
    private lateinit var audioManager: AudioManager
    private var lightSensor: Sensor? = null

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var container: FrameLayout
    private lateinit var btnRetry: Button

    // State
    private var isArmed = false
    private var isGracePeriod = false
    private var currentLux = 0f

    // NEW: Custom Alarm Player
    private var mediaPlayer: MediaPlayer? = null

    private var sensitivityThreshold = 2.0f
    private var isSilentMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitoring)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()
        //  User Preferences
        val prefs = getSharedPreferences("MotionSentryConfig", Context.MODE_PRIVATE)
        sensitivityThreshold = prefs.getFloat("sensitivity", 2.0f)
        isSilentMode = prefs.getBoolean("silent_mode", false)

        tvStatus = findViewById(R.id.tvUnlockHint)
        container = findViewById(R.id.rootContainer)
        btnRetry = findViewById(R.id.btnRetryUnlock)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        cameraHelper = CameraHelper(this, this)
        cameraHelper.startCamera()
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Prevent Back Button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isArmed) {
                    Toast.makeText(this@MonitoringActivity, "SYSTEM LOCKED!", Toast.LENGTH_SHORT).show()
                } else {
                    finish()
                }
            }
        })

        btnRetry.setOnClickListener { showBiometricPrompt() }

        // CHECK GPS BEFORE STARTING
        if (checkGpsEnabled()) {
            startArmingTimer()
        }
    }

    // NEW GPS CHECK
    private fun checkGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isGpsOn) {
            AlertDialog.Builder(this)
                .setTitle("GPS Required")
                .setMessage("Security System requires GPS to track location. Please enable it.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    finish() // Close this screen so they restart the process correctly
                }
                .setNegativeButton("Cancel") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return false
        }
        return true
    }

    private fun startArmingTimer() {
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvStatus.text = "Arming in ${millisUntilFinished / 1000}..."
            }
            override fun onFinish() {
                isArmed = true
                tvStatus.text = ""
                maximizeVolume()
                startSensors()

                // LOCK THE APP
                try {
                    startLockTask()
                } catch (e: Exception) {
                    Log.e("Lock", "Could not pin screen: ${e.message}")
                }
            }
        }.start()
    }

    private fun maximizeVolume() {
        try {
            // Maximize BOTH Music and Alarm streams
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startSensors() {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            currentLux = event.values[0]
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && isArmed && !isGracePeriod) {
            val gForce = abs(event.values[0] + event.values[1] + event.values[2] - 9.81)
            if (gForce > sensitivityThreshold) triggerGracePeriod()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerGracePeriod() {
        isGracePeriod = true
        tvStatus.text = "MOTION DETECTED! UNLOCK NOW!"
        tvStatus.setTextColor(Color.WHITE)
        showBiometricPrompt()

        object : CountDownTimer(3000, 1000) {
            override fun onTick(millis: Long) {}
            override fun onFinish() {
                if (isArmed) performTheftActions()
            }
        }.start()
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // DISARM
                    isArmed = false
                    isGracePeriod = false
                    stopAlarm()

                    // --- UNLOCK APP ---
                    try { stopLockTask() } catch (e: Exception) {}

                    tvStatus.text = "DISARMED"
                    container.setBackgroundColor(Color.BLACK)
                    btnRetry.visibility = View.GONE
                    finish()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Disarm System")
            .setNegativeButtonText("Cancel")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun performTheftActions() {
        tvStatus.text = "ALARM! UPLOADING EVIDENCE!"
        btnRetry.visibility = View.VISIBLE
        playAlarm() // NEW CUSTOM ALARM

        if (currentLux < 10.0) container.setBackgroundColor(Color.WHITE)
        else container.setBackgroundColor(Color.RED)

        container.postDelayed({
            cameraHelper.takePhoto { photoFile ->
                runOnUiThread {
                    container.setBackgroundColor(Color.WHITE)
                    container.postDelayed({ container.setBackgroundColor(Color.RED) }, 100)
                    Toast.makeText(this, "Evidence Captured.", Toast.LENGTH_SHORT).show()
                }
                uploadData(photoFile)
            }
        }, 200)
    }

    // --- 3. CUSTOM LOOPING ALARM ---
    private fun playAlarm() {

        if (isSilentMode) return

        try {
            if (mediaPlayer == null) {

                mediaPlayer = MediaPlayer.create(this, R.raw.loudalarm)
                mediaPlayer?.isLooping = true
            }
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun uploadData(photoFile: File) {
        val user = Firebase.auth.currentUser ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val location = getLocation()
                val requestFile = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("photo", photoFile.name, requestFile)
                val emailPart = (user.email ?: "unknown").toRequestBody("text/plain".toMediaTypeOrNull())
                val latPart = ((location?.latitude ?: 0.0).toString()).toRequestBody("text/plain".toMediaTypeOrNull())
                val lonPart = ((location?.longitude ?: 0.0).toString()).toRequestBody("text/plain".toMediaTypeOrNull())
                val timePart = java.util.Date().toString().toRequestBody("text/plain".toMediaTypeOrNull())

                val response = NetworkManager.theftApi.uploadReport(body, emailPart, latPart, lonPart, timePart)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) tvStatus.text = "EVIDENCE UPLOADED"
                }
            } catch (e: Exception) {
                Log.e("Upload", "Error", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val client = LocationServices.getFusedLocationProviderClient(this)
        return try {
            val source = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token).await()
        } catch (e: Exception) { null }
    }

    private fun hideSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }
}