package com.example.lokerlokal.ui.home

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.lokerlokal.BuildConfig
import com.example.lokerlokal.R
import com.example.lokerlokal.data.remote.BusinessPlaceDetails
import com.example.lokerlokal.data.remote.GooglePlacesService
import com.example.lokerlokal.ui.map.MapJobItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class LocalJobsListAdapter : RecyclerView.Adapter<LocalJobsListAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "LocalJobsListAdapter"
        private val expiryOutputFormatter = DateTimeFormatter.ofPattern("HH:mm, d MMM", Locale.forLanguageTag("id-ID"))
        private val isoLocalInputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val utcZoneId: ZoneId = ZoneId.of("UTC")
    }

    private val items = mutableListOf<MapJobItem>()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val placeDetailsCache = mutableMapOf<String, BusinessPlaceDetails?>()

    fun submitItems(newItems: List<MapJobItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_job_card, parent, false)
        return ViewHolder(view, requestScope, placeDetailsCache)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        requestScope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val requestScope: CoroutineScope,
        private val placeDetailsCache: MutableMap<String, BusinessPlaceDetails?>,
    ) : RecyclerView.ViewHolder(itemView) {
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

        private var activePlaceJob: Job? = null

        fun bind(item: MapJobItem) {
            activePlaceJob?.cancel()
            Glide.with(itemView.context).clear(image)
            image.setImageDrawable(null)

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

            loadBusinessImage(item)
        }

        fun clear() {
            activePlaceJob?.cancel()
            activePlaceJob = null
            Glide.with(itemView.context).clear(image)
        }

        private fun loadBusinessImage(item: MapJobItem) {
            val placeId = item.businessPlaceId.trim()
            if (placeId.isBlank()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "No business_place_id for jobId=${item.id}; using gradient background only")
                }
                image.setImageDrawable(null)
                return
            }

            image.setImageDrawable(null)

            if (placeDetailsCache.containsKey(placeId)) {
                val cached = placeDetailsCache[placeId]
                if (cached != null) {
                    applyPlaceDetails(item, cached)
                } else {
                    image.setImageDrawable(null)
                }
                return
            }

            activePlaceJob = requestScope.launch {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Fetching place details for jobId=${item.id}, placeId=$placeId")
                }
                val details = GooglePlacesService.getPlaceDetails(placeId)
                placeDetailsCache[placeId] = details

                if (details != null && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    applyPlaceDetails(item, details)
                } else {
                    image.setImageDrawable(null)
                }
            }
        }

        private fun applyPlaceDetails(item: MapJobItem, details: BusinessPlaceDetails) {
            if (details.formattedAddress.isNotBlank()) {
                address.text = details.formattedAddress
            }

            if (!details.photoUrl.isNullOrBlank()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Loading place photo for jobId=${item.id} placeId=${details.placeId} url=${details.photoUrl}")
                }
                val glideUrl = GlideUrl(
                    details.photoUrl,
                    LazyHeaders.Builder()
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                        .build(),
                )
                Glide.with(itemView.context)
                    .load(glideUrl)
                    .error(android.R.color.transparent)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean,
                        ): Boolean {
                            if (BuildConfig.DEBUG) {
                                Log.e(TAG, "Place photo load failed for jobId=${item.id} model=$model", e)
                            }
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean,
                        ): Boolean {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Place photo loaded for jobId=${item.id} source=$dataSource")
                            }
                            return false
                        }
                    })
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(image)
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "No photoUrl from place details for jobId=${item.id}; using gradient background only")
                }
                image.setImageDrawable(null)
            }
        }

        private fun formatExpiry(rawValue: String): String {
            if (rawValue.isBlank()) return "Batas Akhir: -"

            val normalized = rawValue.trim()
            val userZoneId = ZoneId.systemDefault()

            runCatching {
                val instant = Instant.parse(normalized)
                val zonedDt = instant.atZone(userZoneId)
                return "Batas Akhir: ${formatExpiryDateTime(zonedDt)}"
            }

            runCatching {
                val offsetDateTime = OffsetDateTime.parse(normalized)
                val zonedDt = offsetDateTime.atZoneSameInstant(userZoneId)
                return "Batas Akhir: ${formatExpiryDateTime(zonedDt)}"
            }

            runCatching {
                val localDateTime = LocalDateTime.parse(normalized, isoLocalInputFormatter)
                val zonedDateTime = localDateTime.atZone(utcZoneId).withZoneSameInstant(userZoneId)
                return "Batas Akhir: ${formatExpiryDateTime(zonedDateTime)}"
            }

            return try {
                val localDateTime = LocalDateTime.parse(normalized)
                val zonedDateTime: ZonedDateTime = localDateTime.atZone(utcZoneId).withZoneSameInstant(userZoneId)
                "Batas Akhir: ${formatExpiryDateTime(zonedDateTime)}"
            } catch (_: DateTimeParseException) {
                "Batas Akhir: $normalized"
            }
        }

        private fun formatExpiryDateTime(zonedDateTime: ZonedDateTime): String {
            return expiryOutputFormatter.format(zonedDateTime)
        }
    }
}
