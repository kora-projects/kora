package ru.tinkoff.kora.http.common;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class HttpHeadersImplTest {

    @Test
    void lowerCaseTest(){
        HttpHeaders headers = HttpHeaders.EMPTY;
        headers = headers.with("Some-Key", "Some-Value");
        for(Map.Entry<String, List<String>> header: headers){
            assertEquals("some-key", header.getKey());
        }
    }
}
