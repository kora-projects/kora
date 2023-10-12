package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.CacheKeyMapper;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache11;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.Tag;

import java.math.BigDecimal;

public class CacheableSyncMapper {

    public static final class CacheMapper implements CacheKeyMapper.CacheKeyMapper2<String, String, BigDecimal> {

        @Nonnull
        @Override
        public String map(String arg1, BigDecimal arg2) {
            return arg1 + arg2.toString();
        }
    }

    public String value = "1";

    @Tag(CacheMapper.class)
    @Mapping(CacheMapper.class)
    @Cacheable(DummyCache11.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @Mapping(CacheMapper.class)
    @CachePut(value = DummyCache11.class, parameters = {"arg1", "arg2"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }
}
