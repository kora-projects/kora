package ru.tinkoff.kora.http.common.telemetry;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {
    @Test
    void testMaskHeaders() {
        var headers = HttpHeaders.of("authorization", "auth", "OtherHeader", "val");
        var headersToMask = Set.of("authorization");
        var mask = "<mask>";

        var maskedValue = Masking.toMaskedString(headersToMask, mask, headers);

        assertThat(maskedValue).isEqualTo("authorization: <mask>\notherheader: val");
    }

    @Test
    void testMaskQueryString() {
        var queryString = "a=5&sessionid=***";
        var queryParamsToMask = Set.of("sessionid");
        var mask = "<mask>";

        var maskedValue = Masking.toMaskedString(queryParamsToMask, mask, queryString);

        assertThat(maskedValue).isEqualTo("a=5&sessionid=<mask>");
    }

    @Test
    void testMaskQueryMap() {
        var queryParams = new LinkedHashMap<String, List<String>>();
        queryParams.put("a", List.of("5"));
        queryParams.put("sessionid", List.of("abc"));
        var queryParamsToMask = Set.of("sessionid");
        var mask = "<mask>";

        var maskedValue = Masking.toMaskedString(queryParamsToMask, mask, queryParams);

        assertThat(maskedValue).isEqualTo("a=5&sessionid=<mask>");
    }


}
