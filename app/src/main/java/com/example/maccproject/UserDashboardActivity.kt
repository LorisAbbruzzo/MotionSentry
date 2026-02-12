package com.example.maccproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class UserDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dashboard)

        val rv = findViewById<RecyclerView>(R.id.rvReports)
        rv.layoutManager = LinearLayoutManager(this)

        val user = Firebase.auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please Login First", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Fetch Data
        lifecycleScope.launch {
            try {
                val reports = NetworkManager.theftApi.getReports(user.email ?: "")
                rv.adapter = ReportsAdapter(reports)
            } catch (e: Exception) {
                Toast.makeText(this@UserDashboardActivity, "Error loading logs: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- INNER ADAPTER CLASS ---
    class ReportsAdapter(private val list: List<TheftLog>) : RecyclerView.Adapter<ReportsAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvDate: TextView = v.findViewById(R.id.tvDate)
            val img: ImageView = v.findViewById(R.id.ivEvidence)
            val btn: Button = v.findViewById(R.id.btnMap)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvDate.text = item.timestamp

            // Load Image from PythonAnywhere
            Glide.with(holder.itemView.context)
                .load(item.image_url)
                .into(holder.img)


            holder.img.setOnClickListener {
                val intent = Intent(holder.itemView.context, ImageDetailActivity::class.java)
                intent.putExtra("IMAGE_URL", item.image_url)
                holder.itemView.context.startActivity(intent)
            }

            // Map Button
            holder.btn.setOnClickListener {
                val uri = Uri.parse("geo:${item.lat},${item.lon}?q=${item.lat},${item.lon}(Theft Location)")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                holder.itemView.context.startActivity(intent)
            }
        }

        override fun getItemCount() = list.size
    }
}