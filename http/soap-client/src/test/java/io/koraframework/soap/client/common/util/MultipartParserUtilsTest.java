package io.koraframework.soap.client.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartParserUtilsTest {

    @Test
    void parseMetaBoundaryLast() {
        var meta = MultipartParserUtils.parseMeta("multipart/related; type=\"application/xop+xml\"; start=\"<root>\"; start-info=\"text/xml\"; boundary=\"uuid:abc\"");
        assertThat(meta.boundary()).isEqualTo("uuid:abc");
        assertThat(meta.start()).isEqualTo("<root>");
    }

    @Test
    void parseMetaStartLast() {
        var meta = MultipartParserUtils.parseMeta("multipart/related; type=\"application/xop+xml\"; boundary=\"uuid:abc\"; start=\"<root>\"");
        assertThat(meta.boundary()).isEqualTo("uuid:abc");
        assertThat(meta.start()).isEqualTo("<root>");
    }

    @Test
    void parseMetaUnspacedUnquotedAndCaseInsensitive() {
        var meta = MultipartParserUtils.parseMeta("multipart/related;start-info=\"text/xml\";Boundary=uuid:abc;start=\"<root>\";type=\"application/xop+xml\"");
        assertThat(meta.boundary()).isEqualTo("uuid:abc");
        assertThat(meta.start()).isEqualTo("<root>");
    }

    @Test
    void partHeadersWithoutSpaceAfterColon() {
        var b = "uuid:1";
        var body = ("--" + b + "\r\n"
            + "Content-Type:application/xop+xml\r\n"
            + "Content-ID:<root>\r\n"
            + "\r\n"
            + "<x/>\r\n"
            + "--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8);
        var parts = MultipartParserUtils.parse(body, b);
        assertThat(parts).containsKey("<root>");
        assertThat(parts.get("<root>").contentType()).isEqualTo("application/xop+xml");
    }
}
