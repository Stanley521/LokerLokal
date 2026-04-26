package com.example.lokerlokal.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lokerlokal.R
import com.example.lokerlokal.ui.map.MapJobItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.zone.ZoneRules
import java.util.Locale

class LocalJobsListAdapter : RecyclerView.Adapter<LocalJobsListAdapter.ViewHolder>() {

    companion object {
        private val expiryOutputFormatter = DateTimeFormatter.ofPattern("HH:mm, d MMM", Locale.forLanguageTag("id-ID"))
        private val isoLocalInputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val utcZoneId: ZoneId = ZoneId.of("UTC")
    }

    private val items = mutableListOf<MapJobItem>()

    fun submitItems(newItems: List<MapJobItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_job_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.job_image)
        private val title: TextView = itemView.findViewById(R.id.job_title)
        private val businessName: TextView = itemView.findViewById(R.id.job_business_name)
        private val description: TextView = itemView.findViewById(R.id.job_description)
        private val jobType: TextView = itemView.findViewById(R.id.job_type)
        private val payText: TextView = itemView.findViewById(R.id.job_pay_text)
        private val distance: TextView = itemView.findViewById(R.id.job_distance)
        private val address: TextView = itemView.findViewById(R.id.job_address)
        private val whatsapp: TextView = itemView.findViewById(R.id.job_whatsapp)
        private val phone: TextView = itemView.findViewById(R.id.job_phone)
        private val expiresAt: TextView = itemView.findViewById(R.id.job_expires_at)

        fun bind(item: MapJobItem) {
            image.setImageResource(R.drawable.ic_map_black_24dp)
            title.text = item.title
            businessName.text = item.businessName
            description.text = item.description.ifBlank { "-" }
            jobType.text = item.jobType.ifBlank { "-" }
            payText.text = item.payText.ifBlank { "-" }
            distance.text = item.distanceText.ifBlank { "-" }
            address.text = item.addressText.ifBlank { "-" }
            whatsapp.text = item.whatsapp.ifBlank { "-" }
            phone.text = item.phone.ifBlank { "-" }
            expiresAt.text = formatExpiry(item.expiresAt)
        }

        private fun formatExpiry(rawValue: String): String {
            if (rawValue.isBlank()) return "Batas Akhir: -"

            val normalized = rawValue.trim()
            val userZoneId = ZoneId.systemDefault()

            runCatching {
                val instant = Instant.parse(normalized)
                val zonedDt = instant.atZone(userZoneId)
                return "Batas Akhir: ${formatWithTimezone(zonedDt, userZoneId)}"
            }

            runCatching {
                val offsetDateTime = OffsetDateTime.parse(normalized)
                val zonedDt = offsetDateTime.atZoneSameInstant(userZoneId)
                return "Batas Akhir: ${formatWithTimezone(zonedDt, userZoneId)}"
            }

            runCatching {
                val localDateTime = LocalDateTime.parse(normalized, isoLocalInputFormatter)
                val zonedDateTime = localDateTime.atZone(utcZoneId).withZoneSameInstant(userZoneId)
                return "Batas Akhir: ${formatWithTimezone(zonedDateTime, userZoneId)}"
            }

            return try {
                val localDateTime = LocalDateTime.parse(normalized)
                val zonedDateTime: ZonedDateTime = localDateTime.atZone(utcZoneId).withZoneSameInstant(userZoneId)
                "Batas Akhir: ${formatWithTimezone(zonedDateTime, userZoneId)}"
            } catch (_: DateTimeParseException) {
                "Batas Akhir: $normalized"
            }
        }

        private fun formatWithTimezone(zonedDateTime: ZonedDateTime, zoneId: ZoneId): String {
            val timeStr = expiryOutputFormatter.format(zonedDateTime)
            val tzAbbr = zonedDateTime.format(DateTimeFormatter.ofPattern("z"))
            val tzLabel = if (tzAbbr.isEmpty() || tzAbbr == "z") {
                // Fallback to GMT offset
                val rules = zoneId.rules
                val offset = rules.getOffset(zonedDateTime.toInstant())
                val hours = offset.totalSeconds / 3600
                val minutes = Math.abs(offset.totalSeconds % 3600) / 60
                if (minutes == 0) "GMT${String.format("%+d", hours)}" else "GMT${String.format("%+d:%02d", hours, minutes)}"
            } else {
                tzAbbr
            }
            return "$timeStr $tzLabel"
        }
    }
}

