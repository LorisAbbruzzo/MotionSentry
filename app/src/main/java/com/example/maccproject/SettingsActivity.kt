package com.example.maccproject

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("MotionSentryConfig", Context.MODE_PRIVATE)

        val seekBar = findViewById<SeekBar>(R.id.seekBarSens)
        val tvSens = findViewById<TextView>(R.id.tvSensLabel)
        val swSilent = findViewById<Switch>(R.id.swSilent)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // 1. Load Saved Values (Default: Sens 2.0 (Progress 20), Silent False)
        val savedSens = prefs.getFloat("sensitivity", 2.0f)
        val savedSilent = prefs.getBoolean("silent_mode", false)

        seekBar.progress = (savedSens * 10).toInt()
        swSilent.isChecked = savedSilent
        updateLabel(tvSens, savedSens)

        // 2. Handle Slider Change
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 10f
                updateLabel(tvSens, value)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        // 3. Save Button
        btnSave.setOnClickListener {
            val editor = prefs.edit()
            editor.putFloat("sensitivity", seekBar.progress / 10f)
            editor.putBoolean("silent_mode", swSilent.isChecked)
            editor.apply()

            Toast.makeText(this, "Configuration Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateLabel(tv: TextView, value: Float) {
        val desc = when {
            value < 1.5 -> "High Sensitivity (Triggers easily)"
            value > 3.0 -> "Low Sensitivity (Hard shoves only)"
            else -> "Medium Sensitivity"
        }
        tv.text = "Threshold: ${value}G\n$desc"
    }
}