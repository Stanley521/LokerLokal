package com.example.lokerlokal.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lokerlokal.R

class BusinessPhotosAdapter : RecyclerView.Adapter<BusinessPhotosAdapter.PhotoViewHolder>() {

    private val photoUrls = mutableListOf<String>()

    fun submitUrls(urls: List<String>) {
        photoUrls.clear()
        photoUrls.addAll(urls)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_business_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoUrls[position])
    }

    override fun getItemCount(): Int = photoUrls.size

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.business_photo)

        fun bind(photoUrl: String) {
            Glide.with(itemView.context)
                .load(photoUrl)
                .placeholder(android.R.color.transparent)
                .error(R.drawable.ic_cafe_placeholder)
                .into(image)
        }
    }
}

