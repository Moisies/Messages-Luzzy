package com.goodwy.smsmessenger.network.models

import com.google.gson.annotations.SerializedName

data class PurchaseRegistrationRequest(
    @SerializedName("purchase_token") val purchaseToken: String,
    @SerializedName("order_id") val orderId: String?,
    @SerializedName("product_id") val productId: String,
    @SerializedName("package_name") val packageName: String,
    @SerializedName("purchase_state") val purchaseState: String,
    @SerializedName("purchase_time") val purchaseTime: Long,
    @SerializedName("device_id") val deviceId: String?
)

data class PurchaseData(
    @SerializedName("compra_id") val compraId: Long,
    @SerializedName("is_premium") val isPremium: Boolean
)

data class PurchaseStatusResponse(
    @SerializedName("is_premium") val isPremium: Boolean,
    @SerializedName("purchase_time") val purchaseTime: String?,
    @SerializedName("purchase_token") val purchaseToken: String?
)
