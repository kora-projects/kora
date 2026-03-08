package io.koraframework.cache.symbol.processor.testdata

import io.koraframework.cache.CacheKeyMapper.CacheKeyMapper2
import io.koraframework.cache.annotation.CachePut
import io.koraframework.cache.annotation.Cacheable
import io.koraframework.cache.symbol.processor.testcache.DummyCache21
import io.koraframework.common.Mapping
import io.koraframework.common.Tag
import java.math.BigDecimal

open class CacheableMapper {
    var value = "1"

    class CacheMapper : CacheKeyMapper2<DummyCache21.Key, String?, BigDecimal?> {

        override fun map(arg1: String?, arg2: BigDecimal?): DummyCache21.Key? {
            if(arg1 == null || arg2 == null) {
                return null
            }

            return DummyCache21.Key(arg1, arg2)
        }
    }

    @Tag(CacheMapper::class)
    @Mapping(CacheMapper::class)
    @Cacheable(value = DummyCache21::class)
    open fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @Mapping(CacheMapper::class)
    @CachePut(value = DummyCache21::class, parameters = ["arg1", "arg2"])
    open fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        return value
    }
}
