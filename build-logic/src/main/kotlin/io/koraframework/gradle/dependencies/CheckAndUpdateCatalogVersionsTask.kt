package io.koraframework.gradle.dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

abstract class CheckAndUpdateCatalogVersionsTask : DefaultTask() {

    @get:InputFile
    abstract val catalogFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    abstract val versionPolicyFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val vulnerabilityReportFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val updateReportFile: RegularFileProperty

    @get:Input
    @get:Option(option = "writeVersions", description = "Write changes back to libs.versions.toml")
    @get:Optional
    abstract val writeVersions: Property<Boolean>

    @get:Input
    @get:Option(option = "includePreRelease", description = "Include alpha, beta, rc versions")
    @get:Optional
    abstract val includePreRelease: Property<Boolean>

    @get:Input
    @get:Option(option = "updateLevel", description = "Allowed update scope: any, major, minor, patch")
    @get:Optional
    abstract val updateLevel: Property<String>

    @get:Input
    @get:Option(option = "reportVulnerabilities", description = "Query OSV.dev and write Markdown vulnerability report")
    @get:Optional
    abstract val reportVulnerabilities: Property<Boolean>

    @get:Input
    @get:Option(option = "reportUpdates", description = "Write Markdown dependency updates report")
    @get:Optional
    abstract val reportUpdates: Property<Boolean>

    init {
        group = "version catalog"
        description = "Checks gradle/libs.versions.toml against Maven metadata and updates it."
        writeVersions.convention(false)
        includePreRelease.convention(false)
        updateLevel.convention("any")
        reportVulnerabilities.convention(false)
        reportUpdates.convention(false)
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun execute() {
        val fileToParse = catalogFile.get().asFile
        val level = updateLevel.get().lowercase(Locale.ROOT)

        if (!fileToParse.isFile) throw GradleException("Catalog file not found at: $fileToParse")

        val versionPolicy = if (versionPolicyFile.isPresent) {
            val policyFile = versionPolicyFile.get().asFile
            if (policyFile.isFile) {
                logger.lifecycle("Using version policy $policyFile:")
                val parsedPolicy = readVersionPolicy(policyFile)
                parsedPolicy.keys.sorted().forEach { key ->
                    logger.lifecycle("  $key: ${parsedPolicy[key]}")
                }
                parsedPolicy
            } else emptyMap()
        } else emptyMap()

        val catalogLines = fileToParse.readLines(Charsets.UTF_8).toMutableList()
        var section = ""

        val versionValues = mutableMapOf<String, String>()
        val versionLineNumbers = mutableMapOf<String, Int>()
        val versionRefs = mutableMapOf<String, MutableList<CatalogCoord>>()
        val updates = mutableListOf<DependencyUpdate>()
        val checkedCoordinates = mutableListOf<Triple<String, String, String>>()

        catalogLines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()

            val sectionMatcher = SECTION_PATTERN.matcher(line)
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1)
                return@forEachIndexed
            }

            if (section == "versions") {
                val versionMatcher = VERSION_PATTERN.matcher(line)
                if (versionMatcher.find()) {
                    versionValues[versionMatcher.group(1)] = versionMatcher.group(2)
                    versionLineNumbers[versionMatcher.group(1)] = index
                }
            }

