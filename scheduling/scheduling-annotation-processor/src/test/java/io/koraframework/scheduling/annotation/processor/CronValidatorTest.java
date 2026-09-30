package io.koraframework.scheduling.annotation.processor;

import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import io.koraframework.scheduling.annotation.processor.CronValidator.Dialect;
import io.koraframework.scheduling.jdk.util.CronExpression;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CronValidatorTest {

    private static final int FUZZ_EXPRESSIONS = 30_000;

    @ParameterizedTest
    @MethodSource("validExpressions")
    void acceptsExpressionAcceptedByScheduler(Dialect dialect, String expression) {
        assertThat(schedulerAccepts(dialect, expression)).as("scheduler accepts '%s'", expression).isTrue();
        assertThat(CronValidator.validate(dialect, expression)).as("validator accepts '%s'", expression).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidExpressions")
    void rejectsExpressionRejectedByScheduler(Dialect dialect, String expression) {
        assertThat(schedulerAccepts(dialect, expression)).as("scheduler rejects '%s'", expression).isFalse();
        assertThat(CronValidator.validate(dialect, expression)).as("validator rejects '%s'", expression).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("skippedExpressions")
    void skipsExpressionItDoesNotModel(Dialect dialect, String expression) {
        assertThat(schedulerAccepts(dialect, expression)).as("scheduler rejects '%s'", expression).isFalse();
        assertThat(CronValidator.validate(dialect, expression)).as("validator skips '%s'", expression).isNull();
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void formatHintIsAcceptedByScheduler(Dialect dialect) {
        var template = dialect.format().lines()
            .filter(line -> line.matches("[*? ]+"))
            .findFirst()
            .orElseThrow();

        var expressions = new ArrayList<>(dialect.examples());
        expressions.add(template);
        for (var expression : expressions) {
            assertThat(schedulerAccepts(dialect, expression)).as("scheduler accepts '%s'", expression).isTrue();
            assertThat(CronValidator.validate(dialect, expression)).as("validator accepts '%s'", expression).isNull();
        }
    }

    @ParameterizedTest
    @EnumSource(Dialect.class)
    void neverRejectsExpressionAcceptedByScheduler(Dialect dialect) {
        var falseErrors = new ArrayList<String>();
        var caught = 0;
        var invalid = 0;
        for (var expression : fuzzExpressions(dialect)) {
            var error = CronValidator.validate(dialect, expression);
            var accepted = schedulerAccepts(dialect, expression);
            if (error != null && accepted) {
                falseErrors.add("'" + expression + "': " + error);
            }
            if (!accepted) {
                invalid++;
                if (error != null) {
                    caught++;
                }
            }
        }

        assertThat(falseErrors).as("expressions accepted by %s scheduler but rejected by validator", dialect).isEmpty();
        // the check is incomplete by design, but it must still catch most of the invalid expressions
        assertThat(caught).as("caught %d of %d invalid expressions", caught, invalid).isGreaterThan(invalid / 2);
    }

    static boolean schedulerAccepts(Dialect dialect, String expression) {
        try {
            switch (dialect) {
                case JDK -> CronExpression.parse(expression);
                case QUARTZ -> org.quartz.CronExpression.validateExpression(expression);
                case DB -> new CronSchedule(expression, ZoneOffset.UTC);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Stream<Arguments> validExpressions() {
        return Stream.of(
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
            Arguments.of(Dialect.DB, "+5 0 12 * * *")
        );
    }

    static Stream<Arguments> invalidExpressions() {
        return Stream.of(
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
            Arguments.of(Dialect.DB, "0 0 12 * * MONDAY")
        );
    }

    static Stream<Arguments> skippedExpressions() {
        return Stream.of(
            Arguments.of(Dialect.JDK, "0 0 12 ,1 * ?"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * FOO"),
            Arguments.of(Dialect.QUARTZ, "0 0 12 ? * 2#6"),
            Arguments.of(Dialect.DB, "0 0 12 5C * *"),
            Arguments.of(Dialect.DB, "@reboot")
        );
    }

    private static final String[] VALUES = {
        "*", "?", "0", "1", "5", "7", "8", "12", "13", "23", "24", "31", "32", "59", "60", "61", "99", "012", "99999999999",
        "1969", "1970", "2027", "2099", "2100", "3000", "+5", "-5",
        "SUN", "MON", "FRI", "SAT", "mon", "MONDAY", "JAN", "DEC", "jan", "JANUARY", "FOO", "A",
        "L", "W", "LW", "C", "5L", "L-3", "15W", "5C", "MON#2", "2#6", "#", ""
    };

    static Set<String> fuzzExpressions(Dialect dialect) {
        var random = new Random(dialect.ordinal() * 31L + 7);
        var expressions = new LinkedHashSet<String>();
        expressions.add("");
        expressions.add("@daily");
        expressions.add("@reboot");
        expressions.add("-");
        while (expressions.size() < FUZZ_EXPRESSIONS) {
            var count = 4 + random.nextInt(5);
            var fields = new ArrayList<String>(count);
            for (int i = 0; i < count; i++) {
                fields.add(randomField(random));
            }
            // mostly valid shapes keep the fuzzer close to the boundaries of every rule
            if (random.nextInt(3) > 0) {
                fields = baseline(dialect, random);
                var index = random.nextInt(fields.size());
                fields.set(index, randomField(random));
            }
            var separator = random.nextInt(10) == 0 ? "  " : " ";
            expressions.add(String.join(separator, fields));
        }
        return expressions;
    }

    private static ArrayList<String> baseline(Dialect dialect, Random random) {
        var fields = new ArrayList<>(List.of("0", "0", "12", "*", "*", "?"));
        switch (dialect) {
            case JDK -> {
                if (random.nextBoolean()) {
                    fields.add("2027");
                }
            }
            case QUARTZ -> {
                if (random.nextBoolean()) {
                    fields.set(3, "?");
                    fields.set(5, "*");
                }
                if (random.nextBoolean()) {
                    fields.add("2027");
                }
            }
            case DB -> {
                if (random.nextBoolean()) {
                    fields.set(5, "*");
                }
            }
        }
        return fields;
    }

    private static String randomField(Random random) {
        var elements = 1 + (random.nextInt(4) == 0 ? random.nextInt(3) : 0);
        var parts = new ArrayList<String>(elements);
        for (int i = 0; i < elements; i++) {
            var element = VALUES[random.nextInt(VALUES.length)];
            switch (random.nextInt(4)) {
                case 0 -> element = element + "-" + VALUES[random.nextInt(VALUES.length)];
                case 1 -> element = element + "/" + VALUES[random.nextInt(VALUES.length)];
                default -> {
                }
            }
            parts.add(element);
        }
        var field = String.join(",", parts);
        return field.isEmpty() ? "*" : field;
    }
}
