package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.Locale

/**
 * Compile-time check of CRON expressions declared in scheduling annotations.
 *
 * The check is intentionally incomplete: it reports only errors that the runtime parser of the target scheduler
 * rejects as well, and accepts every construct it does not model. Such expressions are still validated by the
 * scheduler on application start. `CronValidatorTest` compares the check with the real parsers.
 */
internal object CronValidator {

    enum class Dialect(val annotation: String, val format: String, private val dailyExample: String, private val weekdaysExample: String) {
        JDK(
            "@ScheduleJdkWithCron", """
            The JDK scheduler expects 5, 6 or 7 fields, the second and year fields are optional:
            ┌───────────── second (0-59, optional)
            │ ┌───────────── minute (0-59)
            │ │ ┌───────────── hour (0-23)
            │ │ │ ┌───────────── day of the month (1-31 or ?)
            │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
            │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT, 0 and 1 are Sunday, or ?)
            │ │ │ │ │ │ ┌───────────── year (1970-2099 or ?, optional)
            │ │ │ │ │ │ │
            * * * * * * *
            Modifiers L, W, # and C are not supported.
            """.trimIndent(), "0 0 15 * * ?", "0 0 9-17 * * MON-FRI"
        ),
        QUARTZ(
            "@ScheduleQuartzWithCron", """
            Quartz expects 6 or 7 fields, the year field is optional, and exactly one of day-of-month and day-of-week must be '?':
            ┌───────────── second (0-59)
            │ ┌───────────── minute (0-59)
            │ │ ┌───────────── hour (0-23)
            │ │ │ ┌───────────── day of the month (1-31, L, W or ?)
            │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
            │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT, 1 is Sunday, L, # or ?)
            │ │ │ │ │ │ ┌───────────── year (optional)
            │ │ │ │ │ │ │
            * * * ? * * *
            """.trimIndent(), "0 0 15 * * ?", "0 0 9-17 ? * MON-FRI"
        ),
        DB(
            "@ScheduleDbWithCron", """
            The database scheduler expects exactly 6 fields, a macro such as @daily, or '-' to disable the job:
            ┌───────────── second (0-59)
            │ ┌───────────── minute (0-59)
            │ │ ┌───────────── hour (0-23)
            │ │ │ ┌───────────── day of the month (1-31, L, W or ?)
            │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
            │ │ │ │ │ ┌───────────── day of the week (0-7 or MON-SUN, 0 and 7 are Sunday, L, # or ?)
            │ │ │ │ │ │
            * * * * * *
            """.trimIndent(), "0 0 15 * * *", "0 0 9-17 * * MON-FRI"
        );

        /**
         * Expressions accepted by the scheduler, described in [examplesDescription].
         */
        val examples: List<String>
            get() = listOf(dailyExample, weekdaysExample)

        val examplesDescription: String
            get() = "Examples:\n" +
                "  '$dailyExample' runs every day at 15:00\n" +
                "  '$weekdaysExample' runs every hour from 9:00 through 17:00 on weekdays"
    }

    private enum class Kind { SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH, DAY_OF_WEEK, YEAR }

    private data class Field(val kind: Kind, val name: String, val min: Int, val max: Int, val names: Map<String, Int>, val noSpecific: Boolean)

    private sealed interface Parsed {
        data class Value(val value: Int) : Parsed
        data class Error(val message: String) : Parsed
        data object Unknown : Parsed
    }

    private val simpleElement = Regex("^(\\*|[0-9]+|[A-Z]+)(?:-([0-9]+|[A-Z]+))?(?:/([0-9]+))?$")

