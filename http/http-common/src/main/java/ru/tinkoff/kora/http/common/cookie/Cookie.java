package ru.tinkoff.kora.http.common.cookie;

import java.time.ZonedDateTime;

public interface Cookie extends Comparable<Cookie> {

    String name();

    String value();

    Cookie setValue(final String value);

    String path();

    Cookie setPath(final String path);

    String domain();

    Cookie setDomain(final String domain);

    Integer maxAge();

    Cookie setMaxAge(final Integer maxAge);

    boolean isDiscard();

    Cookie setDiscard(final boolean discard);

    boolean isSecure();

    Cookie setSecure(final boolean secure);

    int version();

    Cookie setVersion(final int version);

    boolean isHttpOnly();

    Cookie setHttpOnly(final boolean httpOnly);

    ZonedDateTime expires();

    Cookie setExpires(final ZonedDateTime expires);

    String comment();

    Cookie setComment(final String comment);

    boolean isSameSite();

    Cookie setSameSite(final boolean sameSite);

    String sameSiteMode();

    Cookie setSameSiteMode(final String mode);

    @Override
    default int compareTo(final Cookie other) {
        int retVal = 0;

        // compare names
        if (name() == null && other.name() != null) return -1;
        if (name() != null && other.name() == null) return 1;
        retVal = (name() == null && other.name() == null) ? 0 : name().compareTo(other.name());
        if (retVal != 0) return retVal;

        // compare paths
        if (path() == null && other.path() != null) return -1;
        if (path() != null && other.path() == null) return 1;
        retVal = (path() == null && other.path() == null) ? 0 : path().compareTo(other.path());
        if (retVal != 0) return retVal;

        // compare domains
        if (domain() == null && other.domain() != null) return -1;
        if (domain() != null && other.domain() == null) return 1;
        retVal = (domain() == null && other.domain() == null) ? 0 : domain().compareTo(other.domain());
        if (retVal != 0) return retVal;

        return 0; // equal
    }
}
