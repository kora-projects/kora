package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingFull;
import io.koraframework.logging.common.masking.MaskingPathRules;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DataMaskerBytesTest {

    private final JsonDataMasker masker = new JsonDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .build());

    @Test
    void shouldMaskBytesAsUtf8ByDefault() {
        var content = "{\"login\":\"антон\",\"password\":\"secret\"}".getBytes(StandardCharsets.UTF_8);

        var masked = this.masker.mask(content);

        assertThat(masked).isEqualTo("{\"login\":\"антон\",\"password\":\"***\"}");
    }

    @Test
    void shouldMaskBytesInDeclaredCharset() {
        var charset = Charset.forName("windows-1251");
        var content = "{\"login\":\"антон\",\"password\":\"secret\"}".getBytes(charset);

        var masked = this.masker.mask(content, charset);

        assertThat(masked).isEqualTo("{\"login\":\"антон\",\"password\":\"***\"}");
    }

    @Test
    void shouldFallBackToUtf8OnUnknownCharset() {
        var content = "{\"password\":\"secret\"}".getBytes(StandardCharsets.UTF_8);

        var masked = this.masker.mask(content, null);

        assertThat(masked).isEqualTo("{\"password\":\"***\"}");
    }

    @Test
    void shouldReplaceUndecodableBytesInsteadOfFailing() {
        var content = new byte[]{'{', '"', 'a', '"', ':', '"', (byte) 0xC3, (byte) 0x28, '"', '}'};

        var masked = this.masker.mask(content);

        assertThat(masked).startsWith("{\"a\":\"").endsWith("\"}");
    }

    @Test
    void shouldMaskEmptyBytesFailClosed() {
        assertThat(this.masker.mask(new byte[0])).isEqualTo(JsonDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldMaskFormUrlencodedBytes() {
        var masker = new FormUrlencodedDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .build());

        var masked = masker.mask("login=anton&password=secret".getBytes(StandardCharsets.UTF_8));

        assertThat(masked).isEqualTo("login=anton&password=***");
    }

    @Test
    void shouldMaskXmlBytes() {
        var masker = new XmlDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .build());

        var masked = masker.mask("<a><password>secret</password></a>".getBytes(StandardCharsets.UTF_8));

        assertThat(masked).isEqualTo("<a><password>***</password></a>");
    }

    @Test
    void shouldMaskUtf16ByReEncodingIt() {
        var charset = StandardCharsets.UTF_16;
        var content = "{\"login\":\"антон\",\"password\":\"secret\"}".getBytes(charset);

        var masked = this.masker.mask(content, charset);

        assertThat(masked).isEqualTo("{\"login\":\"антон\",\"password\":\"***\"}");
    }

    @Test
    void shouldMatchNonAsciiRuleNames() {
        var masker = new JsonDataMasker(MaskingPathRules.builder()
            .mask("пароль", new MaskingFull())
            .build());

        var masked = masker.mask("{\"пароль\":\"secret\",\"a\":1}".getBytes(StandardCharsets.UTF_8));

        assertThat(masked).isEqualTo("{\"пароль\":\"***\",\"a\":1}");
    }

    @Test
    void shouldNotReadPayloadByteAsEndOfInput() {
        // 0xFF is -1 as a signed byte and must not be mistaken for the end of the payload
        var content = new byte[]{'{', '"', 'a', '"', ':', '"', (byte) 0xFF, '"', ',', '"', 'b', '"', ':', '1', '}'};

        var masked = this.masker.mask(content);

        assertThat(masked).endsWith("\"b\":1}");
    }

}
