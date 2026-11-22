package com.npk.kfv.dialogs

import com.npk.kfv.APPLICATION_VERSION
import com.npk.swing.ViewModel
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingWorker

class UpdateViewModel : ViewModel() {

    private val latestReleaseUri =  URI.create("https://api.github.com/repos/akolomiets/kafka-reader/releases/latest")
    private val htmlUrlRegex =      "\"html_url\"\\s?:\\s?\"(?<htmlUrl>https://github.com/akolomiets/kafka-reader/releases/.+?)\"".toRegex()
    private val tagNameRegex =      "\"tag_name\"\\s?:\\s?\"(?<tagName>.+?)\"".toRegex()

    private val logger = Logger.getLogger(this.javaClass.getName())

    private var latestReleaseWorker: SwingWorker<HttpResponse<String>, Unit>? = null

    fun checkForUpdates(callback: (Result<Triple<Int, String, URI?>>) -> Unit) {
        latestReleaseWorker = object : SwingWorker<HttpResponse<String>, Unit>() {
            override fun doInBackground(): HttpResponse<String> {
                logger.log(Level.INFO, "Request latest application release")
                return HttpClient
                    .newBuilder()
                    .proxy(ProxySelector.getDefault())
                    .build()
                    .send(
                        HttpRequest
                            .newBuilder(latestReleaseUri)
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
                    )
            }
            override fun done() {
                try {
                    val response = get()
                    logger.log(Level.FINE, "Response received. StatusCode - ${response.statusCode()}")

                    check(response.statusCode() == 200) { "Unexpected status code: ${response.statusCode()}" }
                    response.body().let { body ->
                        val tagName = requireNotNull(tagNameRegex.find(body)?.groups?.get("tagName")?.value) { "tag_name property not found in the response body" }

                        val args = if (APPLICATION_VERSION in tagName) {
                            Triple(200, tagName, null)
                        } else {
                            val htmlUrl = requireNotNull(htmlUrlRegex.find(body)?.groups?.get("htmlUrl")?.value) { "html_url property not found in the response body" }
                            Triple(201, tagName, URI.create(htmlUrl))
                        }

                        callback(Result.success(args))
                    }
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Error while requesting release info", e)
                    callback(Result.failure(if (e is ExecutionException) e.cause ?: e else e))
                }
            }
        }.also { it.execute() }
    }

}