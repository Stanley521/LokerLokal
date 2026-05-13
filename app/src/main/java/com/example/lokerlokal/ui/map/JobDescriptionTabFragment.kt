package com.example.lokerlokal.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.lokerlokal.R

class JobDescriptionTabFragment : Fragment() {

    private val sharedJobsViewModel: MapJobsSharedViewModel by viewModels(
        ownerProducer = { requireParentFragment().parentFragment ?: requireParentFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_job_description_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val body = view.findViewById<TextView>(R.id.description_body)
        val pay = view.findViewById<TextView>(R.id.description_pay)
        val location = view.findViewById<TextView>(R.id.description_location)
        val expires = view.findViewById<TextView>(R.id.description_expires)

        sharedJobsViewModel.selectedJob.observe(viewLifecycleOwner) { job ->
            if (job == null) return@observe
            body.text = job.description.ifBlank { getString(R.string.unknown_job_title) }
            pay.text = getString(
                R.string.job_detail_summary,
                job.payText.ifBlank { "-" },
                job.addressText.ifBlank { "-" },
                job.expiresAt.ifBlank { "-" },
            )
            location.text = getString(
                R.string.map_job_company_coordinates,
                job.businessName.ifBlank { getString(R.string.unknown_business) },
                job.latitude,
                job.longitude,
            )
            expires.text = getString(R.string.job_expires_at_label, job.expiresAt.ifBlank { "-" })
        }
    }
}
