package io.koraframework.scheduling.symbol.processor

import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule
import io.koraframework.scheduling.jdk.util.CronExpression
import io.koraframework.scheduling.symbol.processor.CronValidator.Dialect
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneOffset
import java.util.Random

internal class CronValidatorTest {

    @ParameterizedTest
    @MethodSource("validExpressions")
    fun acceptsExpressionAcceptedByScheduler(dialect: Dialect, expression: String) {
        assertThat(schedulerAccepts(dialect, expression)).`as`("scheduler accepts '%s'", expression).isTrue()
        assertThat(CronValidator.validate(dialect, expression)).`as`("validator accepts '%s'", expression).isNull()
    }

    @ParameterizedTest
    @MethodSource("invalidExpressions")
    fun rejectsExpressionRejectedByScheduler(dialect: Dialect, expression: String) {
        assertThat(schedulerAccepts(dialect, expression)).`as`("scheduler rejects '%s'", expression).isFalse()
        assertThat(CronValidator.validate(dialect, expression)).`as`("validator rejects '%s'", expression).isNotNull()
    }

    @ParameterizedTest
    @MethodSource("skippedExpressions")
    fun skipsExpressionItDoesNotModel(dialect: Dialect, expression: String) {
        assertThat(schedulerAccepts(dialect, expression)).`as`("scheduler rejects '%s'", expression).isFalse()
        assertThat(CronValidator.validate(dialect, expression)).`as`("validator skips '%s'", expression).isNull()
    }

    @ParameterizedTest
    @EnumSource(Dialect::class)
    fun formatHintIsAcceptedByScheduler(dialect: Dialect) {
        val template = dialect.format.lines().first { it.matches(Regex("[*? ]+")) }

        for (expression in dialect.examples + template) {
            assertThat(schedulerAccepts(dialect, expression)).`as`("scheduler accepts '%s'", expression).isTrue()
            assertThat(CronValidator.validate(dialect, expression)).`as`("validator accepts '%s'", expression).isNull()
        }
    }

    @ParameterizedTest
    @EnumSource(Dialect::class)
    fun neverRejectsExpressionAcceptedByScheduler(dialect: Dialect) {
        val falseErrors = ArrayList<String>()
        var caught = 0
        var invalid = 0
        for (expression in fuzzExpressions(dialect)) {
            val error = CronValidator.validate(dialect, expression)
            val accepted = schedulerAccepts(dialect, expression)
            if (error != null && accepted) {
                falseErrors.add("'$expression': $error")
            }
            if (!accepted) {
                invalid++
                if (error != null) {
                    caught++
                }
            }
        }

        assertThat(falseErrors).`as`("expressions accepted by %s scheduler but rejected by validator", dialect).isEmpty()
        // the check is incomplete by design, but it must still catch most of the invalid expressions
        assertThat(caught).`as`("caught %d of %d invalid expressions", caught, invalid).isGreaterThan(invalid / 2)
    }

