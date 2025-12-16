package com.goodwy.smsmessenger.activities

import android.os.Bundle
import android.util.Patterns
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.auth.AuthRepository
import com.goodwy.smsmessenger.databinding.ActivityRegisterBinding
import com.goodwy.smsmessenger.network.ApiClient
import com.goodwy.smsmessenger.network.models.RegisterRequest
import kotlinx.coroutines.launch

class RegisterActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityRegisterBinding::inflate)
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        authRepository = AuthRepository(this)

        setupToolbar(binding.toolbar)
        setupUI()
    }

    private fun setupUI() {
        binding.registerButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (validateInput(name, email, password, confirmPassword)) {
                performRegister(name, email, password, confirmPassword)
            }
        }

        binding.loginButton.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(name: String, email: String, password: String, confirmPassword: String): Boolean {
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            toast(R.string.error_empty_fields)
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast(R.string.error_invalid_email)
            return false
        }

        if (password.length < 6) {
            toast(R.string.error_password_short)
            return false
        }

        if (password != confirmPassword) {
            toast(R.string.error_password_mismatch)
            return false
        }

        return true
    }

    private fun performRegister(name: String, email: String, password: String, confirmPassword: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.register(
                    RegisterRequest(name, email, password, confirmPassword)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val authData = response.body()?.data
                    if (authData != null) {
                        authRepository.saveAuthData(
                            token = authData.token,
                            userId = authData.user.id,
                            userName = authData.user.name,
                            userEmail = authData.user.email,
                            isPremium = authData.user.is_premium
                        )

                        toast(R.string.register_success)
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    toast(R.string.error_register_failed)
                }
            } catch (e: Exception) {
                toast(R.string.error_register_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.registerButton.isEnabled = !loading
        binding.loginButton.isEnabled = !loading
        binding.nameInput.isEnabled = !loading
        binding.emailInput.isEnabled = !loading
        binding.passwordInput.isEnabled = !loading
        binding.confirmPasswordInput.isEnabled = !loading

        if (loading) {
            binding.progressBar.visibility = android.view.View.VISIBLE
        } else {
            binding.progressBar.visibility = android.view.View.GONE
        }
    }
}
