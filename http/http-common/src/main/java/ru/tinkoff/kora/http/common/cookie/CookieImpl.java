package ru.tinkoff.kora.http.common.cookie;

import java.time.ZonedDateTime;

public final class CookieImpl implements Cookie {
    private final String name;
    private String value;
    private String path;
    private String domain;
    private Integer maxAge;
    private ZonedDateTime expires;
    private boolean discard;
    private boolean secure;
    private boolean httpOnly;
    private int version = 0;
    private String comment;
    private boolean sameSite;
    private String sameSiteMode;

    public CookieImpl(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    public CookieImpl(final String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public CookieImpl setValue(final String value) {
        this.value = value;
        return this;
    }

    public String path() {
        return path;
    }

    public CookieImpl setPath(final String path) {
        this.path = path;
        return this;
    }

    public String domain() {
        return domain;
    }

    public CookieImpl setDomain(final String domain) {
        this.domain = domain;
        return this;
    }

    public Integer maxAge() {
        return maxAge;
    }

    public CookieImpl setMaxAge(final Integer maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public boolean isDiscard() {
        return discard;
    }

    public CookieImpl setDiscard(final boolean discard) {
        this.discard = discard;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public CookieImpl setSecure(final boolean secure) {
        this.secure = secure;
        return this;
    }

    public int version() {
        return version;
    }

    public CookieImpl setVersion(final int version) {
        this.version = version;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public CookieImpl setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public ZonedDateTime expires() {
        return expires;
    }

    public CookieImpl setExpires(final ZonedDateTime expires) {
        this.expires = expires;
        return this;
    }

    public String comment() {
        return comment;
    }

    public Cookie setComment(final String comment) {
        this.comment = comment;
        return this;
    }

    @Override
    public boolean isSameSite() {
        return sameSite;
    }

    @Override
    public Cookie setSameSite(final boolean sameSite) {
        this.sameSite = sameSite;
        return this;
    }

    @Override
    public String sameSiteMode() {
        return sameSiteMode;
    }

    @Override
    public Cookie setSameSiteMode(final String mode) {
        final String m = CookieSameSiteMode.lookupModeString(mode);
        if (m != null) {
            this.sameSiteMode = m;
            this.setSameSite(true);
        }
        return this;
    }

    @Override
    public final int hashCode() {
        int result = 17;
        result = 37 * result + (name() == null ? 0 : name().hashCode());
        result = 37 * result + (path() == null ? 0 : path().hashCode());
        result = 37 * result + (domain() == null ? 0 : domain().hashCode());
        return result;
    }

    @Override
    public final boolean equals(final Object other) {
        if (other == this) return true;
        if (!(other instanceof Cookie)) return false;
        final Cookie o = (Cookie) other;
        // compare names
        if (name() == null && o.name() != null) return false;
        if (name() != null && !name().equals(o.name())) return false;
        // compare paths
        if (path() == null && o.path() != null) return false;
        if (path() != null && !path().equals(o.path())) return false;
        // compare domains
        if (domain() == null && o.domain() != null) return false;
        if (domain() != null && !domain().equals(o.domain())) return false;
        // same cookie
        return true;
    }

    @Override
    public final String toString() {
        return "{CookieImpl@" + System.identityHashCode(this) + " name=" + name() + " path=" + path() + " domain=" + domain() + "}";
    }
}
