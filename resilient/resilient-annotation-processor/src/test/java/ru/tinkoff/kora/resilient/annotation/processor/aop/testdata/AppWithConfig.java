package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.ConfigModule;
import ru.tinkoff.kora.resilient.circuitbreaker.impl.FastCircuitBreakerModule;

@KoraApp
public interface AppWithConfig extends FastCircuitBreakerModule, ConfigModule {

    @Override
    default Config config() {
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
                """
        ).resolve();
    }
}
