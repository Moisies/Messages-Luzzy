package com.goodwy.smsmessenger.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.auth.AuthRepository
import com.goodwy.smsmessenger.billing.PremiumRepository
import com.goodwy.smsmessenger.databinding.ActivityUserSettingsBinding
import com.goodwy.smsmessenger.network.ApiClient
import com.goodwy.smsmessenger.network.models.ConfiguracionData
import com.goodwy.smsmessenger.network.models.ConfiguracionUpdateRequest
import kotlinx.coroutines.launch

class UserSettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityUserSettingsBinding::inflate)
    private lateinit var authRepository: AuthRepository
    private lateinit var premiumRepository: PremiumRepository

    private var isPremium = false
    private var currentConfig: ConfiguracionData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        authRepository = AuthRepository(this)
        premiumRepository = PremiumRepository(this)

        setupToolbar(binding.toolbar)

        if (!authRepository.isLoggedIn()) {
            toast(R.string.login_required)
            finish()
            return
        }

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
    }

    private fun loadConfiguration() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val authHeader = authRepository.getAuthHeader()
                if (authHeader == null) {
                    toast(R.string.login_required)
                    finish()
                    return@launch
                }

                val response = ApiClient.apiService.getConfiguracion(authHeader)

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    if (data != null) {
                        currentConfig = data.configuracion
                        isPremium = data.is_premium

                        updateUI(data.configuracion)
                        updatePremiumStatus(isPremium)
                    }
                } else {
                    toast(R.string.error_loading_settings)
                }
            } catch (e: Exception) {
                toast(R.string.error_loading_settings)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateUI(config: ConfiguracionData) {
        binding.nombreUsuarioInput.setText(config.nombre_usuario ?: "")
        binding.temaColorInput.setText(config.tema_color ?: "")
        binding.mensajeAutomaticoInput.setText(config.mensaje_automatico ?: "")
        binding.firmaSmsInput.setText(config.firma_sms ?: "")

        binding.notificacionesSilenciososSwitch.isChecked = config.notificaciones_silenciosas
        binding.modoOscuroSwitch.isChecked = config.modo_oscuro
        binding.autoRespuestaSwitch.isChecked = config.auto_respuesta_activada
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

        val request = ConfiguracionUpdateRequest(
            nombre_usuario = binding.nombreUsuarioInput.text.toString().takeIf { it.isNotBlank() },
            tema_color = binding.temaColorInput.text.toString().takeIf { it.isNotBlank() },
            mensaje_automatico = if (isPremium) binding.mensajeAutomaticoInput.text.toString().takeIf { it.isNotBlank() } else null,
            firma_sms = if (isPremium) binding.firmaSmsInput.text.toString().takeIf { it.isNotBlank() } else null,
            notificaciones_silenciosas = binding.notificacionesSilenciososSwitch.isChecked,
            modo_oscuro = binding.modoOscuroSwitch.isChecked,
            auto_respuesta_activada = if (isPremium) binding.autoRespuestaSwitch.isChecked else null
        )

        lifecycleScope.launch {
            try {
                val authHeader = authRepository.getAuthHeader()
                if (authHeader == null) {
                    toast(R.string.login_required)
                    return@launch
                }

                val response = ApiClient.apiService.updateConfiguracion(authHeader, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    toast(R.string.settings_saved)
                    finish()
                } else {
                    toast(R.string.error_saving_settings)
                }
            } catch (e: Exception) {
                toast(R.string.error_saving_settings)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.saveButton.isEnabled = !loading
        binding.nombreUsuarioInput.isEnabled = !loading
        binding.temaColorInput.isEnabled = !loading
        binding.notificacionesSilenciososSwitch.isEnabled = !loading
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
