package io.koraframework.resilient.symbol.processor.aop.testdata

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.koraframework.common.KoraApp
import io.koraframework.config.common.DefaultConfigExtractorsModule
import io.koraframework.config.common.origin.SimpleConfigOrigin
import io.koraframework.config.hocon.HoconConfigFactory
import io.koraframework.resilient.ResilientModule

@KoraApp
interface AppWithConfig : ResilientModule, DefaultConfigExtractorsModule {
    fun config(config: Config) = HoconConfigFactory.fromHocon(SimpleConfigOrigin("test"), config)

    fun config() = ConfigFactory.parseString(
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
                customZeroAttempts {
                  delay = 100ms
                  attempts = 0
                }
                customDisabled {
                  enabled = false
                }
              }
              ratelimiter {
                default {
                  limitForPeriod = 1
                  limitRefreshPeriod = 1s
                }
                customDisabled {
                  enabled = false
                  limitForPeriod = 1
                  limitRefreshPeriod = 1s
                }
              }
            }
            """.trimIndent()
    ).resolve()
}
