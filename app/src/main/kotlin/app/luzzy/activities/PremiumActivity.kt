package app.luzzy.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.goodwy.commons.activities.BaseSimpleActivity
import app.luzzy.R
import app.luzzy.billing.BillingManager
import app.luzzy.billing.PremiumRepository
import app.luzzy.databinding.ActivityPremiumBinding

class PremiumActivity : BaseSimpleActivity() {

    private lateinit var binding: ActivityPremiumBinding
    private lateinit var billingManager: BillingManager
    private lateinit var premiumRepository: PremiumRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initBilling()
        setupUI()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initBilling() {
        premiumRepository = PremiumRepository(this)
        billingManager = BillingManager(this, premiumRepository)

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
        // Actualizar estado inicial
        updatePremiumStatus(billingManager.isPremium())

        // Botón de compra
        binding.purchaseButton.setOnClickListener {
            showLoading()
            billingManager.launchPurchaseFlow(this)
        }

        // Botón de restaurar compras
        binding.restorePurchasesButton.setOnClickListener {
            showLoading()
            billingManager.initialize() // Esto verificará las compras existentes
            Toast.makeText(
                this,
                getString(R.string.checking_purchases),
                Toast.LENGTH_SHORT
            ).show()
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

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_one,
        R.mipmap.ic_launcher_two,
        R.mipmap.ic_launcher_three,
        R.mipmap.ic_launcher_four,
        R.mipmap.ic_launcher_five,
        R.mipmap.ic_launcher_six,
        R.mipmap.ic_launcher_seven,
        R.mipmap.ic_launcher_eight,
        R.mipmap.ic_launcher_nine,
        R.mipmap.ic_launcher_ten,
        R.mipmap.ic_launcher_eleven
    )

    override fun getAppLauncherName() = getString(R.string.messages)

    override fun getRepositoryName() = "Messages"
}
