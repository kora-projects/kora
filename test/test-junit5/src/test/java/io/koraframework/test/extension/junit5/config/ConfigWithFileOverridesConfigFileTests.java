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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A build-wide config.file system property must not make the test config ambiguous.
 */
@KoraAppTest(TestConfigApplication.class)
public class ConfigWithFileOverridesConfigFileTests implements KoraAppTestConfigModifier {

    static Path configFile;

    @BeforeAll
    static void setupConfigFile() throws IOException {
        configFile = Files.createTempFile("config-file-tests", ".conf");
        Files.writeString(configFile, "myconfig { myinnerconfig { third = 1 } }");
        System.setProperty("config.file", configFile.toString());
    }

    @AfterAll
    static void cleanupConfigFile() throws IOException {
        System.clearProperty("config.file");
        Files.deleteIfExists(configFile);
    }

    @Override
    public KoraConfigModification config() {
        return KoraConfigModification.ofResourceFile("application-raw.conf");
    }

    @Test
    void fileConfigOverridesConfigFile(@TestComponent Config config) {
        assertEquals(3, config.get("myconfig.myinnerconfig.third").asNumber());
    }
}
