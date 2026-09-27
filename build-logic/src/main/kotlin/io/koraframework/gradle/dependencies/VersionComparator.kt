package io.koraframework.gradle.dependencies

import java.util.Locale
import java.util.regex.Pattern

object VersionComparator : Comparator<String> {

    val PATTERN_DIGITS: Pattern = Pattern.compile("\\d+")
    val REGEX_PRE_RELEASE = Regex("(?i)(alpha|beta|rc|snapshot|milestone|preview|ea|[.-]M\\d+)")

    override fun compare(left: String?, right: String?): Int {

        if (left == right) return 0
        if (left == null) return -1
        if (right == null) return 1

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

    fun numericVersionParts(version: String): List<Long> {
        if (version.isEmpty()) return emptyList()
        val matcher = PATTERN_DIGITS.matcher(version)
        val parts = mutableListOf<Long>()
        while (matcher.find()) {
            parts.add(matcher.group().toLongOrNull() ?: 0L)
        }
        return parts
    }

    fun getQualifierRank(version: String): Int {
        val lower = version.lowercase(Locale.ROOT)
        return when {
            lower.contains("alpha") -> 0
            lower.contains("beta") -> 1
            lower.contains("rc") || lower.contains("cr") -> 2
            !lower.contains(REGEX_PRE_RELEASE) -> 4
            else -> 3
        }
    }
}
