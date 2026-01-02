package app.luzzy.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.viewBinding
import app.luzzy.R
import app.luzzy.auth.GoogleAuthRepository
import app.luzzy.billing.PremiumRepository
import app.luzzy.databinding.ActivityUserSettingsBinding
import app.luzzy.network.RetrofitClient
import app.luzzy.utils.SharedPrefsManager
import kotlinx.coroutines.launch

class UserSettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityUserSettingsBinding::inflate)
    private lateinit var premiumRepository: PremiumRepository
    private lateinit var googleAuthRepository: GoogleAuthRepository
    private var isPremium = false

    private val googleLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            toast(R.string.google_login_success)
            updateGoogleLoginUI()
            loadConfiguration()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar(binding.toolbar)
        setupEdgeToEdge(padBottomSystem = listOf(binding.nestedScrollView))

        premiumRepository = PremiumRepository(this)
        googleAuthRepository = GoogleAuthRepository(this)
        isPremium = premiumRepository.isPremium()

        updateGoogleLoginUI()
        loadConfiguration()
        setupUI()
    }

    private fun setupUI() {
        binding.saveButton.setOnClickListener {
            saveConfiguration()
        }

        binding.upgradePremiumButton.setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
        }

        binding.googleLoginButton.setOnClickListener {
            val intent = Intent(this, GoogleLoginActivity::class.java)
            googleLoginLauncher.launch(intent)
        }

        binding.googleLogoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        updatePremiumStatus(isPremium)
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirmation_title)
            .setMessage(R.string.logout_confirmation_message_detail)
            .setPositiveButton(R.string.logout) { _, _ ->
                performLogout()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performLogout() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val success = googleAuthRepository.logout(revokeAccess = false)

                if (success) {
                    toast(R.string.google_logout_success)
                } else {
                    toast(R.string.logout_failed)
                }

                updateGoogleLoginUI()
            } catch (e: Exception) {
                toast(R.string.logout_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateGoogleLoginUI() {
        val isLoggedIn = googleAuthRepository.isLoggedIn()

        if (isLoggedIn) {
            binding.googleLoggedInLayout.visibility = View.VISIBLE
            binding.googleLoggedOutLayout.visibility = View.GONE

            val email = googleAuthRepository.getUserEmail() ?: ""
            binding.googleAccountEmail.text = getString(R.string.logged_in_with_google, email)
        } else {
            binding.googleLoggedInLayout.visibility = View.GONE
            binding.googleLoggedOutLayout.visibility = View.VISIBLE
        }
    }

    private fun loadConfiguration() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val authHeader = SharedPrefsManager.getAuthHeader(this@UserSettingsActivity)
                if (authHeader != null) {
                    val response = RetrofitClient.apiService.getSettings(authHeader)

                    if (response.isSuccessful && response.body() != null) {
                        val settings = response.body()!!
                        updateUI(settings)
                    }
                }
            } catch (e: Exception) {
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateUI(settings: Map<String, Any>) {
        binding.nombreUsuarioInput.setText(settings["nombre_usuario"]?.toString() ?: "")
        binding.temaColorInput.setText(settings["tema_color"]?.toString() ?: "")
        binding.mensajeAutomaticoInput.setText(settings["mensaje_automatico"]?.toString() ?: "")
        binding.firmaSmsInput.setText(settings["firma_sms"]?.toString() ?: "")

        binding.notificacionesSilenciosasSwitch.isChecked = settings["notificaciones_silenciosas"] as? Boolean ?: false
        binding.modoOscuroSwitch.isChecked = settings["modo_oscuro"] as? Boolean ?: false
        binding.autoRespuestaSwitch.isChecked = settings["auto_respuesta_activada"] as? Boolean ?: false
    }

    private fun updatePremiumStatus(premium: Boolean) {
        if (premium) {
            binding.premiumBanner.visibility = View.GONE
            enablePremiumFeatures(true)
        } else {
            binding.premiumBanner.visibility = View.VISIBLE
            enablePremiumFeatures(false)
        }
    }

    private fun enablePremiumFeatures(enabled: Boolean) {
        binding.mensajeAutomaticoInput.isEnabled = enabled
        binding.firmaSmsInput.isEnabled = enabled
        binding.autoRespuestaSwitch.isEnabled = enabled

        val alpha = if (enabled) 1.0f else 0.5f
        binding.mensajeAutomaticoLayout.alpha = alpha
        binding.firmaSmsLayout.alpha = alpha
        binding.autoRespuestaSwitch.alpha = alpha
    }

    private fun saveConfiguration() {
        setLoading(true)

        val settings = mutableMapOf<String, Any>()

        val nombreUsuario = binding.nombreUsuarioInput.text.toString()
        if (nombreUsuario.isNotBlank()) {
            settings["nombre_usuario"] = nombreUsuario
        }

        val temaColor = binding.temaColorInput.text.toString()
        if (temaColor.isNotBlank()) {
            settings["tema_color"] = temaColor
        }

        if (isPremium) {
            val mensajeAutomatico = binding.mensajeAutomaticoInput.text.toString()
            if (mensajeAutomatico.isNotBlank()) {
                settings["mensaje_automatico"] = mensajeAutomatico
            }

            val firmaSms = binding.firmaSmsInput.text.toString()
            if (firmaSms.isNotBlank()) {
                settings["firma_sms"] = firmaSms
            }

            settings["auto_respuesta_activada"] = binding.autoRespuestaSwitch.isChecked
        }

        settings["notificaciones_silenciosas"] = binding.notificacionesSilenciosasSwitch.isChecked
        settings["modo_oscuro"] = binding.modoOscuroSwitch.isChecked

        lifecycleScope.launch {
            try {
                val authHeader = SharedPrefsManager.getAuthHeader(this@UserSettingsActivity)
                if (authHeader != null) {
                    val response = RetrofitClient.apiService.updateSettings(authHeader, settings)

                    if (response.isSuccessful) {
                        toast(R.string.settings_saved)
                        finish()
                    } else {
                        toast(R.string.error_saving_settings)
                    }
                } else {
                    toast(R.string.settings_saved)
                    finish()
                }
            } catch (e: Exception) {
                toast(R.string.settings_saved)
                finish()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.saveButton.isEnabled = !loading
        binding.nombreUsuarioInput.isEnabled = !loading
        binding.temaColorInput.isEnabled = !loading
        binding.notificacionesSilenciosasSwitch.isEnabled = !loading
        binding.modoOscuroSwitch.isEnabled = !loading

        if (!loading && isPremium) {
            enablePremiumFeatures(true)
        }

        if (loading) {
            binding.progressIndicator.visibility = View.VISIBLE
        } else {
            binding.progressIndicator.visibility = View.GONE
        }
    }
}
