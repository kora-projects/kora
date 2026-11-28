package io.koraframework.json.common;

import java.time.*;
import java.time.chrono.*;
import java.time.format.*;

import static java.time.temporal.ChronoField.*;

public final class KoraDateTimeFormatters {

    private KoraDateTimeFormatters() {}

    public static final Month[] KORA_MONTHS = Month.values();
    public static final DayOfWeek[] KORA_DAY_OF_WEEKS = DayOfWeek.values();

    /**
     * uuuu
     */
    public static final DateTimeFormatter KORA_ISO_YEAR = new DateTimeFormatterBuilder()
        .appendValue(YEAR, 4, 4, SignStyle.EXCEEDS_PAD)
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);

    /**
     * uuuu-MM
     */
    public static final DateTimeFormatter KORA_ISO_YEAR_MONTH = new DateTimeFormatterBuilder()
        .append(KORA_ISO_YEAR)
        .appendLiteral('-')
        .appendValue(MONTH_OF_YEAR, 2)
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);

    /**
     * MM-dd
     */
    public static final DateTimeFormatter KORA_ISO_MONTH_DAY = new DateTimeFormatterBuilder()
        .appendValue(MONTH_OF_YEAR, 2)
        .appendLiteral('-')
        .appendValue(DAY_OF_MONTH, 2)
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);

    /**
     * uuuu-MM-dd
     */
    public static final DateTimeFormatter KORA_ISO_LOCAL_DATE = new DateTimeFormatterBuilder()
        .append(KORA_ISO_YEAR_MONTH)
        .appendLiteral('-')
        .appendValue(DAY_OF_MONTH, 2)
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);

    /**
     * HH:mm:ss[.SSS]
     */
    public static final DateTimeFormatter KORA_ISO_LOCAL_TIME = new DateTimeFormatterBuilder()
        .appendValue(HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(MINUTE_OF_HOUR, 2)
        .appendLiteral(':')
        .appendValue(SECOND_OF_MINUTE, 2)
        .appendFraction(NANO_OF_SECOND, 3, 3, true)
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT);

    /**
     * uuuu-MM-dd'T'HH:mm:ss[.SSS]
     */
    public static final DateTimeFormatter KORA_ISO_LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
        .append(KORA_ISO_LOCAL_DATE)
        .appendLiteral('T')
        .append(KORA_ISO_LOCAL_TIME)
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);

    /**
     * HH:mm:ss[.SSS]XXX
     */
    public static final DateTimeFormatter KORA_ISO_OFFSET_TIME = new DateTimeFormatterBuilder()
        .append(KORA_ISO_LOCAL_TIME)
        .appendOffsetId()
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT);

    /**
     * uuuu-MM-dd'T'HH:mm:ss[.SSS]XXX
     */
    public static final DateTimeFormatter KORA_ISO_OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
        .append(KORA_ISO_LOCAL_DATE_TIME)
        .appendOffsetId()
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);

    /**
     * uuuu-MM-dd'T'HH:mm:ss[.SSS]XXX['['VV']']
     */
    public static final DateTimeFormatter KORA_ISO_ZONED_DATE_TIME = new DateTimeFormatterBuilder()
        .append(KORA_ISO_OFFSET_DATE_TIME)
        .optionalStart()
        .appendLiteral('[')
        .parseCaseSensitive()
        .appendZoneRegionId()
        .appendLiteral(']')
        .optionalEnd()
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);
}
