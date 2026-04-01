package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class EplayHD : MainAPI() {
    override var mainUrl = "https://eplayhd.com"
    override var name = "EplayHD Live"
    // এখানে আমরা Live স্পোর্টস এবং TV সিলেক্ট করে দিয়েছি
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)

    // ১. হোম পেজে লাইভ খেলার ইভেন্টগুলো লোড করার জন্য
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        
        // DooPlay থিমের লাইভ বা লেটেস্ট পোস্টের ডিব্বাগুলো ধরবে
        val items = document.select("div.items article, div.live-match article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse("Live Sports & Events", items)
    }

    // ২. সার্চ করার জন্য
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        
        return document.select("div.result-item article, article.item").mapNotNull {
            it.toSearchResult()
        }
    }

    // ৩. লাইভ ম্যাচের পেজটি লোড করার জন্য
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("div.data h1, h1.entry-title")?.text() ?: "Live Event"
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val plot = document.selectFirst("div.wp-content p, div.description")?.text()

        // লাইভ খেলাকে মুভির মতো একটা সিঙ্গেল প্লেয়ার হিসেবে লোড করবে
        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    // ৪. লাইভ ভিডিওর ডিরেক্ট লিংক বের করার জন্য
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // প্লেয়ার বক্স বা আইফ্রেম থেকে ভিডিও সোর্স খোঁজা
        document.select("div.player-box iframe, iframe.live-stream").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                // CloudStream নিজে থেকেই চেনা প্লেয়ার হলে লিংক বের করে নেবে
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        
        // অনেক সময় লাইভ সাইটগুলো ডিরেক্ট .m3u8 লিংক ব্যবহার করে স্ক্রিপ্ট ট্যাগের ভেতর
        val scripts = document.select("script").map { it.html() }
        scripts.forEach { script ->
            if (script.contains("file:") || script.contains(".m3u8")) {
                val match = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(script)
                val m3u8Link = match?.groupValues?.get(1)
                
                if (m3u8Link != null) {
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "Live Stream",
                            m3u8Link,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                }
            }
        }
        return true
    }

    // সাইটের এইচটিএমএল থেকে নাম আর থাম্বনেইল তোলার শর্টকাট
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 a, h2 a")?.text() ?: return null
        val href = this.selectFirst("h3 a, h2 a")?.attr("href") ?: return null
        val poster = this.selectFirst("div.poster img, img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = poster
        }
    }
}
