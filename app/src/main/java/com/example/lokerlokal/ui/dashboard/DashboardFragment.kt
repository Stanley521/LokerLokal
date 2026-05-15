package com.example.lokerlokal.ui.dashboard

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.lokerlokal.R
import com.example.lokerlokal.data.auth.SupabaseAuthService
import com.example.lokerlokal.data.auth.SupabaseAuthStore
import com.example.lokerlokal.data.remote.ResumeMeta
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
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
    private lateinit var authStatusView: TextView
    private lateinit var authProfileSummaryView: TextView
    private lateinit var authFullNameLayout: TextInputLayout
    private lateinit var authEmailLayout: TextInputLayout
    private lateinit var authPasswordLayout: TextInputLayout
    private lateinit var authFullNameInput: TextInputEditText
    private lateinit var authEmailInput: TextInputEditText
    private lateinit var authPasswordInput: TextInputEditText
    private lateinit var signInButton: MaterialButton
    private lateinit var signUpButton: MaterialButton
    private lateinit var signOutButton: MaterialButton

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
        authStatusView = view.findViewById(R.id.auth_status)
        authProfileSummaryView = view.findViewById(R.id.auth_profile_summary)
        authFullNameLayout = view.findViewById(R.id.auth_full_name_input_layout)
        authEmailLayout = view.findViewById(R.id.auth_email_input_layout)
        authPasswordLayout = view.findViewById(R.id.auth_password_input_layout)
        authFullNameInput = view.findViewById(R.id.auth_full_name_input)
        authEmailInput = view.findViewById(R.id.auth_email_input)
        authPasswordInput = view.findViewById(R.id.auth_password_input)
        signInButton = view.findViewById(R.id.button_auth_sign_in)
        signUpButton = view.findViewById(R.id.button_auth_sign_up)
        signOutButton = view.findViewById(R.id.button_auth_sign_out)

        uploadButton.setOnClickListener { onUploadResumeClicked() }
        signInButton.setOnClickListener { submitAuth(isSignUp = false) }
        signUpButton.setOnClickListener { submitAuth(isSignUp = true) }
        signOutButton.setOnClickListener { signOut() }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            resumeLoadingView.visibility = if (loading) View.VISIBLE else View.GONE
            resumeStatusView.visibility = if (loading) View.GONE else View.VISIBLE
        }
        viewModel.resumeMeta.observe(viewLifecycleOwner) { meta ->
            resumeStatusView.text = formatResumeStatus(meta)
        }

        renderAuthState()
        viewModel.loadResume(requireContext())
    }

    override fun onResume() {
        super.onResume()
        renderAuthState()
    }

    private fun renderAuthState() {
        if (!isAdded) return

        val session = SupabaseAuthStore.loadSession(requireContext())
        if (session == null) {
            authStatusView.text = getString(R.string.dashboard_sign_in_prompt)
            authProfileSummaryView.text = ""
            authFullNameLayout.visibility = View.VISIBLE
            authEmailLayout.visibility = View.VISIBLE
            authPasswordLayout.visibility = View.VISIBLE
            signInButton.visibility = View.VISIBLE
            signUpButton.visibility = View.VISIBLE
            signOutButton.visibility = View.GONE
            uploadButton.isEnabled = false
            viewModel.clearResume()
            return
        }

        authStatusView.text = getString(R.string.dashboard_signed_in_as, session.user.email ?: session.user.id)
        authProfileSummaryView.text = getString(R.string.dashboard_signed_in_as, session.user.id)
        authFullNameLayout.visibility = View.GONE
        authEmailLayout.visibility = View.GONE
        authPasswordLayout.visibility = View.GONE
        signInButton.visibility = View.GONE
        signUpButton.visibility = View.GONE
        signOutButton.visibility = View.VISIBLE
        uploadButton.isEnabled = true
    }

    private fun setAuthInputsEnabled(enabled: Boolean) {
        authFullNameInput.isEnabled = enabled
        authEmailInput.isEnabled = enabled
        authPasswordInput.isEnabled = enabled
        signInButton.isEnabled = enabled
        signUpButton.isEnabled = enabled
        signOutButton.isEnabled = enabled
    }

    private fun submitAuth(isSignUp: Boolean) {
        val email = authEmailInput.text?.toString()?.trim().orEmpty()
        val password = authPasswordInput.text?.toString().orEmpty()
        val fullName = authFullNameInput.text?.toString()?.trim().orEmpty()

        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_missing_auth_fields), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            setAuthInputsEnabled(false)
            authStatusView.text = if (isSignUp) getString(R.string.dashboard_sign_up) else getString(R.string.dashboard_sign_in)

            val result = runCatching {
                if (isSignUp) {
                    SupabaseAuthService.signUpWithPassword(email, password, fullName)
                } else {
                    SupabaseAuthService.signInWithPassword(email, password)
                }
            }

            setAuthInputsEnabled(true)

            result.onSuccess { session ->
                SupabaseAuthStore.saveSession(requireContext(), session)
                authEmailInput.setText("")
                authPasswordInput.setText("")
                if (isSignUp) authFullNameInput.setText("")
                renderAuthState()
                viewModel.loadResume(requireContext())
                Toast.makeText(
                    requireContext(),
                    if (isSignUp) getString(R.string.dashboard_sign_up_success) else getString(R.string.dashboard_sign_in_success),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { error ->
                authStatusView.text = error.message ?: getString(R.string.dashboard_auth_error)
                Toast.makeText(requireContext(), error.message ?: getString(R.string.dashboard_auth_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun signOut() {
        SupabaseAuthStore.clearSession(requireContext())
        viewModel.clearResume()
        renderAuthState()
        Toast.makeText(requireContext(), getString(R.string.dashboard_sign_out_success), Toast.LENGTH_SHORT).show()
    }

    private fun onUploadResumeClicked() {
        if (!SupabaseAuthStore.isSignedIn(requireContext())) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_sign_in_required), Toast.LENGTH_LONG).show()
            return
        }
        pdfPickerLauncher.launch(arrayOf("application/pdf"))
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

        val session = SupabaseAuthStore.loadSession(requireContext())
        if (session == null) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_sign_in_required), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            uploadButton.isEnabled = false
            val meta = runCatching {
                uploadResume(session, name.ifBlank { "resume.pdf" }, bytes)
            }.getOrElse { err ->
                Toast.makeText(requireContext(), err.message ?: getString(R.string.resume_upload_failed), Toast.LENGTH_LONG).show()
                uploadButton.isEnabled = true
                return@launch
            }

            viewModel.onResumeUploaded(meta)
            Toast.makeText(requireContext(), getString(R.string.resume_uploaded_success), Toast.LENGTH_SHORT).show()
            uploadButton.isEnabled = true
        }
    }

    private suspend fun uploadResume(
        session: com.example.lokerlokal.data.auth.SupabaseSession,
        fileName: String,
        bytes: ByteArray,
    ): ResumeMeta {
        return com.example.lokerlokal.data.remote.SupabaseResumeService.uploadResume(
            session = session,
            fileName = fileName,
            mimeType = "application/pdf",
            bytes = bytes,
        )
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