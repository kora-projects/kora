package io.koraframework.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.cache.Cache
import io.koraframework.cache.caffeine.CaffeineCacheModule
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@KspExperimental
class CacheAopGeneratedCodeTests : AbstractSymbolProcessorTest(), CaffeineCacheModule {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.cache.annotation.Cache;
            import io.koraframework.cache.annotation.CacheInvalidate;
            import io.koraframework.cache.caffeine.CaffeineCache;

            """.trimIndent()
    }

    @Test
    fun cacheInvalidateNonVoidWithParameterNamedValue() {
        compile0(
            listOf(CacheSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Cache("cache1")
            interface Cache1 : CaffeineCache<String, String>
            """,
            """
            open class CacheableSync {
                @CacheInvalidate(Cache1::class)
                open fun update(value: String): String = "result"
            }
            """
        )
        compileResult.assertSuccess()

        @Suppress("UNCHECKED_CAST")
        val cache = newObject(
            "\$Cache1_Impl",
            CacheRunner.getCaffeineConfig(),
            caffeineCacheFactory(null),
            defaultCaffeineCacheTelemetryFactory(null, null, null, null)
        ).objectInstance as Cache<String, String>
        val service = newObject("\$CacheableSync__AopProxy", cache)
        cache.put("1", "cached")
        cache.put("result", "other")

        assertEquals("result", service.invoke<String>("update", "1"))
        assertNull(cache.get("1"))
        assertEquals("other", cache.get("result"))
    }
}
