package com.example.lokerlokal.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lokerlokal.R
import com.example.lokerlokal.ui.map.MapJobItem

class NotificationsJobAdapter(
    private val onJobClicked: (MapJobItem) -> Unit,
) : RecyclerView.Adapter<NotificationsJobAdapter.ViewHolder>() {

    private var items: List<MapJobItem> = emptyList()

    fun submitItems(newItems: List<MapJobItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_job, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.notif_job_title)
        private val businessView: TextView = view.findViewById(R.id.notif_business_name)
        private val payView: TextView = view.findViewById(R.id.notif_pay_text)
        private val distanceView: TextView = view.findViewById(R.id.notif_distance)
        private val jobTypeView: TextView = view.findViewById(R.id.notif_job_type)

        fun bind(job: MapJobItem) {
            titleView.text = job.title.ifBlank { itemView.context.getString(R.string.unknown_job_title) }
            businessView.text = job.businessName.ifBlank { itemView.context.getString(R.string.unknown_business) }
            payView.text = job.payText.ifBlank { "-" }
            jobTypeView.text = job.jobType
            jobTypeView.visibility = if (job.jobType.isBlank()) View.GONE else View.VISIBLE
            distanceView.text = job.distanceText.ifBlank { "" }
            itemView.setOnClickListener { onJobClicked(job) }
        }
    }
}
