package io.koraframework.test.extension.junit5.config;

import io.koraframework.config.common.Config;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.KoraAppTestConfigModifier;
import io.koraframework.test.extension.junit5.KoraConfigModification;
import io.koraframework.test.extension.junit5.TestComponent;
import io.koraframework.test.extension.junit5.testdata.TestConfigApplication;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Several @KoraAppTest classes with {@link KoraConfigModification#ofString(String)} and no system properties
 * run in parallel, each graph must be built from its own config.
 */
public class ConfigParallelIsolationTests {

    static volatile boolean nested = false;

    static boolean nested() {
        return nested;
    }

    @Test
    void ofStringConfigsStayIsolatedWhenClassesRunInParallel() {
        nested = true;
        try {
            var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Config1.class), selectClass(Config2.class), selectClass(Config3.class), selectClass(Config4.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
                .build();
            var listener = new SummaryGeneratingListener();
            LauncherFactory.create().execute(request, listener);
            var summary = listener.getSummary();
            var failures = new StringWriter();
            summary.printFailuresTo(new PrintWriter(failures), 3);
            assertEquals(0, summary.getTotalFailureCount(),
                "started=" + summary.getTestsStartedCount() + " failed=" + summary.getTotalFailureCount() + "\n" + failures);
        } finally {
            nested = false;
        }
    }

    abstract static class ParallelConfig implements KoraAppTestConfigModifier {

        abstract int id();

        @Override
        public KoraConfigModification config() {
            return KoraConfigModification.ofString("parallel { value = " + id() + " }");
        }

        @RepeatedTest(25)
        void ownConfigInjected(@TestComponent Config config) {
            assertEquals(id(), config.get("parallel.value").asNumber().intValue());
        }
    }

    @EnabledIf("io.koraframework.test.extension.junit5.config.ConfigParallelIsolationTests#nested")
    @KoraAppTest(TestConfigApplication.class)
    static class Config1 extends ParallelConfig {
        @Override
        int id() {
            return 1;
        }
    }

    @EnabledIf("io.koraframework.test.extension.junit5.config.ConfigParallelIsolationTests#nested")
    @KoraAppTest(TestConfigApplication.class)
    static class Config2 extends ParallelConfig {
        @Override
        int id() {
            return 2;
        }
    }

    @EnabledIf("io.koraframework.test.extension.junit5.config.ConfigParallelIsolationTests#nested")
    @KoraAppTest(TestConfigApplication.class)
    static class Config3 extends ParallelConfig {
        @Override
        int id() {
            return 3;
        }
    }

    @EnabledIf("io.koraframework.test.extension.junit5.config.ConfigParallelIsolationTests#nested")
    @KoraAppTest(TestConfigApplication.class)
    static class Config4 extends ParallelConfig {
        @Override
        int id() {
            return 4;
        }
    }
}
