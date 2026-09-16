package io.koraframework.logging.common.masking.raw;

import io.koraframework.logging.common.masking.MaskingFull;
import io.koraframework.logging.common.masking.MaskingPathRules;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FormUrlencodedDataMaskerTest {

    private final FormUrlencodedDataMasker masker = new FormUrlencodedDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .mask("token", new MaskingFull())
            .build());

    @Test
    void shouldMaskMatchedParameter() {
        var masked = this.mask("login=anton&password=secret&remember=true");

        assertThat(masked).isEqualTo("login=anton&password=***&remember=true");
    }

    @Test
    void shouldMaskSeveralParameters() {
        var masked = this.mask("password=a&token=b");

        assertThat(masked).isEqualTo("password=***&token=***");
    }

    @Test
    void shouldMaskCaseInsensitivelyAndDecodeNames() {
        assertThat(this.mask("PassWord=x")).isEqualTo("PassWord=***");
        assertThat(this.mask("pass%77ord=x")).isEqualTo("pass%77ord=***");
    }

    @Test
    void shouldKeepUnmatchedParametersVerbatim() {
        var source = "a=1&b=%D0%B0&c=hello+world";

        assertThat(this.mask(source)).isEqualTo(source);
    }

    @Test
    void shouldHandleEmptyValue() {
        assertThat(this.mask("password=&a=1")).isEqualTo("password=***&a=1");
    }

    @Test
    void shouldKeepParameterWithoutValue() {
        assertThat(this.mask("flag&a=1")).isEqualTo("flag&a=1");
    }

    @Test
    void shouldMaskValueWhenNameIsUndecodable() {
        var masked = this.mask("bad%ZZname=secret&a=1");

        assertThat(masked).isEqualTo("bad%ZZname=***&a=1");
        assertThat(masked).doesNotContain("secret");
    }

    @Test
    void shouldResumeAfterUndecodableParameter() {
        var masked = this.mask("a=1&bad%=x&password=s&b=2");

        assertThat(masked).isEqualTo("a=1&bad%=***&password=***&b=2");
    }

    @Test
    void shouldHandleEmptyPayload() {
        assertThat(this.mask("")).isEmpty();
    }

    @Test
    void shouldTruncateLongPayload() {
        var small = new FormUrlencodedDataMasker(MaskingPathRules.builder()
            .mask("password", new MaskingFull())
            .build(), 16);

        var masked = small.mask(("a=" + "x".repeat(1000)).getBytes(StandardCharsets.UTF_8));

        assertThat(masked).hasSize(16 + FormUrlencodedDataMasker.TRUNCATED_SUFFIX.length());
        assertThat(masked).endsWith(FormUrlencodedDataMasker.TRUNCATED_SUFFIX);
    }

    @Test
    void shouldReportItsFormat() {
        assertThat(this.masker.format()).isEqualTo("form-urlencoded");
    }

    private String mask(String content) {
        return this.masker.mask(content.getBytes(StandardCharsets.UTF_8));
    }

}
