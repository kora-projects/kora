package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Vanilla extends BaseJacksonBenchmark {

    protected ObjectMapper createObjectMapper() {
        return new ObjectMapper();
    }
}
