package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.publisher;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache2;

import java.math.BigDecimal;

public class CacheableWrongPublisherGet {

    public String value = "1";

    @Cacheable(DummyCache2.class)
    public Publisher<String> getValue(String arg1, BigDecimal arg2) {
        return Flux.just(value);
    }

    @CachePut(DummyCache2.class)
    public String putValue(String arg1, BigDecimal arg2) {
        return value;
    }
}
