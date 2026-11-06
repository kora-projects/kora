package io.koraframework.http.common.telemetry;

import org.junit.jupiter.api.Test;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilsTest {
    @Test
    void testMaskHeaders() {
        var headers = HttpHeaders.of("authorization", "auth", "OtherHeader", "val");
        var headersToMask = Set.of("authorization");
        var mask = "<mask>";

        var maskedValue = MaskingUtils.toMaskedString(headersToMask, mask, headers);

        assertThat(maskedValue).isEqualTo("authorization: <mask>\notherheader: val");
    }

    @Test
    void testMaskHeadersIgnoresHeaderNameCase() {
        // transports like OkHttp keep header names in their original wire case
        var wireHeaders = new LinkedHashMap<String, List<String>>();
        wireHeaders.put("X-Secret-Token", List.of("s3cr3t"));
        wireHeaders.put("Content-Type", List.of("text/plain"));
        var headers = new HttpHeaders() {
            @Override
            public String getFirst(String headerName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> getAll(String headerName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean has(String headerName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int size() {
                return wireHeaders.size();
            }

            @Override
            public Set<String> names() {
                return wireHeaders.keySet();
            }

            @Override
            public Iterator<Map.Entry<String, List<String>>> iterator() {
                return wireHeaders.entrySet().iterator();
            }
        };

        var maskedValue = MaskingUtils.toMaskedString(Set.of("x-secret-token"), "<mask>", headers);

        assertThat(maskedValue).isEqualTo("X-Secret-Token: <mask>\nContent-Type: text/plain");
    }

    @Test
    void testMaskQueryString() {
        var queryString = "a=5&sessionid=***";
        var queryParamsToMask = Set.of("sessionid");
        var mask = "<mask>";

        var maskedValue = MaskingUtils.toMaskedString(queryParamsToMask, mask, queryString);

        assertThat(maskedValue).isEqualTo("a=5&sessionid=<mask>");
    }

    @Test
    void testMaskRawQueryStringWithEncodedNameAndValue() {
        var queryString = "a=5&session%69d=p%26ss%3Dw0rd";
        var queryParamsToMask = Set.of("sessionid");
        var mask = "<mask>";

        var maskedValue = MaskingUtils.toMaskedString(queryParamsToMask, mask, queryString);

        assertThat(maskedValue).isEqualTo("a=5&session%69d=<mask>");
    }

    @Test
    void testMaskQueryMap() {
        var queryParams = new LinkedHashMap<String, List<String>>();
        queryParams.put("a", List.of("5"));
        queryParams.put("sessionid", List.of("abc"));
        var queryParamsToMask = Set.of("sessionid");
        var mask = "<mask>";

        var maskedValue = MaskingUtils.toMaskedString(queryParamsToMask, mask, queryParams);

        assertThat(maskedValue).isEqualTo("a=5&sessionid=<mask>");
    }

    @Test
    void passesValueToStrategy() {
        var headers = HttpHeaders.of("authorization", "secret");

        var maskedValue = MaskingUtils.toMaskedString(Set.of("authorization"),
            value -> "masked:" + value, headers);

        assertThat(maskedValue).isEqualTo("authorization: masked:secret");
    }

}
