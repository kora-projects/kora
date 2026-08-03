package io.koraframework.http.server.common.request;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerRequestBuilderImplTest {

    @Test
    void toBuilderCopiesQueryParamsOnlyOnMutation() {
        var queryParams = new LinkedHashMap<String, List<String>>();
        queryParams.put("existing", new ArrayList<>(List.of("1")));
        var original = new SimpleHttpServerRequest(
            "localhost",
            "http",
            "GET",
            "/foo",
            "/foo",
            Map.of(),
            queryParams,
            HttpHeaders.empty(),
            List.of(),
            HttpBody.empty(),
            42
        );

        var unchanged = original.toBuilder().build();
        assertSame(queryParams, unchanged.queryParams());

        var builder = original.toBuilder()
            .queryParam("existing", "2")
            .queryParam("flag");
        var changed = builder.build();

        assertAll(
            () -> assertSame(queryParams, original.queryParams()),
            () -> assertNotSame(queryParams, changed.queryParams()),
            () -> assertEquals(List.of("1"), original.queryParams().get("existing")),
            () -> assertEquals(List.of("1", "2"), changed.queryParams().get("existing")),
            () -> assertEquals(List.of(), changed.queryParams().get("flag"))
        );

        builder.queryParam("existing", "3");
        assertEquals(List.of("1", "2"), changed.queryParams().get("existing"));
    }
}
