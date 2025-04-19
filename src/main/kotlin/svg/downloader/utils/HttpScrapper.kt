package svg.downloader.utils

import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Shared HTTP client with connection pooling
object HttpUtils {
    private const val MAX_CONNECTIONS = 50
    private val executor: ExecutorService = Executors.newFixedThreadPool(MAX_CONNECTIONS)

    val client: HttpClient =
        HttpClient.newBuilder().executor(executor).connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_2).followRedirects(HttpClient.Redirect.NORMAL)
            .priority(1)
            .build()
}

data class SvgItem(val name: String, val url: String, val svgContent: String, val fileName: String)

fun extractSvgItems(html: String): List<SvgItem> {
    val doc = Jsoup.parse(html)

    return doc.select("a[itemType='http://schema.org/ImageObject']").mapNotNull { anchor ->
        val img = anchor.selectFirst("img[itemProp=contentUrl]") ?: return@mapNotNull null
        val rawTitle = anchor.attr("title")

        Pair(
            rawTitle.removePrefix("Show ").removeSuffix(" SVG File").trim(), img.attr("src")
        )
    }.parallelStream().map { (name, url) ->
        try {
            val request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.10 Safari/605.1.1"
            ).build()

            val response = HttpUtils.client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                SvgItem(name, url, response.body(), getLastPart(url))
            } else null
        } catch (e: Exception) {
            null
        }
    }.filter { it != null }.toList().filterNotNull()
}

fun fetchSvgRepoPage(searchName: String, pageNumber: Int): String {
    val encodedSearch = URLEncoder.encode(searchName, "UTF-8")
    val url = "https://www.svgrepo.com/vectors/$encodedSearch/$pageNumber"

    val request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).header(
        "User-Agent",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.10 Safari/605.1.1"
    ).GET().build()

    val response = HttpUtils.client.send(request, HttpResponse.BodyHandlers.ofString())
    val statusCode = response.statusCode()

    if (statusCode == 200) {
        return response.body()
    } else {
        throw Exception("HTTP $statusCode")
    }
}

fun getLastPart(url: String): String {
    val trimmedUrl = url.trimEnd('/') // Remove trailing slash
    return trimmedUrl.substringAfterLast('/')
}
