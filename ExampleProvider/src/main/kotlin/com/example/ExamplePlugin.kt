package com.sportsbite

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SportsBitePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SportsBiteProvider())
        registerExtractorAPI(SportsBiteExtractor())
    }
}
