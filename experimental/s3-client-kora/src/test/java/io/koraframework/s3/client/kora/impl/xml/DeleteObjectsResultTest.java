package io.koraframework.s3.client.kora.impl.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteObjectsResultTest {

    static Stream<String> keys() {
        return Stream.of(
            "plain",
            " leading-space",
            "trailing-space ",
            "  both  ",
            " ",
            "   ",
            "inner  double  space",
            "tab\there",
            "\ttab-edges\t",
            "new\nline",
            "\nnewline-edges\n",
            "carriage\rreturn",
            "a & b",
            "&",
            "&&&",
            " & ",
            "<tag>",
            "quote\"apos'",
            "a &amp; literal",
            "]]>",
            "юникод ключ",
            "emoji 😀 pair",
            " nbsp ",
            " em-space ",
            "dir/with space/ file ",
            "x".repeat(1024),
            " " + "y & ".repeat(250) + " "
        );
    }

    static Stream<Arguments> keysWithEncodings() {
        return keys().flatMap(key -> Stream.of(
            Arguments.of(key, "entities", (Function<String, String>) DeleteObjectsResultTest::escape),
            Arguments.of(key, "char-refs", (Function<String, String>) DeleteObjectsResultTest::escapeAllCharRefs),
            Arguments.of(key, "cdata", (Function<String, String>) DeleteObjectsResultTest::cdata)
        ));
    }

    @ParameterizedTest(name = "[{index}] {1}: \"{0}\"")
    @MethodSource("keysWithEncodings")
    void testDeletedKeyIsPreservedExactly(String key, String encoding, Function<String, String> encoder) throws Exception {
        var result = parse("""
            <?xml version="1.0" encoding="UTF-8"?>
            <DeleteResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/"><Deleted><Key>%s</Key></Deleted></DeleteResult>
            """.formatted(encoder.apply(key)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.deleted()).extracting(DeleteObjectsResult.Deleted::key).containsExactly(key);
    }

    @ParameterizedTest(name = "[{index}] {1}: \"{0}\"")
    @MethodSource("keysWithEncodings")
    void testErrorKeyIsPreservedExactly(String key, String encoding, Function<String, String> encoder) throws Exception {
        var result = parse("""
            <?xml version="1.0" encoding="UTF-8"?>
            <DeleteResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/"><Error><Key>%s</Key><Code>AccessDenied</Code><Message>Access Denied</Message></Error></DeleteResult>
            """.formatted(encoder.apply(key)));

        assertThat(result.deleted()).isEmpty();
        assertThat(result.errors()).containsExactly(new DeleteObjectsResult.Error("AccessDenied", key, "Access Denied", null));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("keys")
    void testKeyInPrettyPrintedXml(String key) throws Exception {
        var result = parse("""
            <?xml version="1.0" encoding="UTF-8"?>
            <DeleteResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                <Deleted>
                    <Key>%1$s</Key>
                </Deleted>
                <Error>
                    <Key>%1$s</Key>
                    <Code>AccessDenied</Code>
                    <Message>Access Denied</Message>
                </Error>
            </DeleteResult>
            """.formatted(escape(key)));

        assertThat(result.deleted()).extracting(DeleteObjectsResult.Deleted::key).containsExactly(key);
        assertThat(result.errors()).extracting(DeleteObjectsResult.Error::key).containsExactly(key);
    }

    @Test
    void testManyKeysCrossingParserBufferBoundaries() throws Exception {
        // Enough content to make the SAX parser split text nodes at its internal buffer boundaries
        var keys = new ArrayList<String>();
        for (int i = 0; i < 2000; i++) {
            keys.add(" key &" + i + " " + "z".repeat(i % 97) + " ");
        }
        var xml = new StringBuilder("<DeleteResult>");
        for (int i = 0; i < keys.size(); i++) {
            if (i % 2 == 0) {
                xml.append("<Deleted><Key>").append(escape(keys.get(i))).append("</Key></Deleted>");
            } else {
                xml.append("<Error><Key>").append(escape(keys.get(i))).append("</Key><Code>AccessDenied</Code><Message>Access Denied</Message></Error>");
            }
        }
        xml.append("</DeleteResult>");

        var result = parse(xml.toString());

        var expectedDeleted = new ArrayList<String>();
        var expectedErrors = new ArrayList<String>();
        for (int i = 0; i < keys.size(); i++) {
            (i % 2 == 0 ? expectedDeleted : expectedErrors).add(keys.get(i));
        }
        assertThat(result.deleted()).extracting(DeleteObjectsResult.Deleted::key).containsExactlyElementsOf(expectedDeleted);
        assertThat(result.errors()).extracting(DeleteObjectsResult.Error::key).containsExactlyElementsOf(expectedErrors);
    }

    @Test
    void testAllDeletedFields() throws Exception {
        var result = parse("""
            <DeleteResult>
              <Deleted>
                <Key> k </Key>
                <VersionId>v1</VersionId>
                <DeleteMarker>true</DeleteMarker>
                <DeleteMarkerVersionId>dm1</DeleteMarkerVersionId>
              </Deleted>
            </DeleteResult>
            """);

        assertThat(result.deleted()).containsExactly(new DeleteObjectsResult.Deleted(true, "dm1", " k ", "v1"));
    }

    @Test
    void testAllErrorFields() throws Exception {
        var result = parse("""
            <DeleteResult>
              <Error>
                <Key> k </Key>
                <VersionId>v1</VersionId>
                <Code>AccessDenied</Code>
                <Message>Access &amp; more</Message>
              </Error>
            </DeleteResult>
            """);

        assertThat(result.errors()).containsExactly(new DeleteObjectsResult.Error("AccessDenied", " k ", "Access & more", "v1"));
    }

    @Test
    void testMissingOptionalFieldsAreNull() throws Exception {
        var result = parse("<DeleteResult><Deleted><Key>k</Key></Deleted><Error><Key>e</Key><Code>C</Code><Message/></Error></DeleteResult>");

        assertThat(result.deleted()).containsExactly(new DeleteObjectsResult.Deleted(null, null, "k", null));
        assertThat(result.errors()).containsExactly(new DeleteObjectsResult.Error("C", "e", "", null));
    }

    @Test
    void testEmptyKeyElement() throws Exception {
        var result = parse("<DeleteResult><Deleted><Key></Key></Deleted><Deleted><Key/></Deleted></DeleteResult>");

        assertThat(result.deleted()).extracting(DeleteObjectsResult.Deleted::key).containsExactly("", "");
    }

    @Test
    void testFieldsDoNotLeakBetweenEntries() throws Exception {
        var result = parse("""
            <DeleteResult>
              <Deleted><Key>first</Key><VersionId>v1</VersionId></Deleted>
              <Deleted><Key>second</Key></Deleted>
              <Error><Key>third</Key><Code>C</Code><Message>M</Message><VersionId>v3</VersionId></Error>
              <Error><Key>fourth</Key><Code>C</Code><Message>M</Message></Error>
            </DeleteResult>
            """);

        assertThat(result.deleted()).containsExactly(
            new DeleteObjectsResult.Deleted(null, null, "first", "v1"),
            new DeleteObjectsResult.Deleted(null, null, "second", null)
        );
        assertThat(result.errors()).containsExactly(
            new DeleteObjectsResult.Error("C", "third", "M", "v3"),
            new DeleteObjectsResult.Error("C", "fourth", "M", null)
        );
    }

    @Test
    void testUnknownElementsAreIgnored() throws Exception {
        var result = parse("""
            <DeleteResult>
              <Unknown>junk</Unknown>
              <Deleted><Extra><Nested>junk</Nested></Extra><Key>k</Key></Deleted>
              <Error><Key>e</Key><Resource>/bucket/e</Resource><Code>C</Code><Message>M</Message></Error>
            </DeleteResult>
            """);

        assertThat(result.deleted()).extracting(DeleteObjectsResult.Deleted::key).containsExactly("k");
        assertThat(result.errors()).containsExactly(new DeleteObjectsResult.Error("C", "e", "M", null));
    }

    @Test
    void testEmptyResult() throws Exception {
        assertThat(parse("<DeleteResult/>")).isEqualTo(new DeleteObjectsResult(List.of(), List.of()));
        assertThat(parse("<DeleteResult>\n  \n</DeleteResult>")).isEqualTo(new DeleteObjectsResult(List.of(), List.of()));
    }

    static DeleteObjectsResult parse(String xml) throws Exception {
        return DeleteObjectsResult.fromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    static String escape(String s) {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            // XML parsers normalize raw CR to LF, so it has to be a character reference
            .replace("\r", "&#13;");
    }

    static String escapeAllCharRefs(String s) {
        var sb = new StringBuilder();
        s.codePoints().forEach(cp -> sb.append("&#x").append(Integer.toHexString(cp)).append(';'));
        return sb.toString();
    }

    static String cdata(String s) {
        // CR is normalized inside CDATA too, so it is emitted as a character reference outside of it
        var parts = s.replace("]]>", "]]]]><![CDATA[>").split("\r", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append("&#13;");
            }
            sb.append("<![CDATA[").append(parts[i]).append("]]>");
        }
        return sb.toString();
    }
}
