package io.koraframework.test.extension.junit5.testdata;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.annotation.KoraApp;
import io.koraframework.common.annotation.Root;

import java.util.concurrent.atomic.AtomicInteger;

@KoraApp
public interface LifecycleTestApplication {

    final class CountingLifecycleComponent implements Lifecycle {

        public static final AtomicInteger INIT = new AtomicInteger();
        public static final AtomicInteger RELEASE = new AtomicInteger();

        @Override
        public void init() {
            INIT.incrementAndGet();
        }

        @Override
        public void release() {
            RELEASE.incrementAndGet();
        }
    }

    class DependentComponent {

        private final CountingLifecycleComponent dependency;

        public DependentComponent(CountingLifecycleComponent dependency) {
            this.dependency = dependency;
        }

        public String get() {
            return "real";
        }
    }

    default CountingLifecycleComponent countingLifecycleComponent() {
        return new CountingLifecycleComponent();
    }

    @Root
    default DependentComponent dependentComponent(CountingLifecycleComponent dependency) {
        return new DependentComponent(dependency);
    }

    default TestComponent3 testComponent3() {
        return new TestComponent3();
    }
}
