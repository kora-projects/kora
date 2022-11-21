package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.config.common.ConfigModule
import ru.tinkoff.kora.resilient.circuitbreaker.impl.FastCircuitBreakerModule

@KoraApp
interface AppWithConfig : FastCircuitBreakerModule, ConfigModule {

    override fun config(): Config {
        return ConfigFactory.parseString(
            """
                resilient {
                  circuitBreaker {
                    fast {
                      default {
                        slidingWindowSize = 1
                        minimumRequiredCalls = 1
                        failureRateThreshold = 100
                        permittedCallsInHalfOpenState = 1
                        waitDurationInOpenState = 1s
                      }
                    }
                  }
                }
                """.trimIndent()
        ).resolve()
    }

    fun mockLifeCycle(
        target: CircuitBreakerTarget, fallbackTarget: CircuitBreakerFallbackTarget
    ): MockLifecycle {
        return CircuitBreakerLifecycle(target, fallbackTarget)
    }
}
