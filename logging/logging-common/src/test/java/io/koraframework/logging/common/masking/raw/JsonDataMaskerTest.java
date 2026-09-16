package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingFull;
import io.koraframework.logging.common.masking.MaskingPathRules;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDataMaskerTest {

    private final JsonDataMasker masker = new JsonDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .mask("user.token", new MaskingFull())
            .mask("root.secret", new MaskingFull())
            .build());

    @Test
    void shouldMaskFieldByNameAtAnyDepth() {
        var masked = this.mask("{\"login\":\"anton\",\"password\":\"secret\"}");

        assertThat(masked).isEqualTo("{\"login\":\"anton\",\"password\":\"***\"}");
    }

    @Test
    void shouldMaskNestedFieldByName() {
        var masked = this.mask("{\"a\":{\"b\":{\"password\":\"secret\"}}}");

        assertThat(masked).isEqualTo("{\"a\":{\"b\":{\"password\":\"***\"}}}");
    }

    @Test
    void shouldMaskByAbsolutePath() {
        var masked = this.mask("{\"user\":{\"token\":\"t\"},\"other\":{\"token\":\"t\"}}");

        assertThat(masked).isEqualTo("{\"user\":{\"token\":\"***\"},\"other\":{\"token\":\"t\"}}");
    }

    @Test
    void shouldNotMaskAbsolutePathUnderAnEnvelope() {
        assertThat(this.mask("{\"root\":{\"secret\":1}}")).isEqualTo("{\"root\":{\"secret\":\"***\"}}");
        assertThat(this.mask("{\"a\":{\"root\":{\"secret\":1}}}")).isEqualTo("{\"a\":{\"root\":{\"secret\":1}}}");
    }

    @Test
    void shouldMaskWholeObjectAndArrayValues() {
        var masked = this.mask("{\"password\":{\"a\":[1,2,{\"b\":\"c\"}]},\"keep\":1}");

        assertThat(masked).isEqualTo("{\"password\":\"***\",\"keep\":1}");
    }

    @Test
    void shouldMaskInsideArrayElementsWithoutIndexInPath() {
        var masked = this.mask("{\"items\":[{\"password\":\"a\"},{\"password\":\"b\"}]}");

        assertThat(masked).isEqualTo("{\"items\":[{\"password\":\"***\"},{\"password\":\"***\"}]}");
    }

    @Test
    void shouldMaskCaseInsensitively() {
        var masked = this.mask("{\"PassWord\":\"secret\"}");

        assertThat(masked).isEqualTo("{\"PassWord\":\"***\"}");
    }

    @Test
    void shouldKeepValidJsonValid() {
        var source = "{\"a\":[1,2.5,-3e10,true,false,null],\"b\":{\"c\":\"d\"},\"password\":\"x\"}";

        var masked = this.mask(source);

        assertThat(masked).isEqualTo("{\"a\":[1,2.5,-3e10,true,false,null],\"b\":{\"c\":\"d\"},\"password\":\"***\"}");
    }

    @Test
    void shouldPreserveWhitespaceAndEscapes() {
        var source = "{\n  \"a\": \"quote \\\" and \\\\ slash\",\n  \"password\": \"s\"\n}";

        var masked = this.mask(source);

        assertThat(masked).isEqualTo("{\n  \"a\": \"quote \\\" and \\\\ slash\",\n  \"password\": \"***\"\n}");
    }

    @Test
    void shouldMaskValueContainingQuoteEscapes() {
        var masked = this.mask("{\"password\":\"a\\\"b\",\"keep\":1}");

        assertThat(masked).isEqualTo("{\"password\":\"***\",\"keep\":1}");
    }

    @Test
    void shouldFailClosedOnUnterminatedString() {
        var masked = this.mask("{\"a\":\"unterminated");

        assertThat(masked).isEqualTo("{\"a\":" + JsonDataMasker.DAMAGED_SUFFIX);
        assertThat(masked).doesNotContain("unterminated");
    }

    @Test
    void shouldFailClosedOnTruncatedPayload() {
        var masked = this.mask("{\"a\":1,\"b\":{\"c\":");

        assertThat(masked).endsWith(JsonDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldFailClosedOnContentThatIsNotJsonAtAll() {
        var masked = this.mask("<html><body>Gateway Timeout</body></html>");

        assertThat(masked).isEqualTo(JsonDataMasker.DAMAGED_SUFFIX);
        assertThat(masked).doesNotContain("Gateway");
    }

    @Test
    void shouldFailClosedOnTrailingGarbage() {
        var masked = this.mask("{\"a\":1} then some log noise");

        assertThat(masked).isEqualTo("{\"a\":1} " + JsonDataMasker.DAMAGED_SUFFIX);
        assertThat(masked).doesNotContain("noise");
    }

    @Test
    void shouldFailClosedOnMissingColon() {
        var masked = this.mask("{\"a\" 1}");

        assertThat(masked).endsWith(JsonDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldFailClosedOnEmptyPayload() {
        assertThat(this.mask("")).isEqualTo(JsonDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldFailClosedOnUnbalancedMaskedValue() {
        var masked = this.mask("{\"password\":{\"a\":1");

        assertThat(masked).endsWith(JsonDataMasker.DAMAGED_SUFFIX);
        assertThat(masked).doesNotContain("\"a\"");
    }

    @Test
    void shouldStopOnDepthLimit() {
        var deep = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            deep.append("{\"a\":");
        }
        deep.append("1");
        deep.append("}".repeat(40));
        var shallow = new JsonDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .build(), 8, JsonDataMasker.DEFAULT_MAX_LENGTH);

        var masked = shallow.mask(deep.toString().getBytes(StandardCharsets.UTF_8));

        assertThat(masked).endsWith(JsonDataMasker.DAMAGED_SUFFIX);
    }

    @Test
    void shouldTruncateLongPayload() {
        var small = new JsonDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .build(), JsonDataMasker.DEFAULT_MAX_DEPTH, 16);

        var masked = small.mask(("{\"a\":\"" + "x".repeat(1000) + "\"}").getBytes(StandardCharsets.UTF_8));

        assertThat(masked).hasSize(16 + JsonDataMasker.TRUNCATED_SUFFIX.length());
        assertThat(masked).endsWith(JsonDataMasker.TRUNCATED_SUFFIX);
    }

    @Test
    void shouldMaskTopLevelScalarNothingToDo() {
        assertThat(this.mask("\"just a string\"")).isEqualTo("\"just a string\"");
        assertThat(this.mask("42")).isEqualTo("42");
    }

    @Test
    void shouldHandleEmptyObjectsAndArrays() {
        assertThat(this.mask("{}")).isEqualTo("{}");
        assertThat(this.mask("[]")).isEqualTo("[]");
        assertThat(this.mask("{\"a\":{},\"b\":[]}")).isEqualTo("{\"a\":{},\"b\":[]}");
    }

    @Test
    void shouldReportItsFormat() {
        assertThat(this.masker.format()).isEqualTo("json");
    }

    private String mask(String content) {
        return this.masker.mask(content.getBytes(StandardCharsets.UTF_8));
    }

}