    private val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
    )
    private val daysFromSunday = mapOf("SUN" to 1, "MON" to 2, "TUE" to 3, "WED" to 4, "THU" to 5, "FRI" to 6, "SAT" to 7)
    private val daysFromMonday = mapOf("MON" to 1, "TUE" to 2, "WED" to 3, "THU" to 4, "FRI" to 5, "SAT" to 6, "SUN" to 7)

    private val second = Field(Kind.SECOND, "second", 0, 59, emptyMap(), false)
    private val minute = Field(Kind.MINUTE, "minute", 0, 59, emptyMap(), false)
    private val hour = Field(Kind.HOUR, "hour", 0, 23, emptyMap(), false)
    private val dayOfMonth = Field(Kind.DAY_OF_MONTH, "day-of-month", 1, 31, emptyMap(), true)
    private val month = Field(Kind.MONTH, "month", 1, 12, months, false)

    private val jdkFields = listOf(
        second, minute, hour, dayOfMonth, month,
        Field(Kind.DAY_OF_WEEK, "day-of-week", 0, 7, daysFromSunday, true),
        Field(Kind.YEAR, "year", 1970, 2099, emptyMap(), true),
    )

    // Quartz accepts any year value, so the year field is not checked
    private val quartzFields = listOf(
        second, minute, hour, dayOfMonth, month,
        Field(Kind.DAY_OF_WEEK, "day-of-week", 1, 7, daysFromSunday, true),
        null,
    )
    private val dbFields = listOf(
        second, minute, hour, dayOfMonth, month,
        Field(Kind.DAY_OF_WEEK, "day-of-week", 0, 7, daysFromMonday, true),
    )

    /**
     * @throws ProcessingErrorException when the scheduler would reject the non-blank expression
     */
    fun check(dialect: Dialect, expression: String?, type: KSClassDeclaration, function: KSFunctionDeclaration) {
        if (expression.isNullOrBlank()) {
            return
        }
        val error = validate(dialect, expression) ?: return
        throw ProcessingErrorException(
            "Invalid CRON expression '$expression' in ${dialect.annotation} on '${type.qualifiedName?.asString()}#${function.simpleName.asString()}()': $error.\n" +
                "${dialect.format}\n${dialect.examplesDescription}\nSee the Javadoc of ${dialect.annotation} for details.",
            function
        )
    }

    /**
     * @return description of an error the scheduler would report for the expression, or `null` when no such error is found
     */
    fun validate(dialect: Dialect, expression: String): String? {
        val trimmed = expression.trim()
        if (dialect == Dialect.DB && (trimmed == "-" || trimmed.startsWith("@"))) {
            return null
        }
        var values = trimmed.split(Regex("\\s+"))
        val fields = when (dialect) {
            Dialect.JDK -> {
                if (values.size !in 5..7) {
                    null
                } else {
                    if (values.size == 5) {
                        values = listOf("0") + values
                    }
                    jdkFields
                }
            }

            Dialect.QUARTZ -> if (values.size !in 6..7) null else quartzFields
            Dialect.DB -> if (values.size != 6) null else dbFields
        }
        if (fields == null) {
            return "expression has ${if (trimmed.isEmpty()) 0 else values.size} fields"
        }

        for ((i, value) in values.withIndex()) {
            val field = fields[i] ?: continue
            val error = validateField(dialect, field, value.uppercase(Locale.ROOT))
            if (error != null) {
                return error
            }
        }

        if (dialect == Dialect.QUARTZ) {
            val dayOfMonth = values[3]
            val dayOfWeek = values[5]
            val dayOfMonthUnspecified = dayOfMonth == "?"
            val dayOfWeekUnspecified = dayOfWeek == "?"
            val simple = (dayOfMonthUnspecified || !dayOfMonth.contains("?")) && (dayOfWeekUnspecified || !dayOfWeek.contains("?"))
                && !quartzSpecialValue(dayOfMonth) && !quartzSpecialValue(dayOfWeek)
            if (simple && dayOfMonthUnspecified == dayOfWeekUnspecified) {
                return "'?' must be used in exactly one of the day-of-month and day-of-week fields"
            }
        }
        return null
    }

    private fun validateField(dialect: Dialect, field: Field, value: String): String? {
        if (value == "?") {
            return if (field.noSpecific) null else "'?' is not allowed in the ${field.name} field"
        }
        for (element in value.split(",")) {
            if (element.contains("?")) {
                // the JDK scheduler reads '?' inside a list as '*', Quartz accepts it after a value, for example '*-?'
                if (!field.noSpecific && dialect != Dialect.JDK && element.startsWith("?")) {
                    return "'?' is not allowed in the ${field.name} field"
                }
                continue
            }
            if (dialect == Dialect.JDK) {
                val error = jdkModifier(field, element)
                if (error != null) {
                    return error
                }
            }
            val match = simpleElement.matchEntire(element) ?: continue
            val base = match.groupValues[1]
            val end = match.groupValues[2].ifEmpty { null }
            val step = match.groupValues[3].ifEmpty { null }
            if (base == "*" && end != null) {
                continue
            }

            var from: Int? = null
            if (base != "*") {
                when (val parsed = parseValue(dialect, field, base)) {
                    is Parsed.Unknown -> continue
                    is Parsed.Error -> return parsed.message
                    is Parsed.Value -> from = parsed.value
                }
            }
            var to: Int? = null
            // Quartz does not check the end of a range
            if (end != null && dialect != Dialect.QUARTZ) {
                when (val parsed = parseValue(dialect, field, end)) {
                    is Parsed.Unknown -> continue
                    is Parsed.Error -> return parsed.message
                    is Parsed.Value -> to = parsed.value
                }
            }
            if (from != null && to != null && from > to && rejectsReversedRange(dialect, field)) {
                return "${field.name} range $element is reversed"
            }
            if (step != null) {
                val increment = if (step.length > 9) Long.MAX_VALUE else step.toLong()
                if (increment == 0L && dialect != Dialect.QUARTZ) {
                    return "${field.name} step must be positive in $element"
                }
                if (increment > field.max && dialect != Dialect.JDK && field.kind != Kind.DAY_OF_MONTH && field.kind != Kind.MONTH && field.kind != Kind.DAY_OF_WEEK) {
                    return "${field.name} step $step is greater than ${field.max}"
                }
            }
        }
        return null
    }

    private fun parseValue(dialect: Dialect, field: Field, token: String): Parsed {
        if (token[0].isDigit()) {
            val value = if (token.length > 9) Long.MAX_VALUE else token.toLong()
            // Quartz uses 98 and 99 internally for '?' and '*' and accepts them in every field
            if (dialect == Dialect.QUARTZ && (value == 98L || value == 99L)) {
                return Parsed.Unknown
            }
            if (value < field.min || value > field.max) {
                return Parsed.Error("${field.name} value $token is out of range ${field.min}-${field.max}")
            }
            if (dialect == Dialect.JDK && field.kind == Kind.DAY_OF_WEEK && value == 0L) {
                return Parsed.Value(1)
            }
            return Parsed.Value(value.toInt())
        }
        field.names[token]?.let { return Parsed.Value(it) }
        val dayField = field.kind == Kind.DAY_OF_MONTH || field.kind == Kind.DAY_OF_WEEK
        if (dialect != Dialect.JDK && dayField && (token.contains('L') || token.contains('W') || token.contains('C'))) {
            return Parsed.Unknown
        }
        if (dialect == Dialect.QUARTZ && field.names.isNotEmpty()) {
            // Quartz reads only the first three letters of a name, so longer words and modifiers may be valid
            return Parsed.Unknown
        }
        return Parsed.Error("${field.name} value $token is not supported")
    }

    /**
     * The JDK scheduler rejects every value with L, W, # or C modifiers unless the value is a month or day name.
     */
    private fun jdkModifier(field: Field, element: String): String? {
        for (value in element.split('-', '/')) {
            if (field.names.containsKey(value)) {
                continue
            }
            for (modifier in charArrayOf('L', 'W', '#', 'C')) {
                if (value.indexOf(modifier) >= 0) {
                    return "${field.name} modifier $modifier in $element is not supported by the JDK scheduler"
                }
            }
        }
        return null
    }

    private fun rejectsReversedRange(dialect: Dialect, field: Field) = when (dialect) {
        Dialect.JDK -> true
        Dialect.QUARTZ -> false
        Dialect.DB -> field.kind == Kind.SECOND || field.kind == Kind.MINUTE || field.kind == Kind.HOUR
    }

    private fun quartzSpecialValue(value: String) = value.contains("98") || value.contains("99")
}
