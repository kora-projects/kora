package io.koraframework.resilient.annotation.processor.aop.testdata;

import com.typesafe.config.ConfigFactory;
import io.koraframework.common.KoraApp;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.DefaultConfigExtractorsModule;
import io.koraframework.config.common.origin.SimpleConfigOrigin;
import io.koraframework.config.hocon.HoconConfigFactory;
import io.koraframework.resilient.ResilientModule;

@KoraApp
public interface AppWithConfig extends ResilientModule, DefaultConfigExtractorsModule {

    default Config config() {
        return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
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
                      duration = 200ms
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
                """
        ).resolve());
    }
}
