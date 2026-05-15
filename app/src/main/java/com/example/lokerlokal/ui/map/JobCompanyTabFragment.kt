package com.example.lokerlokal.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lokerlokal.R
import com.example.lokerlokal.data.remote.GooglePlacesService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class JobCompanyTabFragment : Fragment() {

    private val sharedJobsViewModel: MapJobsSharedViewModel by viewModels(
        ownerProducer = { requireParentFragment().parentFragment ?: requireParentFragment() }
    )

    private val photosAdapter = BusinessPhotosAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_job_company_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val companyName = view.findViewById<TextView>(R.id.company_name)
        val companyAddress = view.findViewById<TextView>(R.id.company_address)
        val loading = view.findViewById<ProgressBar>(R.id.company_loading)
        val viewOnMapButton = view.findViewById<MaterialButton>(R.id.button_view_on_map)
        val photosList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.company_photos_list)
        photosList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        photosList.adapter = photosAdapter

        sharedJobsViewModel.selectedJob.observe(viewLifecycleOwner) { job ->
            if (job == null) return@observe
            companyName.text = job.businessName.ifBlank { getString(R.string.unknown_business) }
            companyAddress.text = job.addressText.ifBlank { job.placeId }
            viewOnMapButton.setOnClickListener {
                (parentFragment?.parentFragment as? MapFragment)?.focusMapOnJob(job)
            }

            val placeId = job.placeId.trim()
            if (placeId.isBlank()) {
                loading.visibility = View.GONE
                photosAdapter.submitUrls(emptyList())
                return@observe
            }

            loading.visibility = View.VISIBLE
            lifecycleScope.launch {
                val details = GooglePlacesService.getPlaceDetails(placeId)
                if (!isAdded) return@launch
                photosAdapter.submitUrls(details?.photoUrls.orEmpty())
                companyName.text = details?.displayName?.ifBlank { job.businessName } ?: job.businessName
                companyAddress.text = details?.formattedAddress?.ifBlank { job.addressText } ?: job.addressText
                loading.visibility = View.GONE
            }
        }
    }
}