            if (section == "libraries") {
                val moduleMatcher = MODULE_PATTERN.matcher(line)
                val refMatcher = REF_PATTERN.matcher(line)
                if (moduleMatcher.find() && refMatcher.find()) {
                    val coord = CatalogCoord(moduleMatcher.group(1), moduleMatcher.group(2), REPO_URI)
                    val vRef = refMatcher.group(1)
                    versionRefs.computeIfAbsent(vRef) { mutableListOf() }.add(coord)

                    val currentVersion = versionValues[vRef]
                    if (currentVersion != null) {
                        checkedCoordinates.add(Triple(coord.group, coord.artifact, currentVersion))
                    }
                }
            }
        }

        if (reportVulnerabilities.get()) {
            executeVulnerabilityReport(checkedCoordinates)
            if (!writeVersions.get() && !reportUpdates.get()) return
        }

        val versionKeys = versionRefs.keys.sorted()
        val updateFutures = versionKeys.map { key ->
            val current = versionValues[key]!!
            val coords = versionRefs[key]!!
            val policyLevel = versionPolicy[key]
            val effectiveLevel = getEffectiveUpdateLevel(level, policyLevel)

            if (policyLevel != null && policyLevel != level) {
                logger.lifecycle("Policy $key: requested=$level, policy=$policyLevel, effective=$effectiveLevel")
            }

            val coordFutures = coords.map { coord ->
                DependencyVersionFetcher.fetchLatestVersion(
                    coord.group, coord.artifact, current, coord.repository, includePreRelease.get(), effectiveLevel, logger
                )
            }

            CompletableFuture.allOf(*coordFutures.toTypedArray()).thenApply {
                val latestVersions = coordFutures.mapNotNull { it.get() }
                val selected = latestVersions.sortedWith(Comparator(DependencyVersionFetcher::compareVersions)).lastOrNull()
                if (selected != null && selected != current) {
                    DependencyUpdate(versionLineNumbers[key]!!, key, current, selected)
                } else null
            }
        }

        CompletableFuture.allOf(*updateFutures.toTypedArray()).join()

        updateFutures.mapNotNull { it.get() }.forEach { updates.add(it) }

        if (reportUpdates.get()) {
            generateUpdateMarkdownReport(updates, level)
        }

        if (updates.isEmpty()) {
            logger.lifecycle("No version updates found.")
            return
        }

        updates.forEach { logger.lifecycle("${it.name}: ${it.current} -> ${it.latest}") }

        if (!writeVersions.get()) {
            logger.lifecycle("\nDry run completed. Run with -PwriteVersions=true to save changes.")
            return
        }

        updates.forEach { update ->
            catalogLines[update.line] = catalogLines[update.line].replace("\"${update.current}\"", "\"${update.latest}\"")
        }
        fileToParse.writeText(catalogLines.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
        logger.lifecycle("Updated $fileToParse successfully!")
    }

    private fun executeVulnerabilityReport(coordinates: List<Triple<String, String, String>>) {
        val reportFile = vulnerabilityReportFile.get().asFile
        val distinctCoords = coordinates.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        logger.lifecycle("Vulnerability report via OSV.dev for ${distinctCoords.size} unique Maven coordinates...")

        val markdown = mutableListOf<String>().apply {
            add("<!-- kora-dependency-vulnerability-report -->")
            add("## Dependency Vulnerability Report")
            add("")
            add("Checked `${distinctCoords.size}` Maven coordinates from `gradle/libs.versions.toml` via OSV.dev.")
            add("")
        }

        val idPattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
        val summaryPattern = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]+)\"")

        data class OsvResult(
            val coord: Triple<String, String, String>,
            val jsonResponse: String?,
            val idPat: Pattern,
            val sumPat: Pattern
        )

        val futures = distinctCoords.map { coord ->
            DependencyVersionFetcher.queryOsvAsync(coord.first, coord.second, coord.third, logger).thenApply { json ->
                OsvResult(coord, json, idPattern, summaryPattern)
            }
        }

        val futuresArray = futures.map { it as CompletableFuture<*> }.toTypedArray()
        CompletableFuture.allOf(*futuresArray).join()

        var vulnerabilityCount = 0
        var vulnerableCoordinatesCount = 0

        futures.forEach { future ->
            val result = future.get()
            if (result.jsonResponse == null) return@forEach

            val (group, artifact, version) = result.coord
            val idMatcher = result.idPat.matcher(result.jsonResponse)
            val summaryMatcher = result.sumPat.matcher(result.jsonResponse)

            val vulns = mutableListOf<Pair<String, String>>()
            while (idMatcher.find() && summaryMatcher.find()) {
                vulns.add(Pair(idMatcher.group(1), summaryMatcher.group(1)))
            }

            if (vulns.isNotEmpty()) {
                vulnerableCoordinatesCount++
                vulnerabilityCount += vulns.size

                logger.lifecycle("$group:$artifact:$version")
                markdown.add("### `$group:$artifact:$version`")
                markdown.add("")

                vulns.forEach { (id, summary) ->
                    logger.lifecycle("  - $id: $summary")
                    markdown.add("- `$id`: $summary")
                }
                markdown.add("")
            }
        }

        if (vulnerabilityCount == 0) {
            logger.lifecycle("No vulnerabilities found.")
            markdown.add("No vulnerabilities found.")
        } else {
            logger.lifecycle("Found vulnerabilityCount vulnerabilities across $vulnerableCoordinatesCount coordinates.")
            markdown.add(3, "**Found `$vulnerabilityCount` vulnerabilities across `$vulnerableCoordinatesCount` coordinates.**")
            markdown.add(4, "")
        }

        reportFile.parentFile.mkdirs()
        reportFile.writeText(markdown.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
        logger.lifecycle("Wrote vulnerability report to $reportFile")
    }

    private fun generateUpdateMarkdownReport(updates: List<DependencyUpdate>, level: String) {
        val reportFile = updateReportFile.get().asFile
        val markdown = mutableListOf<String>().apply {
            add("<!-- kora-dependency-update-report -->")
            add("## Dependency Update Report")
            add("")
            add("Update level: `$level`")
            add("")
        }

        if (updates.isEmpty()) {
            markdown.add("No dependency updates found.")
        } else {
            markdown.add("**Found `${updates.size}` dependency updates.**")
            markdown.add("")
            markdown.add("### `gradle/libs.versions.toml`")
            markdown.add("")
            updates.forEach {
                markdown.add("- `${it.name}`: `${it.current}` -> `${it.latest}`")
            }
        }

        reportFile.parentFile.mkdirs()
        reportFile.writeText(markdown.joinToString(System.lineSeparator()) + System.lineSeparator(), Charsets.UTF_8)
        logger.lifecycle("Wrote dependency update report to $reportFile")
    }

    private fun readVersionPolicy(policyFile: File): Map<String, String> {
        if (!policyFile.isFile) return emptyMap()
        val policy = mutableMapOf<String, String>()
        var section = ""
        policyFile.forEachLine(Charsets.UTF_8) { rawLine ->
            val line = rawLine.replace(COMMENT_REGEX, "").trim()
            if (line.isEmpty()) return@forEachLine
            val sectionMatcher = POLICY_SECTION_PATTERN.matcher(line)
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1)
                return@forEachLine
            }
            if (section == "version-levels") {
                val matcher = POLICY_ENTRY_PATTERN.matcher(line)
                if (matcher.matches()) {
                    policy[matcher.group(1)] = matcher.group(2)
                }
            }
        }
        return policy
    }

    private fun getEffectiveUpdateLevel(requested: String, policy: String?): String {
        if (policy == null) return requested
        val order = listOf("patch", "minor", "major", "any")
        val reqIdx = order.indexOf(requested).let { if (it == -1) 3 else it }
        val polIdx = order.indexOf(policy).let { if (it == -1) 3 else it }
        return order[minOf(reqIdx, polIdx)]
    }

    data class CatalogCoord(val group: String, val artifact: String, val repository: String)
    data class DependencyUpdate(val line: Int, val name: String, val current: String, val latest: String)

    companion object {
        private val SECTION_PATTERN = Pattern.compile("^\\s*\\[\"?([A-Za-z0-9_.-]+)\"?\\s*]\\s*$")
        private val VERSION_PATTERN = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=\\s*\"([^\"]+)\"")
        private val MODULE_PATTERN = Pattern.compile("module\\s*=\\s*\"([^:\"]+):([^:\"]+)\"")
        private val REF_PATTERN = Pattern.compile("version\\.ref\\s*=\\s*\"([^\"]+)\"")

        private val COMMENT_REGEX = Regex("\\s*#.*$")
        private val POLICY_SECTION_PATTERN = Pattern.compile("^\\[([A-Za-z0-9_.-]+)]$")
        private val POLICY_ENTRY_PATTERN = Pattern.compile("^\"?([A-Za-z0-9_.:-]+)\"?\\s*=\\s*\"(any|major|minor|patch)\"$")

        private const val REPO_URI = "https://repo1.maven.org/maven2"
    }
}
