package com.example.lokerlokal.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.lokerlokal.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class JobApplyBottomSheetFragment : Fragment() {

    companion object {
        const val TAG = "JobApplyBottomSheetFragment"
    }

    internal val sharedJobsViewModel: MapJobsSharedViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private lateinit var avatarView: TextView
    private lateinit var businessNameView: TextView
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var locationView: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var tabs: TabLayout
    private lateinit var pager: ViewPager2

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            requestClose()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_job_apply, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        avatarView = view.findViewById(R.id.job_detail_avatar)
        businessNameView = view.findViewById(R.id.job_detail_business)
        titleView = view.findViewById(R.id.job_detail_title)
        metaView = view.findViewById(R.id.job_detail_meta)
        locationView = view.findViewById(R.id.job_detail_location)
        closeButton = view.findViewById(R.id.button_close_apply_sheet)
        tabs = view.findViewById(R.id.job_apply_tabs)
        pager = view.findViewById(R.id.job_apply_pager)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        closeButton.setOnClickListener { requestClose() }

        pager.adapter = JobApplyPagerAdapter(this)
        TabLayoutMediator(tabs, pager) { tab, position ->
            val customView = LayoutInflater.from(tab.view.context)
                .inflate(R.layout.item_job_apply_tab, tabs, false) as TextView
            customView.text = when (position) {
                0 -> getString(R.string.job_tab_description)
                1 -> getString(R.string.job_tab_company)
                else -> getString(R.string.job_tab_apply)
            }
            tab.customView = customView
        }.attach()

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.customView?.isSelected = true
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.customView?.isSelected = false
            }

            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        sharedJobsViewModel.selectedJob.observe(viewLifecycleOwner) { job ->
            if (job == null) return@observe
            val businessName = job.businessName.ifBlank { getString(R.string.unknown_business) }
            val title = job.title.ifBlank { getString(R.string.unknown_job_title) }
            businessNameView.text = businessName
            titleView.text = title
            metaView.text = listOf(job.jobType, job.payText)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            locationView.text = listOf(job.addressText, job.distanceText)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            avatarView.text = businessName.firstOrNull()?.uppercase() ?: "?"
        }
    }

    private fun requestClose() {
        (parentFragment as? MapFragment)?.hideApplySheet()
    }
}

private class JobApplyPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> JobDescriptionTabFragment()
            1 -> JobCompanyTabFragment()
            else -> JobApplyTabFragment()
        }
    }
}
