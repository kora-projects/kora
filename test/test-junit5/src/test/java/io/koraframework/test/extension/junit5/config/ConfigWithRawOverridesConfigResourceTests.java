package io.koraframework.test.extension.junit5.config;

import io.koraframework.config.common.Config;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestConfigModifier;
import io.koraframework.test.extension.junit5.KoraConfigModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestConfigApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A build-wide config.resource system property must not make the test config ambiguous.
 */
@KoraAppTest(TestConfigApplication.class)
public class ConfigWithRawOverridesConfigResourceTests implements KoraAppTestConfigModifier {

    @BeforeAll
    static void setupConfigResource() {
        System.setProperty("config.resource", "application-raw.conf");
    }

    @AfterAll
    static void cleanupConfigResource() {
        System.clearProperty("config.resource");
    }

    @Override
    public KoraConfigModification config() {
        return KoraConfigModification.ofString("""
            myconfig {
              myinnerconfig {
                myproperty = 1
              }
            }
            """);
    }

    @Test
    void rawConfigOverridesConfigResource(@TestComponent Config config) {
        assertEquals(1, config.get("myconfig.myinnerconfig.myproperty").asNumber());
    }
}
