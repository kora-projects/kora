package io.koraframework.gradle.dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
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

        val catalogLines = fileToParse.readLines(Charsets.UTF_8).toMutableList()
        var section = ""

        val versionValues = mutableMapOf<String, String>()
        val versionLineNumbers = mutableMapOf<String, Int>()
        val versionRefs = mutableMapOf<String, MutableList<CatalogCoord>>()
        val updates = mutableListOf<DependencyUpdate>()
        val checkedCoordinates = mutableListOf<Triple<String, String, String>>()

        catalogLines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()

            val sectionMatcher = PATTERN_SECTION.matcher(line)
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1)
                return@forEachIndexed
            }

            if (section == "versions") {
                val versionMatcher = PATTERN_VERSION.matcher(line)
                if (versionMatcher.find()) {
                    versionValues[versionMatcher.group(1)] = versionMatcher.group(2)
                    versionLineNumbers[versionMatcher.group(1)] = index
                }
            }

            if (section == "libraries") {
                val moduleMatcher = PATTERN_MODULE.matcher(line)
                if (moduleMatcher.find()) {
                    val coord = CatalogCoord(moduleMatcher.group(1), moduleMatcher.group(2), REPO_MAVEN)
                    val refMatcher = PATTERN_VERSION_REF.matcher(line)
                    val inlineMatcher = PATTERN_VERSION_INLINE.matcher(line)

                    if (refMatcher.find()) {
                        val vRef = refMatcher.group(1)
                        versionRefs.computeIfAbsent(vRef) { mutableListOf() }.add(coord)

                        val currentVersion = versionValues[vRef]
                        if (currentVersion != null) {
                            checkedCoordinates.add(Triple(coord.group, coord.artifact, currentVersion))
                        }
                    } else if (inlineMatcher.find()) {
                        val currentVersion = inlineMatcher.group(1)
                        val aliasMatcher = PATTERN_ALIAS.matcher(line)
                        val alias = if (aliasMatcher.find()) aliasMatcher.group(1) else null
                        val displayName = "$alias (${coord.group}:${coord.artifact}, inline:${index + 1})"

                        versionValues[displayName] = currentVersion
                        versionLineNumbers[displayName] = index
                        versionRefs.computeIfAbsent(displayName) { mutableListOf() }.add(coord)

                        checkedCoordinates.add(Triple(coord.group, coord.artifact, currentVersion))
                    }
                }
            }

            if (section == "plugins") {
                val pluginMatcher = PATTERN_PLUGIN.matcher(line)
                if (pluginMatcher.find()) {
                    val pId = pluginMatcher.group(1)
                    val coord = CatalogCoord(pId, "$pId.gradle.plugin", REPO_GRADLE_PLUGINS)
                    val refMatcher = PATTERN_VERSION_REF.matcher(line)
                    val inlineMatcher = PATTERN_VERSION_INLINE.matcher(line)

                    if (refMatcher.find()) {
                        val vRef = refMatcher.group(1)
                        versionRefs.computeIfAbsent(vRef) { mutableListOf() }.add(coord)
                    } else if (inlineMatcher.find()) {
                        val currentVersion = inlineMatcher.group(1)
                        val aliasMatcher = PATTERN_ALIAS.matcher(line)
                        val alias = if (aliasMatcher.find()) aliasMatcher.group(1) else null
                        val displayName = "$alias (${coord.group}:${coord.artifact}, inline:${index + 1})"
                        versionValues[displayName] = currentVersion
                        versionLineNumbers[displayName] = index
                        versionRefs.computeIfAbsent(displayName) { mutableListOf() }.add(coord)
                    }
                }
            }
        }

        if (reportVulnerabilities.get()) {
            OsvVulnerabilityReportBuilder.build(
                coordinates = checkedCoordinates,
                reportFile = vulnerabilityReportFile.get().asFile,
                logger = logger,
            )
        }

        if (!writeVersions.get() && !reportUpdates.get()) return

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

        val versionKeys = versionRefs.keys.sorted()
        val updateFutures = versionKeys.map { key ->
            val current = versionValues[key] ?: return@map CompletableFuture.completedFuture<DependencyUpdate?>(null)
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
                val selected = latestVersions.sortedWith(VersionComparator).lastOrNull()
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
            updates.forEach { markdown.add("- `${it.name}`: `${it.current}` -> `${it.latest}`") }
            markdown.add("")
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
            val line = rawLine.replace(REGEX_COMMENT, "").trim()
            if (line.isEmpty()) return@forEachLine
            val sectionMatcher = PATTERN_POLICY_SECTION.matcher(line)
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1)
                return@forEachLine
            }
            if (section == "version-levels") {
                val matcher = PATTERN_POLICY_ENTRY.matcher(line)
                if (matcher.matches()) {
                    policy[matcher.group(1)] = matcher.group(2)
                }
            }
        }
        return policy
    }

    private fun getEffectiveUpdateLevel(requested: String, policy: String?): String {
        if (policy == null) return requested
        val reqIdx = ORDERED_LEVELS.indexOf(requested).let { if (it == -1) 3 else it }
        val polIdx = ORDERED_LEVELS.indexOf(policy).let { if (it == -1) 3 else it }
        return ORDERED_LEVELS[minOf(reqIdx, polIdx)]
    }

    data class CatalogCoord(val group: String, val artifact: String, val repository: String)
    data class DependencyUpdate(val line: Int, val name: String, val current: String, val latest: String)

    companion object {
        private val ORDERED_LEVELS = listOf(
            "patch",
            "minor",
            "major",
            "any",
        )

        private val PATTERN_PLUGIN = Pattern.compile("id\\s*=\\s*\"([^\"]+)\"")
        private val PATTERN_ALIAS = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=")
        private val PATTERN_SECTION = Pattern.compile("^\\s*\\[\"?([A-Za-z0-9_.-]+)\"?\\s*]\\s*$")
        private val PATTERN_VERSION = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=\\s*\"([^\"]+)\"")
        private val PATTERN_VERSION_INLINE = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"")
        private val PATTERN_VERSION_REF = Pattern.compile("version\\.ref\\s*=\\s*\"([^\"]+)\"")
        private val PATTERN_MODULE = Pattern.compile("module\\s*=\\s*\"([^:\"]+):([^:\"]+)\"")
        private val PATTERN_POLICY_SECTION = Pattern.compile("^\\[([A-Za-z0-9_.-]+)]$")
        private val PATTERN_POLICY_ENTRY = Pattern.compile("^\"?([A-Za-z0-9_.:-]+)\"?\\s*=\\s*\"(any|major|minor|patch)\"$")
        private val REGEX_COMMENT = Pattern.compile("\\s*#.*$").toRegex()
        private const val REPO_MAVEN = "https://repo1.maven.org/maven2"
        private const val REPO_GRADLE_PLUGINS = "https://plugins.gradle.org/m2"
    }
}
