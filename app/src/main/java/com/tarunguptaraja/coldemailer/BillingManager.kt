package com.tarunguptaraja.coldemailer

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigManager: RemoteConfigManager,
    private val tokenManager: TokenManager,
    private val userManager: UserManager
) {

    private val TAG = "BillingManager"

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    private val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase")
        } else {
            Log.e(TAG, "Billing response error: ${billingResult.responseCode}")
            _billingError.value = "Purchase failed: ${billingResult.debugMessage}"
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    init {
        startBillingConnection()
    }

    fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing setup finished")
                    queryAvailableProducts()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _billingError.value = "Billing setup failed"
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing service disconnected")
                // Try to restart the connection on the next request to Google Play by calling the startConnection() method.
            }
        })
    }

    private fun queryAvailableProducts() {
        CoroutineScope(Dispatchers.IO).launch {
            remoteConfigManager.fetchAndActivate()
            val productIds = remoteConfigManager.getShopProductIds()
            if (productIds.isEmpty()) return@launch

            val productList = productIds.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }

            if (productList.isEmpty()) return@launch

            val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Sort the product details if needed, or just emit
                    _productDetails.value = productDetailsList
                } else {
                    Log.e(TAG, "Query products failed: ${billingResult.debugMessage}")
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingError.value = "Failed to launch billing flow: ${billingResult.debugMessage}"
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verify the purchase here if you have a backend server
            
            // Consume the purchase so the user can buy it again
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.consumeAsync(consumeParams) { billingResult, _ ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Reward the user
                    val productId = purchase.products.firstOrNull()
                    if (productId != null) {
                        rewardTokensForProduct(productId)
                    }
                } else {
                    Log.e(TAG, "Failed to consume purchase: ${billingResult.debugMessage}")
                }
            }
        }
    }

    private fun rewardTokensForProduct(productId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Mapping productId to tokens could also be in RemoteConfig, but for now we extract the number from the ID
            // Assuming IDs are like "tokens_50k" or "tokens_100k"
            val amount = when (productId) {
                "tokens_10k" -> 10000
                "tokens_50k" -> 50000
                "tokens_100k" -> 100000
                "tokens_500k" -> 500000
                "tokens_1m" -> 1000000
                else -> 0
            }

            if (amount > 0) {
                tokenManager.addTokens(amount.toLong())

                val tx = com.tarunguptaraja.coldemailer.domain.model.TokenTransaction(
                    id = purchaseId(),
                    amount = amount,
                    type = "PURCHASE",
                    description = "Purchased $productId",
                    timestamp = System.currentTimeMillis()
                )
                userManager.addTokenTransaction(tx)
            }
        }
    }
    
    private fun purchaseId() = java.util.UUID.randomUUID().toString()

    fun clearError() {
        _billingError.value = null
    }
}
