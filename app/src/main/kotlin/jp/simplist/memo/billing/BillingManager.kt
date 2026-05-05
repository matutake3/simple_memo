package jp.simplist.memo.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import jp.simplist.memo.BuildConfig
import jp.simplist.memo.trial.TrialManager

/**
 * Wraps Google Play Billing v7 for the single one-time-purchase product
 * "permanent_unlock" (¥250, no subscription, no consumption).
 *
 * Direct port of AdBlock's BillingManager — same lifecycle, same product id
 * (Play Console treats per-app catalogs independently so the shared name does
 * not collide). See AdBlock for full rationale.
 */
class BillingManager(
    context: Context,
    private val onPurchaseSuccess: () -> Unit = {},
) {

    private val appCtx = context.applicationContext

    private val purchaseListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (p in purchases) handlePurchase(p)
        } else if (BuildConfig.DEBUG) {
            Log.i(TAG, "purchases updated: code=${result.responseCode} ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appCtx)
        .setListener(purchaseListener)
        .enablePendingPurchases()
        .build()

    private var connected: Boolean = false
    private var productDetails: ProductDetails? = null

    fun connect() {
        if (connected) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    queryProductDetails()
                    queryPurchasesOnce()
                } else if (BuildConfig.DEBUG) {
                    Log.w(TAG, "billing setup failed: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    fun release() {
        try { client.endConnection() } catch (_: Exception) {}
        connected = false
    }

    fun formattedPrice(): String? =
        productDetails?.oneTimePurchaseOfferDetails?.formattedPrice

    fun queryPurchasesOnce() {
        if (!connected) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            var foundActive = false
            for (p in purchases) {
                if (PRODUCT_UNLOCK !in p.products) continue
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    foundActive = true
                    handlePurchase(p)
                }
            }
            if (!foundActive && TrialManager.get().isPurchased()) {
                TrialManager.get().clearPurchase()
            }
        }
    }

    fun launchPurchase(activity: Activity): Boolean {
        if (!connected) {
            if (BuildConfig.DEBUG) Log.w(TAG, "launchPurchase: not connected")
            return false
        }
        val details = productDetails ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "launchPurchase: productDetails not loaded yet")
            queryProductDetails()
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "launchBillingFlow failed: ${result.responseCode} ${result.debugMessage}")
            }
            return false
        }
        return true
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_UNLOCK)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && list.isNotEmpty()) {
                productDetails = list.first()
            } else if (BuildConfig.DEBUG) {
                Log.w(TAG, "queryProductDetails failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (PRODUCT_UNLOCK !in purchase.products) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        TrialManager.get().markPurchased(purchase.purchaseToken, purchase.orderId)
        onPurchaseSuccess()

        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build(),
            ) { ackResult ->
                if (ackResult.responseCode != BillingClient.BillingResponseCode.OK &&
                    BuildConfig.DEBUG
                ) {
                    Log.w(TAG, "acknowledge failed: ${ackResult.responseCode} ${ackResult.debugMessage}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_UNLOCK = "permanent_unlock"
    }
}
