package io.koraframework.cache.annotation.processor.testdata.sync;

import io.koraframework.cache.CacheKeyMapper;
import io.koraframework.cache.annotation.CachePut;
import io.koraframework.cache.annotation.Cacheable;
import io.koraframework.cache.annotation.processor.testcache.DummyCache11;
import io.koraframework.common.Mapping;
import io.koraframework.common.Tag;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public class CacheableSyncMapper {

    public static final class CacheMapper implements CacheKeyMapper.CacheKeyMapper2<String, String, BigDecimal> {

        @NotNull
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
    @CachePut(value = DummyCache11.class, args = {"arg1", "arg2"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }
}
