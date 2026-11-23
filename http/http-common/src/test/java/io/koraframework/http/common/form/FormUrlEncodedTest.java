package io.koraframework.http.common.form;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormUrlEncodedTest {

    @Test
    void repeatedPartValuesKeepInsertionOrder() {
        var form = new FormUrlEncoded(
            new FormUrlEncoded.FormPart("a", "1"),
            new FormUrlEncoded.FormPart("a", "2"),
            new FormUrlEncoded.FormPart("a", List.of("3", "4"))
        );

        assertThat(form.get("a").values()).containsExactly("1", "2", "3", "4");
    }

    @Test
    void partsIterateInInsertionOrder() {
        var form = new FormUrlEncoded(List.of(
            new FormUrlEncoded.FormPart("c", "1"),
            new FormUrlEncoded.FormPart("b", "2"),
            new FormUrlEncoded.FormPart("a", "3"),
            new FormUrlEncoded.FormPart("b", "4")
        ));

        assertThat(form).extracting(FormUrlEncoded.FormPart::name).containsExactly("c", "b", "a");
    }
}
