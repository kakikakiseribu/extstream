package com.kuramanime

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.utils.*

class Sunrong : FilemoonV2() {
    override var mainUrl = "https://sunrong.my.id"
    override var name = "Sunrong"
}

class Nyomo : StreamSB() {
    override var name: String = "Nyomo"
    override var mainUrl = "https://nyomo.my.id"
}

class Streamhide : Filesim() {
    override var name: String = "Streamhide"
    override var mainUrl: String = "https://streamhide.to"
}

open class Lbx : ExtractorApi() {
    override val name = "Linkbox"
    override val mainUrl = "https://lbx.to"
    private val realUrl = "https://www.linkbox.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val token = Regex("""(?:/f/|/file/|\?id=)(\w+)""").find(url)?.groupValues?.get(1)

        val id = app.get("$realUrl/api/file/share_out_list/?sortField=utime&sortAsc=0&pageNo=1&pageSize=50&shareToken=$token")
            .parsedSafe<Responses>()?.data?.itemId

        app.get("$realUrl/api/file/detail?itemId=$id", referer = url)
            .parsedSafe<Responses>()?.data?.itemInfo?.resolutionList?.map { link ->

                val videoUrl = link.url ?: return@map null

                // 🔥 FILTER FORMAT (skip MKV)
                if (!videoUrl.contains(".mp4") && !videoUrl.contains(".m3u8")) return@map null

                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        videoUrl,
                        INFER_TYPE
                    ) {
                        this.referer = "$realUrl/"
                        this.quality = getQualityFromName(link.resolution)
                    }
                )
            }
    }

    data class Resolutions(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("resolution") val resolution: String? = null,
    )

    data class ItemInfo(
        @JsonProperty("resolutionList") val resolutionList: ArrayList<Resolutions>? = arrayListOf(),
    )

    data class Data(
        @JsonProperty("itemInfo") val itemInfo: ItemInfo? = null,
        @JsonProperty("itemId") val itemId: String? = null,
    )

    data class Responses(
        @JsonProperty("data") val data: Data? = null,
    )
}

open class Kuramadrive : ExtractorApi() {
    override val name = "DriveKurama"
    override val mainUrl = "https://kuramadrive.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val req = app.get(url, referer = referer)
        val doc = req.document

        val title = doc.select("title").text()
        val token = doc.select("meta[name=csrf-token]").attr("content")
        val routeCheckAvl = doc.select("input#routeCheckAvl").attr("value")

        val json = app.get(
            routeCheckAvl,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "X-CSRF-TOKEN" to token
            ),
            referer = url,
            cookies = req.cookies
        ).parsedSafe<Source>()

        val streamUrl = json?.url ?: return

        // 🔥 FILTER FORMAT (skip MKV)
        if (!streamUrl.contains(".mp4") && !streamUrl.contains(".m3u8")) return

        callback.invoke(
            newExtractorLink(
                name,
                name,
                streamUrl,
                INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
                this.quality = getIndexQuality(title)
            }
        )
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private data class Source(
        @JsonProperty("url") val url: String,
    )
}

open class Pixeldrain : ExtractorApi() {
    override val name = "Pixeldrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // ambil ID dari URL
        val id = Regex("/d/([a-zA-Z0-9]+)").find(url)?.groupValues?.get(1)
            ?: Regex("/u/([a-zA-Z0-9]+)").find(url)?.groupValues?.get(1)
            ?: return

        // 🔥 pakai filesystem (yang bisa di-stream)
        val videoUrl = "$mainUrl/api/filesystem/$id"

        callback.invoke(
            newExtractorLink(
                name,
                name,
                videoUrl,
                INFER_TYPE
            ) {
                this.headers = mapOf(
                    "Range" to "bytes=0-",
                    "Accept" to "*/*"
                )
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

