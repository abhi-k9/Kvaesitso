package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

@Serializable
data class WikipediaSearchResult(
    val query: WikipediaSearchResultQuery?,
)

@Serializable
data class WikipediaSearchResultQuery(
    val pages: Map<String, WikipediaSearchResultQueryPage>,
)

@Serializable
data class WikipediaSearchResultQueryPage(
    val pageid: Long,
    val title: String,
    val extract: String,
    val thumbnail: WikipediaSearchResultQueryPageThumnail?,
    val fullurl: String,
    val canonicalurl: String,
)

@Serializable
data class WikipediaSearchResultQueryPageThumnail(
    val source: String
)

private val LanguageVariant = Regex("[a-z]{2,3}(-[a-z0-9]{2,8})+")

internal class WikipediaApi(
    private val context: Context,
    var baseUrl: String?,
) {
    private val appVersion =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName

    val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
            install(UserAgent) {
                agent = "${context.packageName}/v$appVersion"
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 200
                requestTimeoutMillis = 3000
                socketTimeoutMillis = 1000
            }
            defaultRequest {
                url(baseUrl)
            }
        }
    }

    suspend fun search(baseUrl: String, query: String, thumbnailSize: Int): WikipediaSearchResult {
        return httpClient.get {
            url {
                takeFrom(baseUrl)
                val segments = pathSegments.filter { it.isNotEmpty() }
                if (segments.isEmpty()) {
                    path("w", "api.php")
                } else if (segments.size == 1 && LanguageVariant.matches(segments[0])) {
                    // A language variant, e.g. https://zh.wikipedia.org/zh-tw/ (the default for
                    // Traditional Chinese): the API isn't below that path
                    path("w", "api.php")
                    parameter("variant", segments[0])
                }
                parameter("action", "query")
                parameter("generator", "search")
                parameter("redirects", "true")
                parameter("gsrlimit", "1")
                parameter("prop", "extracts|info|pageimages")
                parameter("exchars", "500")
                parameter("exintro", "true")
                parameter("inprop", "url")
                parameter("format", "json")
                parameter("gsrsearch", query)
                parameter("pithumbsize", thumbnailSize)
            }
        }.body()
    }
}