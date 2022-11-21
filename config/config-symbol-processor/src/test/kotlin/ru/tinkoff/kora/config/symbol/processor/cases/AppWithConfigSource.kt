package ru.tinkoff.kora.config.symbol.processor.cases

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import reactor.core.publisher.Mono
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.LifecycleWrapper
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.config.common.ConfigSource
import java.util.Map

@KoraApp
interface AppWithConfigSource {
    @ConfigSource("some.config")
    data class SomeConfig(val field1: String, val field2: Int)

    fun config(): LifecycleWrapper<Config> {
        val config = ConfigFactory.parseMap(
            Map.of(
                "some", Map.of(
                    "config", Map.of(
                        "field1", "field",
                        "field2", 42
                    )
                )
            )
        )
        return LifecycleWrapper(config, { v: Config -> Mono.empty() }) { v: Config -> Mono.empty() }
    }

    fun component(someConfig: SomeConfig): MockLifecycle {
        return object : MockLifecycle {}
    }
}
