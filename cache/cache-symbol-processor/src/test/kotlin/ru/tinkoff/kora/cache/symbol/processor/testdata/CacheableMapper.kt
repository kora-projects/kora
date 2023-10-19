package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.CacheKeyMapper.CacheKeyMapper2
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache21
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.common.Tag
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
