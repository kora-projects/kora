package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;

public class Blackbird extends BaseJacksonBenchmark {
    @Override
    protected ObjectMapper createObjectMapper() {
        return new ObjectMapper().registerModule(new BlackbirdModule());
    }
}
