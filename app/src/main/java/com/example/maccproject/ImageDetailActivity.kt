package com.example.maccproject

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class ImageDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)

        val imageUrl = intent.getStringExtra("IMAGE_URL")
        val imageView = findViewById<ImageView>(R.id.ivFullImage)
        val btnDownload = findViewById<Button>(R.id.btnDownload)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)


        if (imageUrl != null) {
            Glide.with(this).load(imageUrl).into(imageView)
        }

        btnClose.setOnClickListener { finish() }

        btnDownload.setOnClickListener {
            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                saveImageToGallery(bitmap)
            } else {
                Toast.makeText(this, "Wait for image to load...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Prepare the file metadata
                val filename = "Evidence_${System.currentTimeMillis()}.jpg"
                val resolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MotionSentry")
                    }
                }

                // Insert into the system Gallery
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    val outputStream: OutputStream? = resolver.openOutputStream(it)
                    outputStream?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageDetailActivity, "Saved to Gallery!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageDetailActivity, "Save Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}