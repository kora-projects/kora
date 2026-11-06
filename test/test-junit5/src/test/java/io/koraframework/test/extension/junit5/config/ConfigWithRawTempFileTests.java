package io.koraframework.test.extension.junit5.config;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.ContainerConfigOrigin;
import io.koraframework.config.common.origin.FileConfigOrigin;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestConfigModifier;
import io.koraframework.test.extension.junit5.KoraConfigModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestConfigApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigWithRawTempFileTests {

    static final List<Path> configFiles = new CopyOnWriteArrayList<>();

    @Test
    public void tempConfigFileDeletedAfterGraphRelease() {
        configFiles.clear();
        var request = LauncherDiscoveryRequestBuilder.request()
            .configurationParameter("junit.jupiter.conditions.deactivate", "*")
            .selectors(DiscoverySelectors.selectClass(RawConfigTest.class)).build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);

        assertEquals(2, listener.getSummary().getTestsSucceededCount());
        assertEquals(2, configFiles.size());
        for (var configFile : configFiles) {
            assertFalse(Files.exists(configFile), "Temporary config file was not deleted: " + configFile);
        }
    }

    private static Stream<Path> filePaths(ConfigOrigin origin) {
        if (origin instanceof FileConfigOrigin file) {
            return Stream.of(file.path());
        } else if (origin instanceof ContainerConfigOrigin container) {
            return container.origins().stream().flatMap(ConfigWithRawTempFileTests::filePaths);
        } else {
            return Stream.empty();
        }
    }

    @Disabled("IS CHECKED IN SELECTOR")
    @KoraAppTest(TestConfigApplication.class)
    static class RawConfigTest implements KoraAppTestConfigModifier {

        @Override
        public KoraConfigModification config() {
            return KoraConfigModification.ofString("myconfig { myproperty = 1 }");
        }

        @Test
        void test1(@TestComponent Config config) {
            recordConfigFile(config);
        }

        @Test
        void test2(@TestComponent Config config) {
            recordConfigFile(config);
        }

        private static void recordConfigFile(Config config) {
            var path = filePaths(config.origin()).findFirst().orElseThrow();
            assertTrue(Files.exists(path));
            configFiles.add(path);
        }
    }
}
