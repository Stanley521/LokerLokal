package com.example.lokerlokal.ui.dashboard

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.lokerlokal.R
import com.example.lokerlokal.data.remote.ResumeMeta
import com.example.lokerlokal.data.remote.SupabaseResumeService
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class DashboardFragment : Fragment() {

    companion object {
        private const val MAX_PDF_SIZE_BYTES = 500 * 1024L
    }

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var resumeStatusView: TextView
    private lateinit var resumeLoadingView: ProgressBar
    private lateinit var uploadButton: MaterialButton

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handlePickedPdf(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resumeStatusView = view.findViewById(R.id.resume_status_dashboard)
        resumeLoadingView = view.findViewById(R.id.resume_loading)
        uploadButton = view.findViewById(R.id.button_upload_resume_dashboard)

        uploadButton.setOnClickListener { pdfPickerLauncher.launch(arrayOf("application/pdf")) }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            resumeLoadingView.visibility = if (loading) View.VISIBLE else View.GONE
            resumeStatusView.visibility = if (loading) View.GONE else View.VISIBLE
        }
        viewModel.resumeMeta.observe(viewLifecycleOwner) { meta ->
            resumeStatusView.text = formatResumeStatus(meta)
        }

        viewModel.loadResume(requireContext())
    }

    private fun handlePickedPdf(uri: Uri) {
        val resolver = requireContext().contentResolver
        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
            } else null
        }
        if (size == null || size <= 0L) {
            Toast.makeText(requireContext(), getString(R.string.invalid_pdf_file), Toast.LENGTH_LONG).show()
            return
        }
        if (size > MAX_PDF_SIZE_BYTES) {
            Toast.makeText(requireContext(), getString(R.string.pdf_too_large), Toast.LENGTH_LONG).show()
            return
        }

        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx).orEmpty() else ""
            } else ""
        } ?: "resume.pdf"

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.invalid_pdf_file), Toast.LENGTH_LONG).show()
            return
        }

        val androidId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val userKey = androidId?.takeIf { it.isNotBlank() } ?: "anonymous-user"

        lifecycleScope.launch {
            uploadButton.isEnabled = false
            val result = runCatching {
                SupabaseResumeService.uploadResume(
                    userKey = userKey,
                    fileName = name.ifBlank { "resume.pdf" },
                    mimeType = "application/pdf",
                    bytes = bytes,
                )
            }
            uploadButton.isEnabled = true
            result.onSuccess { meta ->
                viewModel.onResumeUploaded(meta)
                Toast.makeText(requireContext(), getString(R.string.resume_uploaded_success), Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(requireContext(), err.message ?: getString(R.string.resume_upload_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatResumeStatus(meta: ResumeMeta?): String {
        if (meta == null) return getString(R.string.no_resume_uploaded)
        val updatedAt = try {
            OffsetDateTime.parse(meta.updatedAt)
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")))
        } catch (_: DateTimeParseException) { meta.updatedAt }
        return getString(R.string.resume_status_template, meta.fileName, readableSize(meta.sizeBytes), updatedAt)
    }

    private fun readableSize(size: Long): String {
        if (size < 1024L) return "$size B"
        return "${DecimalFormat("0.#").format(size / 1024.0)} KB"
    }
}