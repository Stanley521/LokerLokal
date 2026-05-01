package com.example.lokerlokal.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lokerlokal.R
import com.example.lokerlokal.data.remote.GooglePlacesService
import com.example.lokerlokal.data.remote.ResumeMeta
import com.example.lokerlokal.data.remote.SupabaseResumeService
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class JobApplyBottomSheetFragment : Fragment() {

    companion object {
        const val TAG = "JobApplyBottomSheetFragment"
        private const val MAX_PDF_SIZE_BYTES = 500 * 1024L
    }

    private val sharedJobsViewModel: MapJobsSharedViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private lateinit var businessNameView: TextView
    private lateinit var titleView: TextView
    private lateinit var summaryView: TextView
    private lateinit var resumeStatusView: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var uploadButton: MaterialButton
    private lateinit var whatsappButton: MaterialButton

    private val photosAdapter = BusinessPhotosAdapter()

    private var selectedJob: MapJobItem? = null
    private var latestResumeMeta: ResumeMeta? = null
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
        return inflater.inflate(R.layout.bottom_sheet_job_apply, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        businessNameView = view.findViewById(R.id.job_detail_business)
        titleView = view.findViewById(R.id.job_detail_title)
        summaryView = view.findViewById(R.id.job_detail_summary)
        resumeStatusView = view.findViewById(R.id.resume_status)
        closeButton = view.findViewById(R.id.button_close_apply_sheet)
        uploadButton = view.findViewById(R.id.button_upload_pdf)
        whatsappButton = view.findViewById(R.id.button_whatsapp_apply)

        val photosList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.business_photos_list)
        photosList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        photosList.adapter = photosAdapter

        closeButton.setOnClickListener {
            (parentFragment as? MapFragment)?.hideApplySheet()
        }
        uploadButton.setOnClickListener { openPdfPicker() }
        whatsappButton.setOnClickListener { onWhatsAppClicked() }

        sharedJobsViewModel.selectedJob.observe(viewLifecycleOwner) { job ->
            selectedJob = job
            if (job == null) return@observe
            bindJob(job)
            loadBusinessPhotos(job)
            loadLatestResumeMeta()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun bindJob(job: MapJobItem) {
        businessNameView.text = job.businessName.ifBlank { getString(R.string.unknown_business) }
        titleView.text = job.title.ifBlank { getString(R.string.unknown_job_title) }
        summaryView.text = getString(
            R.string.job_detail_summary,
            job.payText.ifBlank { "-" },
            job.addressText.ifBlank { "-" },
            job.expiresAt.ifBlank { "-" },
        )
    }

    private fun loadBusinessPhotos(job: MapJobItem) {
        val placeId = job.businessPlaceId.trim()
        if (placeId.isBlank()) {
            photosAdapter.submitUrls(emptyList())
            return
        }

        lifecycleScope.launch {
            val details = GooglePlacesService.getPlaceDetails(placeId)
            photosAdapter.submitUrls(details?.photoUrls.orEmpty())
        }
    }

    private fun loadLatestResumeMeta() {
        val currentContext = context ?: return
        val userKey = resolveUserKey(currentContext)
        lifecycleScope.launch {
            latestResumeMeta = runCatching { SupabaseResumeService.getLatestResume(userKey) }.getOrNull()
            selectedPdfUri = null
            if (!isAdded || view == null) return@launch
            resumeStatusView.text = formatResumeStatus(latestResumeMeta)
        }
    }

    private fun openPdfPicker() {
        pdfPickerLauncher.launch(arrayOf("application/pdf"))
    }

    private fun handlePickedPdf(uri: Uri) {
        val resolver = requireContext().contentResolver
        val size = querySize(uri)
        if (size == null || size <= 0L) {
            Toast.makeText(requireContext(), getString(R.string.invalid_pdf_file), Toast.LENGTH_LONG).show()
            return
        }
        if (size > MAX_PDF_SIZE_BYTES) {
            Toast.makeText(requireContext(), getString(R.string.pdf_too_large), Toast.LENGTH_LONG).show()
            return
        }

        val name = queryDisplayName(uri).ifBlank { "resume.pdf" }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.invalid_pdf_file), Toast.LENGTH_LONG).show()
            return
        }

        val userKey = resolveUserKey(requireContext())
        lifecycleScope.launch {
            uploadButton.isEnabled = false
            val uploaded = runCatching {
                SupabaseResumeService.uploadResume(
                    userKey = userKey,
                    fileName = name,
                    mimeType = "application/pdf",
                    bytes = bytes,
                )
            }
            uploadButton.isEnabled = true

            uploaded.onSuccess { meta ->
                latestResumeMeta = meta
                selectedPdfUri = null
                resumeStatusView.text = formatResumeStatus(meta)
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

    private suspend fun resolveAttachmentUri(): Uri? {
        selectedPdfUri?.let { return it }

        val meta = latestResumeMeta ?: return null
        val bytes = runCatching { SupabaseResumeService.downloadResume(meta.filePath) }.getOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            val file = File(requireContext().cacheDir, "resume-share.pdf")
            file.outputStream().use { it.write(bytes) }
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
        }
    }

    private fun formatResumeStatus(meta: ResumeMeta?): String {
        val currentContext = context ?: return ""
        if (meta == null) return currentContext.getString(R.string.no_resume_uploaded)

        val updatedAt = try {
            val parsed = OffsetDateTime.parse(meta.updatedAt)
            parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")))
        } catch (_: DateTimeParseException) {
            meta.updatedAt
        }

        return currentContext.getString(
            R.string.resume_status_template,
            meta.fileName,
            readableFileSize(meta.sizeBytes),
            updatedAt,
        )
    }

    private fun readableFileSize(size: Long): String {
        if (size < 1024L) return "$size B"
        val sizeKb = size / 1024.0
        val formatter = DecimalFormat("0.#")
        return "${formatter.format(sizeKb)} KB"
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

    private fun resolveUserKey(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId?.takeIf { it.isNotBlank() } ?: "anonymous-user"
    }
}

