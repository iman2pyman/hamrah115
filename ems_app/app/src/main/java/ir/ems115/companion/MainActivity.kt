package ir.ems115.companion

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseState
import ir.cafebazaar.poolakey.request.PurchaseRequest

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var payment: Payment
    private var paymentConnection: Connection? = null
    @Volatile private var billingConnected = false
    @Volatile private var premiumActive = false
    @Volatile private var billingMessage = "در حال بررسی اشتراک…"

    companion object {
        const val SUBSCRIPTION_ID = "115_EMS_guide"
        const val RSA_PUBLIC_KEY = "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwDjZlK+KLipejMyLhkw9JwgfHBRwqYqvEWALJBOZeQNttr8sVH0w/vjR04uibUohgeZ5pY+3pTqmkFeGela0izZvWtR7q09Pc67y9s+gSnJ7U4t8CYO4FfqLtXo/ha/4jrS3oacrDJdLLFECfpDOjl/w5F8d71vmKPhcoIz0LcUk1/7D80j3dT7inuMw5Xjy6A3qn46gjfoJ81FfiruHsgJmbjyhMpqga2gcifmKDcCAwEAAQ=="
        // The first 30 entries in the source protocol list are free. These are PDF start pages for entries 31-61.
        val PREMIUM_PDF_PAGES = setOf(62, 64, 66, 68, 70, 71, 72, 74, 76, 78, 80, 82, 84, 86, 90, 91, 92, 94, 96, 97, 98, 101, 102, 104, 106, 107, 110, 112, 114, 116, 118)
        @JvmField @Volatile var premiumAccess: Boolean = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                publishStatus()
                view.evaluateJavascript("if(window.NativeBillingReady){window.NativeBillingReady();}", null)
            }
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "NativeApp")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        initializeBilling()
    }

    private fun initializeBilling() {
        val security = SecurityCheck.Enable(rsaPublicKey = RSA_PUBLIC_KEY)
        payment = Payment(this, PaymentConfiguration(localSecurityCheck = security))
        paymentConnection = payment.connect {
            connectionSucceed {
                billingConnected = true
                billingMessage = "اتصال به کافه‌بازار برقرار شد؛ در حال بررسی اشتراک…"
                querySubscription()
            }
            connectionFailed { error ->
                billingConnected = false
                premiumActive = false
                premiumAccess = false
                billingMessage = "اتصال به پرداخت کافه‌بازار برقرار نشد. برای خرید یا بررسی اشتراک، کافه‌بازار را نصب و به‌روز کنید."
                publishStatus()
            }
            disconnected {
                billingConnected = false
                premiumActive = false
                premiumAccess = false
                billingMessage = "ارتباط با کافه‌بازار قطع شد؛ اشتراک ویژه تا بررسی مجدد قفل است."
                publishStatus()
            }
        }
    }

    private fun querySubscription() {
        if (!billingConnected) {
            premiumActive = false
            premiumAccess = false
            publishStatus()
            return
        }
        payment.getSubscribedProducts {
            querySucceed { purchases ->
                premiumActive = purchases.any {
                    it.productId == SUBSCRIPTION_ID && it.purchaseState == PurchaseState.PURCHASED
                }
                premiumAccess = premiumActive
                billingMessage = if (premiumActive) "اشتراک یک‌ساله شما فعال است." else "اشتراک فعالی برای این حساب کافه‌بازار پیدا نشد."
                publishStatus()
            }
            queryFailed { error ->
                premiumActive = false
                premiumAccess = false
                billingMessage = "بررسی وضعیت اشتراک ناموفق بود؛ اتصال کافه‌بازار را بررسی و دوباره تلاش کنید."
                publishStatus()
            }
        }
    }

    private fun startSubscription() {
        if (!billingConnected) {
            Toast.makeText(this, "ابتدا باید به سرویس کافه‌بازار متصل شوید.", Toast.LENGTH_LONG).show()
            paymentConnection?.disconnect()
            initializeBilling()
            return
        }
        billingMessage = "در حال باز کردن صفحه خرید اشتراک…"
        publishStatus()
        payment.subscribeProduct(
            registry = activityResultRegistry,
            request = PurchaseRequest(
                productId = SUBSCRIPTION_ID,
                payload = "hamrah115_365_day_subscription"
            )
        ) {
            purchaseFlowBegan { billingMessage = "فرایند خرید در کافه‌بازار آغاز شد."; publishStatus() }
            failedToBeginFlow { error -> billingMessage = "شروع خرید ممکن نشد: ${error.message ?: "خطای نامشخص"}"; publishStatus() }
            purchaseSucceed { purchase ->
                billingMessage = "خرید ثبت شد؛ در حال تأیید اشتراک…"
                publishStatus()
                querySubscription()
            }
            purchaseCanceled { billingMessage = "خرید لغو شد."; publishStatus() }
            purchaseFailed { error -> billingMessage = "خرید ناموفق بود: ${error.message ?: "خطای نامشخص"}"; publishStatus() }
        }
    }

    private fun publishStatus() {
        if (!::web.isInitialized) return
        val active = if (premiumActive) "true" else "false"
        val msg = billingMessage.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        runOnUiThread {
            if (::web.isInitialized) web.evaluateJavascript("window.onSubscriptionStatus && window.onSubscriptionStatus($active, '$msg');", null)
        }
    }

    inner class Bridge {
        @JavascriptInterface fun openPdf(page: Int) {
            runOnUiThread {
                if (page >= 62 && !premiumActive) {
                    web.evaluateJavascript("window.showPaywall && window.showPaywall();", null)
                    return@runOnUiThread
                }
                startActivity(android.content.Intent(this@MainActivity, PdfActivity::class.java).putExtra("page", page.coerceIn(1, 119)))
            }
        }
        @JavascriptInterface fun subscribe() { runOnUiThread { startSubscription() } }
        @JavascriptInterface fun refreshSubscription() { runOnUiThread { if (billingConnected) querySubscription() else initializeBilling() } }
        @JavascriptInterface fun isPremium(): Boolean = premiumActive
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) querySubscription()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        paymentConnection?.disconnect()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
