package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingFull;
import io.koraframework.logging.common.masking.MaskingPathRules;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class XmlDataMaskerTest {

    private final XmlDataMasker masker = new XmlDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .mask("user.token", new MaskingFull())
            .build());

    @Test
    void shouldMaskElementText() {
        var masked = this.mask("<user><login>anton</login><password>secret</password></user>");

        assertThat(masked).isEqualTo("<user><login>anton</login><password>***</password></user>");
    }

    @Test
    void shouldMaskAttributeValue() {
        var masked = this.mask("<user login=\"anton\" password=\"secret\"/>");

        assertThat(masked).isEqualTo("<user login=\"anton\" password=\"***\"/>");
    }

    @Test
    void shouldMaskSingleQuotedAttribute() {
        var masked = this.mask("<user password='secret'/>");

        assertThat(masked).isEqualTo("<user password='***'/>");
    }

    @Test
    void shouldMaskByAbsolutePath() {
        var masked = this.mask("<user><token>t</token><other><token>t</token></other></user>");

        assertThat(masked).isEqualTo("<user><token>***</token><other><token>t</token></other></user>");
    }

    @Test
    void shouldNotMaskAbsolutePathUnderAnEnvelope() {
        // a multi segment rule is anchored at the root, so an envelope moves the path out of its reach
        var source = "<data><user><token>t</token></user></data>";

        assertThat(this.mask(source)).isEqualTo(source);
    }

    @Test
    void shouldIgnoreNamespacePrefix() {
        var masked = this.mask("<ns:user><ns:password>secret</ns:password></ns:user>");

        assertThat(masked).isEqualTo("<ns:user><ns:password>***</ns:password></ns:user>");
    }

    @Test
    void shouldMaskNestedMarkupInsideMaskedElement() {
        var masked = this.mask("<a><password><inner>secret</inner></password><keep>1</keep></a>");

        assertThat(masked).isEqualTo("<a><password>***</password><keep>1</keep></a>");
        assertThat(masked).doesNotContain("secret");
    }

    @Test
    void shouldKeepDeclarationCommentsAndCdata() {
        var source = "<?xml version=\"1.0\"?><!-- note --><a><![CDATA[raw < data]]></a>";

        assertThat(this.mask(source)).isEqualTo(source);
    }

    @Test
    void shouldMaskCdataInsideMaskedElement() {
        var masked = this.mask("<password><![CDATA[secret]]></password>");

        assertThat(masked).isEqualTo("<password>***</password>");
        assertThat(masked).doesNotContain("secret");
    }

    @Test
    void shouldFailClosedOnUnterminatedTag() {
        var masked = this.mask("<a><b attr=\"x\"");

        assertThat(masked).endsWith(XmlDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldFailClosedOnMismatchedClosingTag() {
        var masked = this.mask("<a><b>text</c></a>");

        assertThat(masked).endsWith(XmlDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldFailClosedOnUnterminatedAttributeValue() {
        var masked = this.mask("<a attr=\"unterminated");

        assertThat(masked).endsWith(XmlDataMasker.DAMAGED_SUFFIX);
        assertThat(masked).doesNotContain("unterminated");
    }

    @Test
    void shouldFailClosedOnUnclosedMaskedElement() {
        var masked = this.mask("<a><password>secret");

        assertThat(masked).endsWith(XmlDataMasker.DAMAGED_SUFFIX);
        assertThat(masked).doesNotContain("secret");
    }

    @Test
    void shouldFailClosedOnUnterminatedComment() {
        var masked = this.mask("<a><!-- unterminated");

        assertThat(masked).endsWith(XmlDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldTruncateLongPayload() {
        var small = new XmlDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .build(), XmlDataMasker.DEFAULT_MAX_DEPTH, 16);

        var masked = small.mask(("<a>" + "x".repeat(1000) + "</a>").getBytes(StandardCharsets.UTF_8));

        assertThat(masked).hasSize(16 + XmlDataMasker.TRUNCATED_SUFFIX.length());
        assertThat(masked).endsWith(XmlDataMasker.TRUNCATED_SUFFIX);
    }

    @Test
    void shouldReportItsFormat() {
        assertThat(this.masker.format()).isEqualTo("xml");
    }

    private String mask(String content) {
        return this.masker.mask(content.getBytes(StandardCharsets.UTF_8));
    }

}
