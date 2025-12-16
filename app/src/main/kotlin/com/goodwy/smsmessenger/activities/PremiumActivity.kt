package com.goodwy.smsmessenger.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.auth.AuthRepository
import com.goodwy.smsmessenger.billing.BillingManager
import com.goodwy.smsmessenger.billing.PremiumRepository
import com.goodwy.smsmessenger.databinding.ActivityPremiumBinding

class PremiumActivity : BaseSimpleActivity() {

    private lateinit var binding: ActivityPremiumBinding
    private lateinit var billingManager: BillingManager
    private lateinit var premiumRepository: PremiumRepository
    private lateinit var authRepository: AuthRepository

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            updateAccountInfo()
            initBilling()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)

        setupToolbar()
        updateAccountInfo()
        initBilling()
        setupUI()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun updateAccountInfo() {
        if (authRepository.isLoggedIn()) {
            binding.accountInfoLayout.visibility = View.VISIBLE
            binding.loginPromptLayout.visibility = View.GONE
            binding.accountEmail.text = getString(R.string.logged_in_as, authRepository.getUserEmail())
        } else {
            binding.accountInfoLayout.visibility = View.GONE
            binding.loginPromptLayout.visibility = View.VISIBLE
        }
    }

    private fun initBilling() {
        premiumRepository = PremiumRepository(this)
        val authToken = authRepository.getAuthHeader()
        billingManager = BillingManager(this, premiumRepository, authToken)

        // Configurar callbacks
        billingManager.onPremiumStatusChanged = { isPremium ->
            runOnUiThread {
                updatePremiumStatus(isPremium)
                hideLoading()
            }
        }

        billingManager.onPurchaseError = { error ->
            runOnUiThread {
                hideLoading()
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }

        billingManager.onProductPriceLoaded = { price ->
            runOnUiThread {
                binding.premiumPriceText.text = price
            }
        }

        // Inicializar billing
        showLoading()
        billingManager.initialize()
    }

    private fun setupUI() {
        updatePremiumStatus(billingManager.isPremium())

        binding.purchaseButton.setOnClickListener {
            showLoading()
            billingManager.launchPurchaseFlow(this)
        }

        binding.restorePurchasesButton.setOnClickListener {
            showLoading()
            billingManager.initialize()
            Toast.makeText(
                this,
                getString(R.string.checking_purchases),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.loginButton.setOnClickListener {
            loginLauncher.launch(Intent(this, LoginActivity::class.java))
        }

        binding.logoutButton.setOnClickListener {
            authRepository.clearAuthData()
            updateAccountInfo()
            Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show()
            initBilling()
        }
    }

    private fun updatePremiumStatus(isPremium: Boolean) {
        if (isPremium) {
            binding.premiumStatusText.text = getString(R.string.premium_status_active)
            binding.purchaseButton.isEnabled = false
            binding.purchaseButton.text = getString(R.string.already_premium)
            binding.restorePurchasesButton.visibility = View.GONE
        } else {
            binding.premiumStatusText.text = getString(R.string.premium_status_inactive)
            binding.purchaseButton.isEnabled = true
            binding.purchaseButton.text = getString(R.string.upgrade_to_premium)
            binding.restorePurchasesButton.visibility = View.VISIBLE
        }
    }

    private fun showLoading() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.purchaseButton.isEnabled = false
        binding.restorePurchasesButton.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressIndicator.visibility = View.GONE
        binding.purchaseButton.isEnabled = !billingManager.isPremium()
        binding.restorePurchasesButton.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
    }
}
