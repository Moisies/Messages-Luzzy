package com.goodwy.smsmessenger.billing

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.android.billingclient.api.*
import com.goodwy.smsmessenger.network.RetrofitClient
import com.goodwy.smsmessenger.network.models.PurchaseRegistrationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(
    private val context: Context,
    private val premiumRepository: PremiumRepository,
    private val authToken: String? = null
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
    }

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    var onPremiumStatusChanged: ((Boolean) -> Unit)? = null
    var onPurchaseError: ((String) -> Unit)? = null
    var onProductPriceLoaded: ((String) -> Unit)? = null

    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing setup completado")
                    queryProductDetails()
                    queryPurchases()
                } else {
                    Log.e(TAG, "Error en billing setup: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing service desconectado, reintentando...")
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConstants.PRODUCT_PREMIUM)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = productDetailsList.firstOrNull()
                productDetails?.let { details ->
                    val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                    Log.d(TAG, "Producto premium cargado: $price")
                    onProductPriceLoaded?.invoke(price)
                }
            } else {
                Log.e(TAG, "Error al cargar producto: ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryPurchases() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val productDetailsToUse = productDetails

        if (productDetailsToUse == null) {
            Log.e(TAG, "Detalles del producto no disponibles")
            onPurchaseError?.invoke("Producto no disponible en este momento")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetailsToUse)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { handlePurchases(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Usuario canceló la compra")
                onPurchaseError?.invoke("Compra cancelada")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "El producto ya está comprado")
                queryPurchases()
            }
            else -> {
                Log.e(TAG, "Error en compra: ${billingResult.debugMessage}")
                onPurchaseError?.invoke("Error al procesar la compra")
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        CoroutineScope(Dispatchers.IO).launch {
            var hasPremium = false

            for (purchase in purchases) {
                if (purchase.products.contains(BillingConstants.PRODUCT_PREMIUM)) {
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            if (!purchase.isAcknowledged) {
                                acknowledgePurchase(purchase)
                            }

                            premiumRepository.setPremium(true, purchase.purchaseToken)
                            hasPremium = true
                            Log.d(TAG, "Premium activado")
                        }
                        Purchase.PurchaseState.PENDING -> {
                            Log.d(TAG, "Compra pendiente")
                        }
                    }
                }
            }

            if (!hasPremium && premiumRepository.isPremium()) {
                Log.d(TAG, "No se encontraron compras válidas, desactivando premium")
                premiumRepository.setPremium(false)
            }

            withContext(Dispatchers.Main) {
                onPremiumStatusChanged?.invoke(hasPremium)
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        withContext(Dispatchers.IO) {
            billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Compra reconocida exitosamente")
                    CoroutineScope(Dispatchers.IO).launch {
                        syncPurchaseToBackend(purchase)
                    }
                }
            }
        }
    }

    private suspend fun syncPurchaseToBackend(purchase: Purchase) {
        if (authToken.isNullOrEmpty()) {
            Log.w(TAG, "No auth token disponible, saltando sincronización con backend")
            return
        }

        try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            val request = PurchaseRegistrationRequest(
                purchaseToken = purchase.purchaseToken,
                orderId = purchase.orderId,
                productId = purchase.products.firstOrNull() ?: "",
                packageName = purchase.packageName,
                purchaseState = when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> "purchased"
                    Purchase.PurchaseState.PENDING -> "pending"
                    else -> "canceled"
                },
                purchaseTime = purchase.purchaseTime,
                deviceId = deviceId
            )

            val response = RetrofitClient.apiService.registerPurchase(
                "Bearer $authToken",
                request
            )

            if (response.isSuccessful) {
                Log.d(TAG, "Compra sincronizada con backend exitosamente")
            } else {
                Log.e(TAG, "Error al sincronizar compra: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al sincronizar compra: ${e.message}")
        }
    }

    fun isPremium(): Boolean {
        return premiumRepository.isPremium()
    }

    fun getPremiumPrice(): String? {
        return productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    fun verifyPremiumFromBackend() {
        if (authToken.isNullOrEmpty()) {
            Log.w(TAG, "No auth token disponible")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.apiService.verifyPremiumStatus("Bearer $authToken")

                if (response.isSuccessful && response.body()?.success == true) {
                    val isPremium = response.body()?.data?.isPremium ?: false
                    val purchaseToken = response.body()?.data?.purchaseToken

                    premiumRepository.setPremium(isPremium, purchaseToken)

                    withContext(Dispatchers.Main) {
                        onPremiumStatusChanged?.invoke(isPremium)
                    }

                    Log.d(TAG, "Estado premium verificado desde backend: $isPremium")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al verificar estado premium: ${e.message}")
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}
