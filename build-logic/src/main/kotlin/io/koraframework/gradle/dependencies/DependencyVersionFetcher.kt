package io.koraframework.gradle.dependencies

import io.koraframework.gradle.dependencies.VersionComparator.REGEX_PRE_RELEASE
import io.koraframework.gradle.dependencies.VersionComparator.numericVersionParts
import org.gradle.api.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

object DependencyVersionFetcher {

    private val OSV_URI = URI.create("https://api.osv.dev/v1/query")
    private val httpExecutor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "kora-dependency-fetcher-${threadId()}"
        }
    }
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "kora-dependency-retry-scheduler"
        }
    }
    private val httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .executor(httpExecutor)
        .build()

    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isExpandEntityReferences = false
    }

    private val REGEX_PLAIN_VERSION = Regex("\\d+(\\.\\d+)+")
    private val REGEX_TIMESTAMP_VERSION = Regex("\\d{8}\\.\\d{6}(\\.\\d+)?")

    private fun <T> sendWithRetryAsync(
        request: HttpRequest,
        bodyHandler: HttpResponse.BodyHandler<T>,
        logger: Logger,
        maxRetries: Int = 4,
        initialDelayMs: Long = 1000,
        attempt: Int = 0
    ): CompletableFuture<HttpResponse<T>> {
        return httpClient.sendAsync(request, bodyHandler)
            .handle { response, exception ->
                var shouldRetry = false
                var logMessage = ""

                if (exception != null) {
                    val cause = exception.cause ?: exception
                    if (cause is java.io.IOException && attempt < maxRetries) {
                        shouldRetry = true
                        logMessage = "Network error (${cause.javaClass.simpleName}: ${cause.message}) for ${request.uri()}."
                    }
                    if (!shouldRetry) throw exception
                } else if (response != null) {
                    val status = response.statusCode()
                    if ((status == 429 || status >= 500) && attempt < maxRetries) {
                        shouldRetry = true
                        val headersString = response.headers().map().entries.joinToString(", ") { (k, v) -> "$k=$v" }
                        logMessage = "Received HTTP $status for ${request.uri()}. Headers: [$headersString]"
                    }
                }

                if (shouldRetry) {
                    val retryAfterSeconds = response?.headers()?.firstValue("Retry-After")?.orElse(null)?.toLongOrNull()
                    val delayMs = if (retryAfterSeconds != null) {
                        retryAfterSeconds * 1000
                    } else {
                        initialDelayMs * (1 shl attempt)
                    }

                    logger.warn("$logMessage Retrying in ${delayMs}ms... (Attempt ${attempt + 1}/$maxRetries)")

                    val nextFuture = CompletableFuture<HttpResponse<T>>()
                    scheduler.schedule({
                        sendWithRetryAsync(request, bodyHandler, logger, maxRetries, initialDelayMs, attempt + 1)
                            .whenComplete { res, ex ->
                                if (ex != null) {
                                    nextFuture.completeExceptionally(ex)
                                } else {
                                    nextFuture.complete(res)
                                }
                            }
                    }, delayMs, TimeUnit.MILLISECONDS)

                    nextFuture
                } else {
                    CompletableFuture.completedFuture(response!!)
                }
            }
            .thenCompose { it }
    }

    fun fetchLatestVersion(
        group: String,
        artifact: String,
        current: String,
        repo: String,
        includePre: Boolean,
        level: String,
        logger: Logger
    ): CompletableFuture<String?> {
        val url = "$repo/${group.replace('.', '/')}/$artifact/maven-metadata.xml"
        logger.info("Fetching metadata async for $group:$artifact from $url")

        return try {
            val rq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)")
                .header("Accept", "application/xml, text/xml, */*")
                .GET()
                .build()

            sendWithRetryAsync(rq, HttpResponse.BodyHandlers.ofInputStream(), logger)
                .thenApply { rs ->
                    if (rs.statusCode() != 200) {
                        logger.warn("Unable to fetch metadata for $group:$artifact. HTTP Status: ${rs.statusCode()}")
                        return@thenApply null
                    }

                    val doc = documentBuilderFactory.newDocumentBuilder().parse(rs.body())
                    val versionNodes = doc.getElementsByTagName("version")

                    val versions = mutableListOf<String>()
                    for (i in 0 until versionNodes.length) {
                        versions.add(versionNodes.item(i).textContent)
                    }

                    var filtered = if (!includePre) {
                        versions.filter { !it.contains(REGEX_PRE_RELEASE) }
                    } else versions

                    if (current.matches(REGEX_PLAIN_VERSION)) {
                        val plain = filtered.filter { it.matches(REGEX_PLAIN_VERSION) && !it.matches(REGEX_TIMESTAMP_VERSION) }
                        if (plain.isNotEmpty()) filtered = plain
                    } else if (current.endsWith("-jre")) {
                        filtered = filtered.filter { it.endsWith("-jre") }
                    } else if (current.endsWith("-android")) {
                        filtered = filtered.filter { it.endsWith("-android") }
                    }

                    filtered = filtered.filter { isAllowedUpdate(current, it, level) }
                    val latest = filtered.sortedWith(VersionComparator).lastOrNull()

                    logger.debug("Latest allowed version for $group:$artifact is $latest (current: $current)")
                    latest
                }
                .exceptionally { e ->
                    logger.warn("Async fetch failed for $group:$artifact from $url. Reason: ${e.message}")
                    null
                }
        } catch (e: Exception) {
            logger.warn("Unable to construct async request for $group:$artifact. Reason: ${e.message}")
            CompletableFuture.completedFuture(null)
        }
    }

    fun queryOsvAsync(group: String, artifact: String, version: String, logger: Logger): CompletableFuture<String?> {

        logger.info("Checking vulnerabilities via OSV for $group:$artifact:$version")

        val payload = """{"package":{"name":"$group:$artifact","ecosystem":"Maven"},"version":"$version"}"""

        return try {
            val rq = HttpRequest.newBuilder()
                .uri(OSV_URI)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)")
                .POST(HttpRequest.BodyPublishers.ofString(payload, Charsets.UTF_8))
                .build()

            sendWithRetryAsync(rq, HttpResponse.BodyHandlers.ofString(), logger)
                .thenApply { response ->
                    if (response.statusCode() == 200) response.body() else null
                }
                .exceptionally { e ->
                    logger.warn("Async query to OSV failed for $group:$artifact:$version. Reason: ${e.message}")
                    null
                }
        } catch (e: Exception) {
            logger.warn("Unable to construct async request to OSV for $group:$artifact:$version. Reason: ${e.message}")
            CompletableFuture.completedFuture(null)
        }
    }

    private fun isAllowedUpdate(current: String, candidate: String, level: String): Boolean {
        if (level == "any" || level == "major") return true
        val cParts = numericVersionParts(current)
        val candidateParts = numericVersionParts(candidate)
        if (cParts.isEmpty() || candidateParts.isEmpty()) return true

        if ((level == "minor" || level == "patch") && candidateParts.getOrNull(0) != cParts.getOrNull(0)) return false
        if (level == "patch" && candidateParts.size > 1 && cParts.size > 1 && candidateParts.getOrNull(1) != cParts.getOrNull(1)) return false
        return true
    }
}
