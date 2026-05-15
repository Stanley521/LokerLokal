package com.example.lokerlokal.ui.map

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.lokerlokal.R
import com.example.lokerlokal.data.auth.SupabaseAuthStore
import com.example.lokerlokal.data.remote.SupabaseResumeService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class JobApplyTabFragment : Fragment() {

    private val sharedJobsViewModel: MapJobsSharedViewModel by viewModels(
        ownerProducer = { requireParentFragment().parentFragment ?: requireParentFragment() }
    )

    private lateinit var whatsappNumberView: TextView
    private lateinit var uploadButton: MaterialButton
    private lateinit var whatsappButton: MaterialButton

    private var selectedJob: MapJobItem? = null
    private var selectedPdfUri: Uri? = null

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        handlePickedPdf(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_job_apply_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        whatsappNumberView = view.findViewById(R.id.apply_whatsapp_number)
        uploadButton = view.findViewById(R.id.button_upload_pdf)
        whatsappButton = view.findViewById(R.id.button_whatsapp_apply)

        uploadButton.setOnClickListener { openPdfPicker() }
        whatsappButton.setOnClickListener { onWhatsAppClicked() }

        sharedJobsViewModel.selectedJob.observe(viewLifecycleOwner) { job ->
            selectedJob = job
            whatsappNumberView.text = buildPhoneText(job)
        }
    }

    private fun buildPhoneText(job: MapJobItem?): String {
        if (job == null) return getString(R.string.no_selected_job)
        return job.whatsapp.ifBlank { job.phone }.ifBlank { "-" }
    }

    private fun openPdfPicker() {
        pdfPickerLauncher.launch(arrayOf("application/pdf"))
    }

    private fun handlePickedPdf(uri: Uri) {
        val size = querySize(uri)
        if (size == null || size <= 0L) {
            Toast.makeText(requireContext(), getString(R.string.invalid_pdf_file), Toast.LENGTH_LONG).show()
            return
        }
        if (size > 500 * 1024L) {
            Toast.makeText(requireContext(), getString(R.string.pdf_too_large), Toast.LENGTH_LONG).show()
            return
        }

        val name = queryDisplayName(uri).ifBlank { "resume.pdf" }
        val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.invalid_pdf_file), Toast.LENGTH_LONG).show()
            return
        }

        val session = SupabaseAuthStore.loadSession(requireContext())
        if (session == null) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_sign_in_required), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            uploadButton.isEnabled = false
            val uploaded = runCatching {
                SupabaseResumeService.uploadResume(
                    session = session,
                    fileName = name,
                    mimeType = "application/pdf",
                    bytes = bytes,
                )
            }
            uploadButton.isEnabled = true

            uploaded.onSuccess {
                selectedPdfUri = uri
                Toast.makeText(requireContext(), getString(R.string.resume_uploaded_success), Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), error.message ?: getString(R.string.resume_upload_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onWhatsAppClicked() {
        val job = selectedJob
        if (job == null) {
            Toast.makeText(requireContext(), getString(R.string.no_selected_job), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val attachUri = resolveAttachmentUri()
            if (attachUri == null) {
                Toast.makeText(requireContext(), getString(R.string.upload_resume_first), Toast.LENGTH_LONG).show()
                return@launch
            }

            val message = getString(
                R.string.whatsapp_application_message,
                job.businessName.ifBlank { "Tim Rekrutmen" },
                job.title.ifBlank { "Posisi" },
                job.addressText.ifBlank { "-" },
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_STREAM, attachUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
            }

            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), getString(R.string.whatsapp_not_installed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resolveAttachmentUri(): Uri? {
        return selectedPdfUri
    }

    private fun querySize(uri: Uri): Long? {
        val resolver = requireContext().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex)
                }
            }
        }
        return null
    }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = requireContext().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    return cursor.getString(nameIndex).orEmpty()
                }
            }
        }
        return ""
    }

}
