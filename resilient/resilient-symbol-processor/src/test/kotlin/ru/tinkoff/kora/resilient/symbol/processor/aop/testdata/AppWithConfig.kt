package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.config.common.ConfigModule
import ru.tinkoff.kora.resilient.circuitbreaker.simple.CircuitBreakerModule
import ru.tinkoff.kora.resilient.fallback.simple.FallbackModule
import ru.tinkoff.kora.resilient.retry.simple.RetryableModule
import ru.tinkoff.kora.resilient.timeout.simple.TimeoutModule

@KoraApp
interface AppWithConfig : CircuitBreakerModule, FallbackModule, TimeoutModule, RetryableModule, ConfigModule {

    override fun config(): Config {
        return ConfigFactory.parseString(
            """
                resilient {
                  circuitbreaker {
                    default {
                      slidingWindowSize = 1
                      minimumRequiredCalls = 1
                      failureRateThreshold = 100
                      permittedCallsInHalfOpenState = 1
                      waitDurationInOpenState = 1s
                    }
                  }
                  timeout {
                    default {
                      duration = 300ms
                    }
                  }
                  retry {
                    default {
                      delay = 100ms
                      attempts = 2
                    }
                  }
                }
                """.trimIndent()
        ).resolve()
    }
}
