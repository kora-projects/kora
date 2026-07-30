package io.koraframework.micrometer.common;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.*;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.search.Search;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public final class NoopMeterRegistry extends MeterRegistry {

    private static final Meter.Id NOOP_ID = new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.OTHER);
    private static final Gauge NOOP_GAUGE = new NoopGauge(NOOP_ID);
    private static final TimeGauge NOOP_TIME_GAUGE = new NoopTimeGauge(NOOP_ID);
    private static final Counter NOOP_COUNTER = new NoopCounter(NOOP_ID);
    private static final FunctionCounter NOOP_FUNCTION_COUNTER = new NoopFunctionCounter(NOOP_ID);
    private static final Timer NOOP_TIMER = new NoopTimer(NOOP_ID) {
        @Override
        public Runnable wrap(Runnable f) {
            return f;
        }

        @Override
        public <T> Callable<T> wrap(Callable<T> f) {
            return f;
        }

        @Override
        public <T> Supplier<T> wrap(Supplier<T> f) {
            return f;
        }
    };
    private static final FunctionTimer NOOP_FUNCTION_TIMER = new NoopFunctionTimer(NOOP_ID);
    private static final DistributionSummary NOOP_DISTRIBUTION_SUMMARY = new NoopDistributionSummary(NOOP_ID);
    private static final Meter NOOP_METER = new NoopMeter(NOOP_ID);
    private static final LongTaskTimer.Sample NOOP_LONG_TASK_TIMER_SAMPLE = new LongTaskTimer.Sample() {
        @Override
        public long stop() {
            return 0;
        }

        @Override
        public double duration(TimeUnit unit) {
            return 0;
        }
    };
    private static final LongTaskTimer NOOP_LONG_TASK_TIMER = new NoopLongTaskTimer(NOOP_ID) {
        @Override
        public Sample start() {
            return NOOP_LONG_TASK_TIMER_SAMPLE;
        }

        @Override
        public <T> T record(Supplier<T> f) {
            return f.get();
        }

        @Override
        public boolean record(BooleanSupplier f) {
            return f.getAsBoolean();
        }

        @Override
        public int record(IntSupplier f) {
            return f.getAsInt();
        }

        @Override
        public long record(LongSupplier f) {
            return f.getAsLong();
        }

        @Override
        public double record(DoubleSupplier f) {
            return f.getAsDouble();
        }

        @Override
        public <T> T recordCallable(Callable<T> f) throws Exception {
            return f.call();
        }

        @Override
        public void record(Consumer<Sample> f) {
            f.accept(NOOP_LONG_TASK_TIMER_SAMPLE);
        }

        @Override
        public void record(Runnable f) {
            f.run();
        }
    };

    public static final NoopMeterRegistry INSTANCE = new NoopMeterRegistry();

    private final Config config = new NoopConfig();
    private final More more = new NoopMore();
    private final Search noopSearch;
    private final RequiredSearch noopRequiredSearch;

    private NoopMeterRegistry() {
        super(Clock.SYSTEM);
        this.noopSearch = Search.in(this).name("noop");
        this.noopRequiredSearch = RequiredSearch.in(this).name("noop");
    }

    @Override
    protected <T> TimeGauge newTimeGauge(Meter.Id id, @Nullable T obj, TimeUnit valueFunctionUnit, ToDoubleFunction<T> valueFunction) {
        return NOOP_TIME_GAUGE;
    }

    @Override
    public @Nullable <T> T gauge(String name, Iterable<Tag> tags, @Nullable T stateObject, ToDoubleFunction<T> valueFunction) {
        return stateObject;
    }

    @Override
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return number;
    }

    @Override
    public <T extends Number> T gauge(String name, T number) {
        return number;
    }

    @Override
    public <T> T gauge(String name, T stateObject, ToDoubleFunction<T> valueFunction) {
        return stateObject;
    }

    @Override
    public <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return collection;
    }

    @Override
    public <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return map;
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T object, ToDoubleFunction<T> valueFunction) {
        return NOOP_GAUGE;
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return NOOP_COUNTER;
    }

    @Override
    public Counter counter(String name, String... tags) {
        return NOOP_COUNTER;
    }

    @Override
    public Counter counter(String name, Tags tags) {
        return NOOP_COUNTER;
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return NOOP_COUNTER;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T object, ToDoubleFunction<T> countFunction) {
        return NOOP_FUNCTION_COUNTER;
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return NOOP_TIMER;
    }

    @Override
    public Timer timer(String name, String... tags) {
        return NOOP_TIMER;
    }

    @Override
    public Timer timer(String name, Tags tags) {
        return NOOP_TIMER;
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return NOOP_TIMER;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T object, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return NOOP_FUNCTION_TIMER;
    }

    @Override
    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        return NOOP_DISTRIBUTION_SUMMARY;
    }

    @Override
    public DistributionSummary summary(String name, String... tags) {
        return NOOP_DISTRIBUTION_SUMMARY;
    }

    public DistributionSummary summary(String name, Tags tags) {
        return NOOP_DISTRIBUTION_SUMMARY;
    }

    @Override
    public More more() {
        return this.more;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return NOOP_DISTRIBUTION_SUMMARY;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return NOOP_METER;
    }

    @Override
    public List<Meter> getMeters() {
        return Collections.emptyList();
    }

    @Override
    public void forEachMeter(Consumer<? super Meter> consumer) {
        // do nothing
    }

    @Override
    public Config config() {
        return this.config;
    }

    @Override
    protected String getConventionName(Meter.Id id) {
        return id.getName();
    }

    @Override
    protected List<Tag> getConventionTags(Meter.Id id) {
        return id.getTags();
    }

    @Override
    public Search find(String name) {
        return noopSearch;
    }

    @Override
    public RequiredSearch get(String name) {
        return noopRequiredSearch;
    }

    @Override
    public @Nullable Meter remove(Meter meter) {
        return null;
    }

    @Override
    public @Nullable Meter removeByPreFilterId(Meter.Id preFilterId) {
        return null;
    }

    @Override
    public @Nullable Meter remove(Meter.Id mappedId) {
        return null;
    }

    @Override
    public void clear() {
        // do nothing
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    protected void meterRegistrationFailed(Meter.Id id, @Nullable String reason) {
        // do nothing
    }

    @Override
    public boolean isClosed() {
        return true;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.DEFAULT;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return NOOP_LONG_TASK_TIMER;
    }

    private final class NoopMore extends More {

        @Override
        public LongTaskTimer longTaskTimer(String name, String... tags) {
            return NOOP_LONG_TASK_TIMER;
        }

        @Override
        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return NOOP_LONG_TASK_TIMER;
        }

        @Override
        public LongTaskTimer longTaskTimer(String name, Tags tags) {
            return NOOP_LONG_TASK_TIMER;
        }

        @Override
        public <T> FunctionCounter counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> countFunction) {
            return NOOP_FUNCTION_COUNTER;
        }

        @Override
        public <T extends Number> FunctionCounter counter(String name, Iterable<Tag> tags, T number) {
            return NOOP_FUNCTION_COUNTER;
        }

        @Override
        public <T> FunctionTimer timer(String name, Iterable<Tag> tags, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
            return NOOP_FUNCTION_TIMER;
        }

        @Override
        public <T> TimeGauge timeGauge(String name, Iterable<Tag> tags, T obj, TimeUnit timeFunctionUnit, ToDoubleFunction<T> timeFunction) {
            return NOOP_TIME_GAUGE;
        }
    }

    private final class NoopConfig extends Config {

        @Override
        public Config commonTags(Iterable<Tag> tags) {
            return this;
        }

        @Override
        public Config commonTags(String... tags) {
            return this;
        }

        @Override
        public synchronized Config meterFilter(MeterFilter filter) {
            return this;
        }

        @Override
        public Config onMeterAdded(Consumer<Meter> meterAddedListener) {
            return this;
        }

        @Override
        public Config onMeterRemoved(Consumer<Meter> meterRemovedListener) {
            return this;
        }

        @Override
        public Config onMeterRegistrationFailed(BiConsumer<Meter.Id, String> meterRegistrationFailedListener) {
            return this;
        }

        @Override
        public Config namingConvention(NamingConvention convention) {
            return this;
        }

        @Override
        public NamingConvention namingConvention() {
            return NamingConvention.snakeCase;
        }

        @Override
        public Clock clock() {
            return Clock.SYSTEM;
        }

        @Override
        public Config pauseDetector(PauseDetector detector) {
            return this;
        }

        @Override
        public PauseDetector pauseDetector() {
            return NoPauseDetector.INSTANCE;
        }

        @Override
        public Config withHighCardinalityTagsDetector() {
            return this;
        }

        @Override
        public Config withHighCardinalityTagsDetector(long threshold, Duration delay) {
            return this;
        }

        @Override
        public Config withHighCardinalityTagsDetector(Function<MeterRegistry, HighCardinalityTagsDetector> highCardinalityTagsDetectorFactory) {
            return this;
        }

        @Override
        public @Nullable HighCardinalityTagsDetector highCardinalityTagsDetector() {
            return null;
        }
    }
}
