package io.koraframework.gradle.dependencies

import org.gradle.api.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

object DependencyVersionFetcher {

    private val httpExecutor = Executors.newFixedThreadPool(8) { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "kora-dependency-fetcher-${threadId()}"
        }
    }
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .executor(httpExecutor)
        .build()

    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isExpandEntityReferences = false
    }

    private val PRE_RELEASE_REGEX = Regex("(?i)(alpha|beta|rc|snapshot|milestone|preview|ea|[.-]M\\d+)")
    private val PLAIN_VERSION_REGEX = Regex("\\d+(\\.\\d+)+")
    private val TIMESTAMP_VERSION_REGEX = Regex("\\d{8}\\.\\d{6}(\\.\\d+)?")
    private val DIGITS_PATTERN = Pattern.compile("\\d+")

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
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)")
                .header("Accept", "application/xml, text/xml, */*")
                .GET()
                .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply { response ->
                    if (response.statusCode() != 200) {
                        logger.warn("Unable to fetch metadata for $group:$artifact. HTTP Status: ${response.statusCode()}")
                        return@thenApply null
                    }

                    val builder = documentBuilderFactory.newDocumentBuilder()
                    val doc = builder.parse(response.body())
                    val versionNodes = doc.getElementsByTagName("version")

                    val versions = mutableListOf<String>()
                    for (i in 0 until versionNodes.length) {
                        versions.add(versionNodes.item(i).textContent)
                    }

                    var filtered = if (!includePre) {
                        versions.filter { !it.contains(PRE_RELEASE_REGEX) }
                    } else versions

                    if (current.matches(PLAIN_VERSION_REGEX)) {
                        val plain = filtered.filter { it.matches(PLAIN_VERSION_REGEX) && !it.matches(TIMESTAMP_VERSION_REGEX) }
                        if (plain.isNotEmpty()) filtered = plain
                    } else if (current.endsWith("-jre")) {
                        filtered = filtered.filter { it.endsWith("-jre") }
                    } else if (current.endsWith("-android")) {
                        filtered = filtered.filter { it.endsWith("-android") }
                    }

                    filtered = filtered.filter { isAllowedUpdate(current, it, level) }
                    val latest = filtered.sortedWith(Comparator(::compareVersions)).lastOrNull()

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
        val url = "https://osv.dev"
        val payload = """{"package":{"name":"$group:$artifact","ecosystem":"Maven"},"version":"$version"}"""

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 KoraFrameworkDependencyUpdates/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(payload, Charsets.UTF_8))
                .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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

    fun compareVersions(left: String, right: String): Int {
        val leftParts = numericVersionParts(left)
        val rightParts = numericVersionParts(right)
        val max = maxOf(leftParts.size, rightParts.size)

        for (i in 0 until max) {
            val lNum = leftParts.getOrNull(i) ?: 0L
            val rNum = rightParts.getOrNull(i) ?: 0L
            if (lNum != rNum) return lNum.compareTo(rNum)
        }

        val leftRank = getQualifierRank(left)
        val rightRank = getQualifierRank(right)
        if (leftRank != rightRank) return leftRank.compareTo(rightRank)

        return left.compareTo(right)
    }

    private fun numericVersionParts(version: String): List<Long> {
        if (version.isEmpty()) return emptyList()
        val matcher = DIGITS_PATTERN.matcher(version)
        val parts = mutableListOf<Long>()
        while (matcher.find()) {
            parts.add(matcher.group().toLongOrNull() ?: 0L)
        }
        return parts
    }

    private fun getQualifierRank(version: String): Int {
        val lower = version.lowercase(Locale.ROOT)
        return when {
            lower.contains("alpha") -> 0
            lower.contains("beta") -> 1
            lower.contains("rc") || lower.contains("cr") -> 2
            !lower.contains(PRE_RELEASE_REGEX) -> 4
            else -> 3
        }
    }

    private fun isAllowedUpdate(current: String, candidate: String, level: String): Boolean {
        if (level == "any" || level == "major") return true
        val cParts = numericVersionParts(current)
        val candidateParts = numericVersionParts(candidate)
        if (cParts.isEmpty() || candidateParts.isEmpty()) return true

        if ((level == "minor" || level == "patch") && candidateParts != cParts) return false
        if (level == "patch" && candidateParts.size > 1 && cParts.size > 1 && candidateParts != cParts) return false
        return true
    }
}
