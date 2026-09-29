package io.koraframework.http.common.cookie;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CookiesTest {

    @Test
    void parseRequestCookiesPreservesTrailingEqualSign() {
        // base64 values end with '=' padding which must not be dropped when allowEqualInValue is false
        var header = "fake_session=123456789abc; fake_token=123456789xyz=; fake_visit=123456789123";

        var cookies = parse(false, header);

        assertThat(cookies).extracting(Cookie::name).containsExactlyInAnyOrder("fake_session", "fake_token", "fake_visit");
        assertThat(cookies).extracting(Cookie::value).containsExactlyInAnyOrder("123456789abc", "123456789xyz=", "123456789123");
    }

    @Test
    void parseRequestCookiesSplitsOnEqualSignInsideValue() {
        // an '=' followed by a value character still separates cookies when allowEqualInValue is false
        var cookies = parse(false, "a=b=c; d=e");

        assertThat(cookies).extracting(Cookie::name).containsExactlyInAnyOrder("a", "d");
        assertThat(cookies).extracting(Cookie::value).containsExactlyInAnyOrder("b", "e");
    }

    @Test
    void parseRequestCookiesEmptyValue() {
        var cookies = parse(false, "a=; b=c");

        assertThat(cookies).extracting(Cookie::name).containsExactlyInAnyOrder("a", "b");
        assertThat(cookies).extracting(Cookie::value).containsExactlyInAnyOrder("", "c");
    }

    @Test
    void parseRequestCookiesNameOnlyIsDropped() {
        // a cookie without '=' has no value and is ignored
        var cookies = parse(false, "a; b=c");

        assertThat(cookies).extracting(Cookie::name).containsExactly("b");
        assertThat(cookies).extracting(Cookie::value).containsExactly("c");
    }

    @Test
    void parseRequestCookiesJustEqualSign() {
        var cookies = parse(false, "a=");

        assertThat(cookies).extracting(Cookie::name).containsExactly("a");
        assertThat(cookies).extracting(Cookie::value).containsExactly("");
    }

    @Test
    void parseRequestCookiesQuotedValueWithEqualSign() {
        var cookies = parse(false, "a=\"b=c\"");

        assertThat(cookies).extracting(Cookie::name).containsExactly("a");
        assertThat(cookies).extracting(Cookie::value).containsExactly("b=c");
    }

    @Test
    void parseRequestCookiesQuotedValueWithSemicolon() {
        var cookies = parse(false, "a=\"b;c\"");

        assertThat(cookies).extracting(Cookie::name).containsExactly("a");
        assertThat(cookies).extracting(Cookie::value).containsExactly("b;c");
    }

    @Test
    void parseRequestCookiesQuotedValueWithEscapedQuote() {
        var cookies = parse(false, "a=\"b\\\"c\"");

        assertThat(cookies).extracting(Cookie::name).containsExactly("a");
        assertThat(cookies).extracting(Cookie::value).containsExactly("b\"c");
    }

    @Test
    void parseRequestCookiesTrailingEqualBeforeSemicolon() {
        var cookies = parse(false, "a=b=; c=d");

        assertThat(cookies).extracting(Cookie::name).containsExactlyInAnyOrder("a", "c");
        assertThat(cookies).extracting(Cookie::value).containsExactlyInAnyOrder("b=", "d");
    }

    @Test
    void parseRequestCookiesMultipleTrailingEquals() {
        // a run of trailing '=' (e.g. base64 padding) is kept entirely, not split into new cookies
        var cookies = parse(false, "a=b===");

        assertThat(cookies).extracting(Cookie::name).containsExactly("a");
        assertThat(cookies).extracting(Cookie::value).containsExactly("b===");
    }

    @Test
    void parseRequestCookiesDoubleTrailingEquals() {
        // base64 values may end with '==' (e.g. ory_hydra_session)
        var cookies = parse(false, "a=abc==; b=x");

        assertThat(cookies).extracting(Cookie::name).containsExactlyInAnyOrder("a", "b");
        assertThat(cookies).extracting(Cookie::value).containsExactlyInAnyOrder("abc==", "x");
    }


    @Test
    void parseRequestCookiesLeadingWhitespaceIsTrimmed() {
        var cookies = parse(false, "  a=b  ;  c=d");

        assertThat(cookies).extracting(Cookie::name).containsExactlyInAnyOrder("a", "c");
        assertThat(cookies).extracting(Cookie::value).containsExactlyInAnyOrder("b  ", "d");
    }

    @Test
    void parseRequestCookiesEmptyHeader() {
        var cookies = parse(false, "");

        assertThat(cookies).isEmpty();
    }
    @Test
    void parseRequestCookiesDuplicateNameKeepsFirst() {
        var cookies = parse(false, "a=b; a=c");

        assertThat(cookies).extracting(Cookie::name).containsExactly("a");
        assertThat(cookies).extracting(Cookie::value).containsExactly("b");
    }

    @Test
    void parseRequestCookiesRfc2109AttributesBecomeCookies() {
        var cookies = parse(false, "a=b; $Domain=x; $Path=/; $Version=1");

        assertThat(cookies).extracting(Cookie::name)
            .containsExactlyInAnyOrder("a", "$Domain", "$Path", "$Version");
        assertThat(cookies).extracting(Cookie::value)
            .containsExactlyInAnyOrder("b", "x", "/", "1");
    }

    @Test
    void parseRequestCookiesAllowEqualInValueKeepsAllEquals() {
        var cookies = parse(true, "a=b=c=d");

        assertThat(cookies).extracting(Cookie::name).containsExactly("a");
        assertThat(cookies).extracting(Cookie::value).containsExactly("b=c=d");
    }

    @Test
    void parseRequestCookiesAllowEqualInValueStillSplitsOnSemicolon() {
        var cookies = parse(true, "a=b=; c=d");

        assertThat(cookies).extracting(Cookie::name).containsExactlyInAnyOrder("a", "c");
        assertThat(cookies).extracting(Cookie::value).containsExactlyInAnyOrder("b=", "d");
    }

    private static List<Cookie> parse(boolean allowEqualInValue, String header) {
        var cookies = new ArrayList<Cookie>();
        Cookies.parseRequestCookies(500, allowEqualInValue, List.of(header), cookies);
        return cookies;
    }
}