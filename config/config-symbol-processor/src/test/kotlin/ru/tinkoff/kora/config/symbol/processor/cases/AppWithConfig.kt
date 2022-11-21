package ru.tinkoff.kora.config.symbol.processor.cases

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor

@KoraApp
interface AppWithConfig : DefaultConfigExtractorsModule {
    fun testConfig(): Config {
        return ConfigFactory.parseString(
            """
                intField = 1
                boxedIntField = 2
                longField = 3
                boxedLongField = 4
                doubleField = 5
                boxedDoubleField = 6
                booleanField = true
                boxedBooleanField = false
                stringField = "some string value"
                listField = "1,2,3,4,5"
                objectField {
                  foo = 1
                  bar = baz
                }
                props {
                  foo.bar.baz1 = 1
                  foo.bar.baz2 = true
                  foo.bar.baz3 = "asd"
                  foo.bar.baz4 = [1, false, "zxc"]
                }
                """.trimIndent()
        ).resolve()
    }

    fun mapConfig(config: Config, extractor: ConfigValueExtractor<Map<String, String>?>): Map<String, String>? {
        return extractor.extract(config.root())
    }
    fun pojoConfig(config: Config, extractor: ConfigValueExtractor<ClassConfig>): ClassConfig {
        return extractor.extract(config.root())
    }

    fun recordConfig(config: Config, extractor: ConfigValueExtractor<DataClassConfig>): DataClassConfig {
        return extractor.extract(config.root())
    }

    fun mockLifecycle(recordConfig: ValueOf<DataClassConfig>, pojoConfig: ValueOf<ClassConfig>): MockLifecycle {
        return object : MockLifecycle {}
    }
}
