package com.sportsbite

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class SportsBiteProvider : MainAPI() {
    override var mainUrl = "https://sportsbite.org"
    override var name = "SportsBite"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    // data = "status:<değer>" veya "sport:<değer>"
    override val mainPage = mainPageOf(
        "status:live" to "🔴 Canlı",
        "status:upcoming" to "Yaklaşan",
        "sport:football" to "Futbol",
        "sport:basketball" to "Basketbol",
        "sport:hockey" to "Hokey",
        "sport:american-football" to "Amerikan Futbolu",
        "sport:fighting" to "Dövüş"
    )

    private suspend fun fetchPage(page: Int): Schedule =
        app.get("$mainUrl/api/schedule?page=$page").parsed<Schedule>()

    private suspend fun findEvent(id: String): Event? {
        var page = 1
        while (true) {
            val res = fetchPage(page)
            res.events.firstOrNull { it.id == id }?.let { return it }
            if (page >= (res.pagination?.pages ?: 1)) return null
            page++
        }
    }

    private fun Event.toSearchResponse(): SearchResponse =
        newLiveSearchResponse(title, this.toJson(), TvType.Live) {
            posterUrl = teams?.home?.logo ?: tournament?.logo
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = fetchPage(page)
        val (key, value) = request.data.split(":", limit = 2)
        val items = res.events
            .filter { if (key == "status") it.status == value else it.sport == value }
            .map { it.toSearchResponse() }
        return newHomePageResponse(
            request.name, items, hasNext = page < (res.pagination?.pages ?: 1)
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        var page = 1
        while (true) {
            val res = fetchPage(page)
            res.events.filter { it.title.contains(query, ignoreCase = true) }
                .forEach { out.add(it.toSearchResponse()) }
            if (page >= (res.pagination?.pages ?: 1)) break
            page++
        }
        return out
    }

    override suspend fun load(url: String): LoadResponse {
        val e = parseJson<Event>(url)
        return newLiveStreamLoadResponse(e.title, e.watchUrl ?: mainUrl, url) {
            posterUrl = e.teams?.home?.logo ?: e.tournament?.logo
            plot = "${e.tournament?.name.orEmpty()}\n${e.date} ${e.kickoffTime} UTC • ${e.status}"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val stored = parseJson<Event>(data)
        // Yayın linkleri sonradan eklenebildiği için güncel veriyi tekrar çek
        val event = findEvent(stored.id) ?: stored
        var found = false
        event.streams.orEmpty().forEach { s ->
            val link = s.embedUrl ?: return@forEach
            loadExtractor(link, "$mainUrl/", subtitleCallback) {
                found = true
                callback(it)
            }
        }
        return found
    }
}

class SportsBiteExtractor : ExtractorApi() {
    override val name = "SportsBite"
    override val mainUrl = "https://embed.sportsbite.org"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(url, referer = referer ?: "https://sportsbite.org/")
            .text.replace("\\/", "/")
        Regex("""https?:[^"'\s<>\\]+?\.m3u8[^"'\s<>\\]*""")
            .findAll(html).map { it.value }.distinct()
            .forEach { m3u8 ->
                callback(
                    newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
    }
}

// ---- API modelleri ----
data class Schedule(
    @JsonProperty("pagination") val pagination: Pagination? = null,
    @JsonProperty("events") val events: List<Event> = emptyList()
)

data class Pagination(
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("pages") val pages: Int? = null,
    @JsonProperty("total") val total: Int? = null
)

data class Event(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("title") val title: String = "",
    @JsonProperty("sport") val sport: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("date") val date: String? = null,
    @JsonProperty("kickoff_time") val kickoffTime: String? = null,
    @JsonProperty("watch_url") val watchUrl: String? = null,
    @JsonProperty("tournament") val tournament: Tournament? = null,
    @JsonProperty("teams") val teams: Teams? = null,
    @JsonProperty("streams") val streams: List<Stream>? = null
)

data class Tournament(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("logo") val logo: String? = null
)

data class Teams(
    @JsonProperty("home") val home: Team? = null,
    @JsonProperty("away") val away: Team? = null
)

data class Team(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("logo") val logo: String? = null
)

data class Stream(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("embed_url") val embedUrl: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("language") val language: String? = null
)
