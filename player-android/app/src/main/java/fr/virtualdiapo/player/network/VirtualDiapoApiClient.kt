package fr.virtualdiapo.player.network

import fr.virtualdiapo.player.model.Slide
import fr.virtualdiapo.player.model.SlideCollection
import fr.virtualdiapo.player.model.CollectionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class VirtualDiapoApiClient {
    suspend fun checkServer(address: String) = withContext(Dispatchers.IO) {
        getJsonObject("${normalizeAddress(address)}/api/v1/server")
        Unit
    }
    suspend fun loadCollections(address: String): List<CollectionSummary> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeAddress(address)
        val summaries = getJsonArray("$baseUrl/api/v1/collections")
        buildList {
            for (index in 0 until summaries.length()) {
                val item = summaries.getJSONObject(index)
                add(CollectionSummary(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    description = item.optString("description").takeIf(String::isNotBlank),
                    year = item.optInt("year").takeIf { item.has("year") && !item.isNull("year") },
                    slideCount = item.getInt("slideCount"),
                ))
            }
        }
    }

    suspend fun loadCollection(address: String, id: String): SlideCollection = withContext(Dispatchers.IO) {
        val baseUrl = normalizeAddress(address)
        parseCollection(getJsonObject("$baseUrl/api/v1/collections/$id"), baseUrl)
    }

    internal fun normalizeAddress(address: String): String {
        val withScheme = if (address.startsWith("http://") || address.startsWith("https://")) {
            address
        } else {
            "http://$address"
        }
        val uri = URI(withScheme.trim().trimEnd('/'))
        require(uri.host != null) { "Adresse du serveur invalide" }
        val port = if (uri.port == -1) 8080 else uri.port
        return URI(uri.scheme, null, uri.host, port, null, null, null).toString().trimEnd('/')
    }

    private fun getJsonArray(url: String): JSONArray = JSONArray(get(url))

    private fun getJsonObject(url: String): JSONObject = JSONObject(get(url))

    private fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) {
                error("Le serveur a répondu HTTP $status")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCollection(json: JSONObject, baseUrl: String): SlideCollection {
        val slidesJson = json.getJSONArray("slides")
        val slides = buildList {
            for (index in 0 until slidesJson.length()) {
                val slide = slidesJson.getJSONObject(index)
                add(
                    Slide(
                        id = slide.getString("id"),
                        position = slide.getInt("position"),
                        imageUrl = baseUrl + slide.getString("imagePath"),
                    ),
                )
            }
        }.sortedBy(Slide::position)

        return SlideCollection(
            id = json.getString("id"),
            title = json.getString("title"),
            description = json.optString("description").takeIf(String::isNotBlank),
            year = json.optInt("year").takeIf { json.has("year") && !json.isNull("year") },
            slides = slides,
        )
    }
}

internal fun connectionErrorMessage(error: Throwable): String = when (error) {
    is SocketTimeoutException -> "Le serveur ne répond pas. Vérifiez le réseau et réessayez."
    is ConnectException -> "Connexion refusée. Vérifiez que VirtualDiapo est lancé sur l’ordinateur."
    is UnknownHostException -> "Serveur introuvable. Vérifiez l’adresse ou la connexion réseau."
    is IllegalArgumentException -> error.message ?: "Adresse du serveur invalide."
    else -> error.message?.takeIf(String::isNotBlank) ?: "Connexion impossible."
}
