package com.goodwy.smsmessenger.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.auth.AuthRepository
import com.goodwy.smsmessenger.databinding.ActivityLoginBinding
import com.goodwy.smsmessenger.network.ApiClient
import com.goodwy.smsmessenger.network.models.LoginRequest
import kotlinx.coroutines.launch

class LoginActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityLoginBinding::inflate)
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        authRepository = AuthRepository(this)

        setupToolbar(binding.toolbar)
        setupUI()
    }

    private fun setupUI() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()

            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }

        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.continueWithoutAccountButton.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            toast(R.string.error_empty_fields)
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast(R.string.error_invalid_email)
            return false
        }

        return true
    }

    private fun performLogin(email: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.login(
                    LoginRequest(email, password)
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

                        toast(R.string.login_success)
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    toast(R.string.error_login_failed)
                }
            } catch (e: Exception) {
                toast(R.string.error_login_failed)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loginButton.isEnabled = !loading
        binding.registerButton.isEnabled = !loading
        binding.continueWithoutAccountButton.isEnabled = !loading
        binding.emailInput.isEnabled = !loading
        binding.passwordInput.isEnabled = !loading

        if (loading) {
            binding.progressBar.visibility = android.view.View.VISIBLE
        } else {
            binding.progressBar.visibility = android.view.View.GONE
        }
    }
}