    companion object {
        private const val FUZZ_EXPRESSIONS = 30_000

        private val values = arrayOf(
            "*", "?", "0", "1", "5", "7", "8", "12", "13", "23", "24", "31", "32", "59", "60", "61", "99", "012", "99999999999",
            "1969", "1970", "2027", "2099", "2100", "3000", "+5", "-5",
            "SUN", "MON", "FRI", "SAT", "mon", "MONDAY", "JAN", "DEC", "jan", "JANUARY", "FOO", "A",
            "L", "W", "LW", "C", "5L", "L-3", "15W", "5C", "MON#2", "2#6", "#", ""
        )

        fun schedulerAccepts(dialect: Dialect, expression: String): Boolean {
            return try {
                when (dialect) {
                    Dialect.JDK -> CronExpression.parse(expression)
                    Dialect.QUARTZ -> org.quartz.CronExpression.validateExpression(expression)
                    Dialect.DB -> CronSchedule(expression, ZoneOffset.UTC)
                }
                true
            } catch (e: Exception) {
                false
            }
        }

        @JvmStatic
        fun validExpressions() = listOf(
            Arguments.of(Dialect.JDK, "0 * * * * *"),
            Arguments.of(Dialect.JDK, "*/10 * * * * *"),
            Arguments.of(Dialect.JDK, "0 0 * * * ?"),
            Arguments.of(Dialect.JDK, "0 0 6,19 * * ?"),
            Arguments.of(Dialect.JDK, "0 0/30 8-10 * * ?"),
            Arguments.of(Dialect.JDK, "0 0 9-17 ? * MON-FRI"),
            Arguments.of(Dialect.JDK, "0 0 0 25 DEC ?"),
            Arguments.of(Dialect.JDK, "0 0 0 1 JAN ? 2027"),
            Arguments.of(Dialect.JDK, "0 0 * * *"),
            Arguments.of(Dialect.JDK, "0 0 12 * * 0-7"),
            Arguments.of(Dialect.JDK, "0 0 12 * * mon"),
            Arguments.of(Dialect.JDK, "?,5 0 12 * * ?"),
            Arguments.of(Dialect.JDK, "+5 0 12 * * ?"),
            Arguments.of(Dialect.JDK, "0 0/61 12 * * ?"),
            Arguments.of(Dialect.JDK, "  0  0  12 * * ?  "),
            Arguments.of(Dialect.QUARTZ, "0 0 12 * * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * MON-FRI"),
            Arguments.of(Dialect.QUARTZ, "0 0 22-2 * * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * SAT-SUN"),
            Arguments.of(Dialect.QUARTZ, "0 0/0 12 * * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 L-3 * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 15W * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * 5L"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * MON#2"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * MONDAY"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 * L ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 * * ? 3000"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 1,,2 * ?"),
            Arguments.of(Dialect.DB, "0 0 12 * * *"),
            Arguments.of(Dialect.DB, "0 0 12 ? * ?"),
            Arguments.of(Dialect.DB, "0 0 9-17 * * MON-FRI"),
            Arguments.of(Dialect.DB, "0 0 12 * * 7-0"),
            Arguments.of(Dialect.DB, "0 0 0 L * *"),
            Arguments.of(Dialect.DB, "0 0 0 LW * *"),
            Arguments.of(Dialect.DB, "0 0 0 * * MON#2"),
            Arguments.of(Dialect.DB, "0 0 0 ? * 5L"),
            Arguments.of(Dialect.DB, "@daily"),
            Arguments.of(Dialect.DB, "@midnight"),
            Arguments.of(Dialect.DB, "-"),
            Arguments.of(Dialect.DB, "+5 0 12 * * *"),
        )

        @JvmStatic
        fun invalidExpressions() = listOf(
            Arguments.of(Dialect.JDK, "i can't cron"),
            Arguments.of(Dialect.JDK, "0 0 12 * * ? 2027 5"),
            Arguments.of(Dialect.JDK, "? 0 12 * * *"),
            Arguments.of(Dialect.JDK, "60 0 12 * * ?"),
            Arguments.of(Dialect.JDK, "0 0 24 * * ?"),
            Arguments.of(Dialect.JDK, "0 0 12 0 * ?"),
            Arguments.of(Dialect.JDK, "0 0 12 ? * 8"),
            Arguments.of(Dialect.JDK, "0 0 12 * * ? 2100"),
            Arguments.of(Dialect.JDK, "0 0 12 L * ?"),
            Arguments.of(Dialect.JDK, "0 0 12 ? * MON#2"),
            Arguments.of(Dialect.JDK, "0 0 12 ? * MONDAY"),
            Arguments.of(Dialect.JDK, "0 0 22-2 * * ?"),
            Arguments.of(Dialect.JDK, "0 0 12 ? * SAT-SUN"),
            Arguments.of(Dialect.JDK, "0 0/0 12 * * ?"),
            Arguments.of(Dialect.JDK, "0 0 12 * MON ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 * *"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * * 2027 5"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 * * *"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 1 * MON"),
            Arguments.of(Dialect.QUARTZ, "?,5 0 12 * * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * 0"),
            Arguments.of(Dialect.QUARTZ, "0 0 A * * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0/60 12 * * ?"),
            Arguments.of(Dialect.QUARTZ, "@daily"),
            Arguments.of(Dialect.DB, "0 0 12 * *"),
            Arguments.of(Dialect.DB, "0 0 12 1 1 ? 2027"),
            Arguments.of(Dialect.DB, "0 0 ? * * *"),
            Arguments.of(Dialect.DB, "?/5 0 12 * * *"),
            Arguments.of(Dialect.DB, "0 0 12 ? * 8"),
            Arguments.of(Dialect.DB, "0 0 22-2 * * *"),
            Arguments.of(Dialect.DB, "0 0/0 12 * * *"),
            Arguments.of(Dialect.DB, "0 0/60 12 * * *"),
            Arguments.of(Dialect.DB, "0 0 12 * FOO *"),
            Arguments.of(Dialect.DB, "0 0 12 * * MONDAY"),
        )

        @JvmStatic
        fun skippedExpressions() = listOf(
            Arguments.of(Dialect.JDK, "0 0 12 ,1 * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * FOO"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * 2#6"),
            Arguments.of(Dialect.DB, "0 0 12 5C * *"),
            Arguments.of(Dialect.DB, "@reboot"),
        )

        fun fuzzExpressions(dialect: Dialect): Set<String> {
            val random = Random(dialect.ordinal * 31L + 7)
            val expressions = LinkedHashSet<String>()
            expressions.add("")
            expressions.add("@daily")
            expressions.add("@reboot")
            expressions.add("-")
            while (expressions.size < FUZZ_EXPRESSIONS) {
                val count = 4 + random.nextInt(5)
                var fields = MutableList(count) { randomField(random) }
                // mostly valid shapes keep the fuzzer close to the boundaries of every rule
                if (random.nextInt(3) > 0) {
                    fields = baseline(dialect, random)
                    fields[random.nextInt(fields.size)] = randomField(random)
                }
                val separator = if (random.nextInt(10) == 0) "  " else " "
                expressions.add(fields.joinToString(separator))
            }
            return expressions
        }

        private fun baseline(dialect: Dialect, random: Random): MutableList<String> {
            val fields = mutableListOf("0", "0", "12", "*", "*", "?")
            when (dialect) {
                Dialect.JDK -> if (random.nextBoolean()) fields.add("2027")
                Dialect.QUARTZ -> {
                    if (random.nextBoolean()) {
                        fields[3] = "?"
                        fields[5] = "*"
                    }
                    if (random.nextBoolean()) fields.add("2027")
                }

                Dialect.DB -> if (random.nextBoolean()) fields[5] = "*"
            }
            return fields
        }

        private fun randomField(random: Random): String {
            val elements = 1 + if (random.nextInt(4) == 0) random.nextInt(3) else 0
            val parts = List(elements) {
                var element = values[random.nextInt(values.size)]
                when (random.nextInt(4)) {
                    0 -> element = element + "-" + values[random.nextInt(values.size)]
                    1 -> element = element + "/" + values[random.nextInt(values.size)]
                }
                element
            }
            val field = parts.joinToString(",")
            return field.ifEmpty { "*" }
        }
    }
}
