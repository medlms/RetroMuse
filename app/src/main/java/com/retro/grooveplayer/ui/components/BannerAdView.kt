package com.retro.grooveplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.retro.grooveplayer.ui.theme.BgColor

@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    // Set admobBannerUnitId in keystore.properties to ship real ads; defaults to
    // Google's official test unit so debug builds never serve live inventory.
    adUnitId: String = com.retro.grooveplayer.BuildConfig.ADMOB_BANNER_UNIT_ID
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = adUnitId
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
