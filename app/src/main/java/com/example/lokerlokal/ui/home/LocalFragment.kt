package com.example.lokerlokal.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lokerlokal.databinding.FragmentHomeBinding
import com.example.lokerlokal.ui.map.MapFragment
import com.example.lokerlokal.ui.map.MapJobsSharedViewModel

class LocalFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var jobsAdapter: LocalJobsListAdapter
    private val sharedJobsViewModel: MapJobsSharedViewModel by viewModels(
        ownerProducer = { requireParentFragment().requireParentFragment() }
    )

    private val binding get() = _binding!!

    private val loadMoreScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!recyclerView.canScrollHorizontally(1)) {
                // Reached the right end – request more items
                (parentFragment?.parentFragment as? MapFragment)?.loadMoreLocalJobs()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        jobsAdapter = LocalJobsListAdapter(
            onViewOnMapClicked = { job ->
                (parentFragment?.parentFragment as? MapFragment)?.focusMapOnJob(job)
            },
            onCardClicked = { job ->
                (parentFragment?.parentFragment as? MapFragment)?.openApplySheetForJob(job)
            },
        )
        binding.jobsListView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.jobsListView.adapter = jobsAdapter
        binding.jobsListView.addOnScrollListener(loadMoreScrollListener)

        sharedJobsViewModel.localJobs.observe(viewLifecycleOwner) { jobs ->
            jobsAdapter.submitItems(jobs)
            binding.emptyJobsText.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            binding.jobsListView.visibility = if (jobs.isEmpty()) View.GONE else View.VISIBLE
        }

        val updateTitle: () -> Unit = {
            val source = sharedJobsViewModel.localJobsSource.value
            val placeName = sharedJobsViewModel.selectedPlaceName.value.orEmpty().ifBlank { "-" }
            binding.localJobsTitle.text = if (source == com.example.lokerlokal.ui.map.MapJobsSharedViewModel.LocalJobsSource.PLACE) {
                getString(com.example.lokerlokal.R.string.local_jobs_title_by_place, placeName)
            } else {
                getString(com.example.lokerlokal.R.string.local_jobs_title)
            }
        }

        sharedJobsViewModel.localJobsSource.observe(viewLifecycleOwner) {
            updateTitle()
        }
        sharedJobsViewModel.selectedPlaceName.observe(viewLifecycleOwner) {
            updateTitle()
        }

        sharedJobsViewModel.isLocalJobsLoading.observe(viewLifecycleOwner) { loading ->
            binding.localJobsLoading.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.emptyJobsText.visibility = View.GONE
                binding.jobsListView.visibility = View.GONE
            } else {
                val jobs = sharedJobsViewModel.localJobs.value.orEmpty()
                binding.emptyJobsText.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
                binding.jobsListView.visibility = if (jobs.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        sharedJobsViewModel.isLoadingMoreLocalJobs.observe(viewLifecycleOwner) { loading ->
            binding.loadMoreLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }

        updateTitle()

        return binding.root
    }

    override fun onDestroyView() {
        binding.jobsListView.removeOnScrollListener(loadMoreScrollListener)
        super.onDestroyView()
        _binding = null
    }
}
