package com.example.lokerlokal.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lokerlokal.databinding.FragmentHomeBinding
import com.example.lokerlokal.ui.map.MapJobsSharedViewModel

class LocalFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var jobsAdapter: LocalJobsListAdapter
    private val sharedJobsViewModel: MapJobsSharedViewModel by viewModels(
        ownerProducer = { requireParentFragment().requireParentFragment() }
    )

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        jobsAdapter = LocalJobsListAdapter()
        binding.jobsListView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.jobsListView.adapter = jobsAdapter

        sharedJobsViewModel.jobs.observe(viewLifecycleOwner) { jobs ->
            jobsAdapter.submitItems(jobs)
            binding.emptyJobsText.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            binding.jobsListView.visibility = if (jobs.isEmpty()) View.GONE else View.VISIBLE
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

