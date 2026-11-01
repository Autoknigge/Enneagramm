package de.autoknigge.enneagramm

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Einwilligungsabfrage (Google UMP) und Banner am Fuß der App.
 * Der Banner-Platz ist fest reserviert, das Layout springt also nicht.
 */
class AdManager(
    private val activity: Activity,
    private val container: FrameLayout,
    private val adSettingsLink: View?,
    private val withStartAd: Boolean = false
) {
    private var started = false
    private var adsInitialized = false
    private var adView: AdView? = null
    private var retries = 0
    private val retryHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (started) return
        started = true

        val info = UserMessagingPlatform.getConsentInformation(activity)

        // Hat der Nutzer früher schon entschieden, kann es sofort losgehen.
        if (info.canRequestAds()) initAds()

        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateAdSettingsLink(info)
                    if (info.canRequestAds()) initAds()
                }
            },
            {
                if (info.canRequestAds()) initAds()
            }
        )
        updateAdSettingsLink(info)
    }

    private fun updateAdSettingsLink(info: ConsentInformation) {
        val required = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        adSettingsLink?.setOnClickListener {
            if (required) {
                UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                    if (info.canRequestAds()) initAds()
                }
            } else {
                Toast.makeText(activity, "Für deine Region sind keine weiteren Werbe-Einstellungen nötig.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initAds() {
        if (adsInitialized) return
        adsInitialized = true
        MobileAds.initialize(activity) { }
        loadBanner()
        if (withStartAd) loadStartAd()
        loadInterstitial()
    }

    private var interstitial: InterstitialAd? = null

    private fun loadInterstitial() {
        if (!adsInitialized || interstitial != null) return
        InterstitialAd.load(
            activity,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            }
        )
    }

    /**
     * Zeigt eine Vollbild-Anzeige, wenn seit der letzten Anzeige mindestens 30 Sekunden vergangen sind. Liefert true, wenn sie gezeigt wurde.
     */
    fun showInterstitialIfDue(): Boolean {
        if (!adsInitialized || activity.isFinishing || activity.isDestroyed) return false
        val now = System.currentTimeMillis()
        if (now - lastFullscreen < MIN_GAP_MS) return false
        val ad = interstitial
        if (ad == null) {
            loadInterstitial()
            return false
        }
        interstitial = null
        lastFullscreen = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loadInterstitial()
            }
        }
        ad.show(activity)
        return true
    }

    /** Kurze Anzeige beim Start: nur einmal je App-Start, nur wenn sie schnell genug geladen ist. */
    private fun loadStartAd() {
        if (startAdHandled) return
        startAdHandled = true
        var timedOut = false
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ timedOut = true }, 4000)
        AppOpenAd.load(
            activity,
            BuildConfig.ADMOB_OPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    handler.removeCallbacksAndMessages(null)
                    if (timedOut || activity.isFinishing || activity.isDestroyed) return
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {}
                    lastFullscreen = System.currentTimeMillis()
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    handler.removeCallbacksAndMessages(null)
                }
            }
        )
    }

    private fun loadBanner() {
        val dm = activity.resources.displayMetrics
        val widthPx = if (container.width > 0) container.width else dm.widthPixels
        val widthDp = (widthPx / dm.density).toInt()

        val view = AdView(activity)
        view.adUnitId = BuildConfig.ADMOB_BANNER_ID
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        container.removeAllViews()
        container.addView(view)
        adView = view
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                retries = 0
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                // Kein Fill: nach einer Pause erneut versuchen (30, 60 und 120 Sekunden).
                if (adView !== view || retries >= 3) return
                val wait = 30_000L shl retries
                retries++
                retryHandler.postDelayed({
                    if (adView === view) view.loadAd(AdRequest.Builder().build())
                }, wait)
            }
        }
        view.loadAd(AdRequest.Builder().build())
    }

    fun pause() {
        adView?.pause()
    }

    fun resume() {
        adView?.resume()
    }

    fun destroy() {
        retryHandler.removeCallbacksAndMessages(null)
        adView?.destroy()
        adView = null
    }

    companion object {
        private var startAdHandled = false
        private var lastFullscreen = 0L
        private const val MIN_GAP_MS = 30 * 1000L
    }
}
