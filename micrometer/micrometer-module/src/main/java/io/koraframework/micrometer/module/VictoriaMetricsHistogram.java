package io.koraframework.micrometer.module;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.UnknownSnapshot;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * VictoriaMetrics-compatible histogram collector for Micrometer Prometheus registry.
 * <p>
 * The implementation is inspired by VictoriaMetrics {@code vmrange} histogram format,
 * the Go VictoriaMetrics metrics implementation, and the old Micrometer 1.12
 * {@code FixedBoundaryVictoriaMetricsHistogram} implementation.
 * <p>
 * The collector keeps one registered Prometheus collector per metric name and stores a
 * separate child for every unique Micrometer tag set. Recording is lock-free for existing
 * children: the input value is converted to seconds, mapped to a VictoriaMetrics bucket by
 * decimal exponent and mantissa, and increments the corresponding {@link AtomicLongArray}
 * cell. The fixed grid has special buckets for {@code 0...0}, values below {@code 1e-9},
 * and values above {@code 1e18}; regular buckets split every decimal exponent from
 * {@code 1e-9} to {@code 1e18} into {@code 9 * DECIMAL_MULTIPLIER} ranges.
 * <p>
 * The builder-provided {@code min}, {@code max}, and {@code buckets} values affect only
 * scrape output and cardinality, not the internal recording grid. During collection the
 * child scans occupied VM buckets, folds everything below {@code min} into a single
 * {@code 0...min} range, folds everything above {@code max} into {@code max...+Inf}, and
 * leaves in-range buckets in their native {@code vmrange} form. If the resulting occupied
 * ranges still exceed {@code buckets}, adjacent ranges are merged into at most
 * {@code buckets} exported bucket series for that label set.
 * <p>
 * Export uses Prometheus {@code untyped} series because VictoriaMetrics {@code vmrange}
 * buckets are not Prometheus cumulative {@code le} histogram buckets. Bucket range merging
 * affects only distribution precision in scrape output; {@code _count} and {@code _sum}
 * are accumulated independently and remain exact.
 *
 * @see <a href="https://docs.victoriametrics.com/victoriametrics/keyconcepts/#histogram">VictoriaMetrics histograms</a>
 * @see <a href="https://valyala.medium.com/improving-histogram-usability-for-prometheus-and-grafana-bc7e5df0e350">Improving histogram usability for Prometheus and Grafana</a>
 */
public final class VictoriaMetricsHistogram {

    private static final int E10_MIN = -9;
    private static final int E10_MAX = 18;
    private static final int DECIMAL_MULTIPLIER = 2;
    private static final int BUCKET_SIZE = 9 * DECIMAL_MULTIPLIER;
    private static final int BUCKETS_COUNT = E10_MAX - E10_MIN;
    private static final double DECIMAL_PRECISION = 0.01 / DECIMAL_MULTIPLIER;

    private static final IdxOffset ZERO = new IdxOffset(-1, 0);
    private static final IdxOffset LOWER = new IdxOffset(-1, 1);
    private static final IdxOffset UPPER = new IdxOffset(-1, 2);

    private static final String[] VM_RANGES = new String[3 + BUCKETS_COUNT * BUCKET_SIZE];
    private static final String[] VM_RANGE_STARTS = new String[VM_RANGES.length];
    private static final String[] VM_RANGE_ENDS = new String[VM_RANGES.length];
    private static final Map<PrometheusRegistry, Map<String, Collector>> COLLECTORS = new WeakHashMap<>();

    static {
        VM_RANGE_STARTS[0] = "0";
        VM_RANGE_ENDS[0] = "0";
        VM_RANGES[0] = VM_RANGE_STARTS[0] + "..." + VM_RANGE_ENDS[0];
        VM_RANGE_STARTS[1] = "0";
        VM_RANGE_ENDS[1] = String.format(Locale.US, "%.1fe%d", 1.0, E10_MIN);
        VM_RANGES[1] = VM_RANGE_STARTS[1] + "..." + VM_RANGE_ENDS[1];
        VM_RANGE_STARTS[2] = String.format(Locale.US, "%.1fe%d", 1.0, E10_MAX);
        VM_RANGE_ENDS[2] = "+Inf";
        VM_RANGES[2] = VM_RANGE_STARTS[2] + "..." + VM_RANGE_ENDS[2];

        var start = String.format(Locale.US, "%.1fe%d", 1.0, E10_MIN);
        var idx = 3;
        for (int bucketIdx = 0; bucketIdx < BUCKETS_COUNT; bucketIdx++) {
            for (int offset = 0; offset < BUCKET_SIZE; offset++) {
                var e10 = E10_MIN + bucketIdx;
                var m = 1 + (double) (offset + 1) / DECIMAL_MULTIPLIER;
                if (Math.abs(m - 10) < DECIMAL_PRECISION) {
                    m = 1;
                    e10++;
                }
                var end = String.format(Locale.US, "%.1fe%d", m, e10);
                VM_RANGE_STARTS[idx] = start;
                VM_RANGE_ENDS[idx] = end;
                VM_RANGES[idx] = start + "..." + end;
                start = end;
                idx++;
            }
        }
    }

    private final Collector collector;
    private final Tags tags;
    private final Buckets buckets;

    private VictoriaMetricsHistogram(Collector collector, Tags tags, Buckets buckets) {
        this.collector = collector;
        this.tags = tags;
        this.buckets = buckets;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public VictoriaMetricsHistogram tags(Tags tags) {
        return new VictoriaMetricsHistogram(this.collector, this.tags.and(tags), this.buckets);
    }

    public void record(double value) {
        this.collector.child(this.tags, this.buckets).record(value);
    }

    public void record(long amount, TimeUnit unit) {
        this.collector.child(this.tags, this.buckets).record(amount, unit);
    }

    private static Collector getOrCreate(PrometheusRegistry registry, String name) {
        synchronized (COLLECTORS) {
            var collectors = COLLECTORS.computeIfAbsent(registry, _ -> new ConcurrentHashMap<>());
            return collectors.computeIfAbsent(name, _ -> {
                var collector = new Collector(name);
                registry.register(collector);
                return collector;
            });
        }
    }

    private static Labels toLabels(Tags tags) {
        var names = new ArrayList<String>();
        var values = new ArrayList<String>();
        for (Tag tag : tags) {
            names.add(tag.getKey());
            values.add(tag.getValue());
        }
        return Labels.of(names, values);
    }

    private static String prometheusName(String name, @Nullable String baseUnit) {
        var result = name.replace('.', '_');
        if (baseUnit != null && !result.endsWith("_" + baseUnit)) {
            result += "_" + baseUnit;
        }
        return result;
    }

    private static String getRangeTagValue(IdxOffset idxOffset) {
        return VM_RANGES[getRangeIndex(idxOffset.bucketIdx, idxOffset.offset)];
    }

    private static String getRangeTagValue(IdxOffset from, IdxOffset to) {
        var fromIndex = getRangeIndex(from.bucketIdx, from.offset);
        var toIndex = getRangeIndex(to.bucketIdx, to.offset);
        if (fromIndex == toIndex) {
            return VM_RANGES[fromIndex];
        }
        return VM_RANGE_STARTS[fromIndex] + "..." + VM_RANGE_ENDS[toIndex];
    }

    private static String format(double value) {
        var e10 = (int) Math.floor(Math.log10(value));
        var m = value / Math.pow(10, e10);
        return String.format(Locale.US, "%.1fe%d", m, e10);
    }

    private static int getRangeIndex(int index, int offset) {
        if (index < 0) {
            return offset;
        }
        return 3 + index * BUCKET_SIZE + offset;
    }

    private static IdxOffset getBucketIdxAndOffset(double value) {
        if (value == 0) {
            return ZERO;
        }
        if (Double.POSITIVE_INFINITY == value) {
            return UPPER;
        }

        var e10 = (int) Math.floor(Math.log10(value));
        var bucketIdx = e10 - E10_MIN;
        if (bucketIdx < 0) {
            return LOWER;
        }

        var pow = Math.pow(10, e10);
        if (bucketIdx >= BUCKETS_COUNT) {
            if ((bucketIdx == BUCKETS_COUNT) && (Math.abs(pow - value) < DECIMAL_PRECISION)) {
                return new IdxOffset(BUCKETS_COUNT - 1, BUCKET_SIZE - 1);
            }
            return UPPER;
        }

        var m = ((value / pow) - 1) * DECIMAL_MULTIPLIER;
        var offset = (int) m;
        if (offset < 0) {
            offset = 0;
        } else if (offset >= BUCKET_SIZE) {
            offset = BUCKET_SIZE - 1;
        }

        if (Math.abs((double) offset - m) < DECIMAL_PRECISION) {
            offset--;
            if (offset < 0) {
                bucketIdx--;
                if (bucketIdx < 0) {
                    return LOWER;
                }
                offset = BUCKET_SIZE - 1;
            }
        }

        return new IdxOffset(bucketIdx, offset);
    }

    private record IdxOffset(int bucketIdx, int offset) {}

    private record Buckets(double min, double max, IdxOffset minBucket, IdxOffset maxBucket, int size) {

        private Buckets {
            if (!Double.isFinite(min) || min <= 0) {
                throw new IllegalArgumentException("VictoriaMetrics histogram min must be a positive finite value");
            }
            if (!Double.isFinite(max) || max <= min) {
                throw new IllegalArgumentException("VictoriaMetrics histogram max must be greater than min");
            }
            if (size < 3) {
                throw new IllegalArgumentException("VictoriaMetrics histogram buckets must be greater than or equal to 3");
            }
        }
    }

    public static final class Builder {

        private final String name;
        private Tags tags = Tags.empty();
        @Nullable
        private String baseUnit;
        private Duration min = Duration.ofMillis(1);
        private Duration max = Duration.ofSeconds(90);
        private int buckets = 16;

        private Builder(String name) {
            this.name = name;
        }

        public Builder tags(Tags tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        public Builder baseUnit(String baseUnit) {
            this.baseUnit = baseUnit;
            return this;
        }

        public Builder min(Duration min) {
            this.min = min;
            return this;
        }

        public Builder max(Duration max) {
            this.max = max;
            return this;
        }

        public Builder buckets(int buckets) {
            this.buckets = buckets;
            return this;
        }

        public VictoriaMetricsHistogram register(PrometheusMeterRegistry registry) {
            var collector = getOrCreate(registry.getPrometheusRegistry(), prometheusName(this.name, this.baseUnit));
            var min = this.min.toNanos() / 1_000_000_000.0;
            var max = this.max.toNanos() / 1_000_000_000.0;
            var buckets = new Buckets(
                min,
                max,
                getBucketIdxAndOffset(min),
                getBucketIdxAndOffset(max),
                this.buckets
            );
            return new VictoriaMetricsHistogram(collector, this.tags, buckets);
        }
    }

    private static final class Collector implements MultiCollector {

        private final String name;
        private final ConcurrentHashMap<Labels, Child> children = new ConcurrentHashMap<>();

        private Collector(String name) {
            this.name = name;
        }

        private Child child(Tags tags, Buckets buckets) {
            var labels = toLabels(tags);
            return this.children.computeIfAbsent(labels, l -> new Child(l, buckets));
        }

        @Override
        public MetricSnapshots collect() {
            var bucket = UnknownSnapshot.builder().name(this.name + "_bucket").help("");
            var sum = UnknownSnapshot.builder().name(this.name + "_sum").help("");
            var count = UnknownSnapshot.builder().name(this.name + "_count").help("");

            for (var child : this.children.values()) {
                child.collect(bucket, sum, count);
            }

            return MetricSnapshots.builder()
                .metricSnapshot(bucket.build())
                .metricSnapshot(sum.build())
                .metricSnapshot(count.build())
                .build();
        }
    }

    private static final class Child {

        private final Labels labels;
        private final Buckets buckets;
        private final AtomicReferenceArray<AtomicLongArray> values = new AtomicReferenceArray<>(BUCKETS_COUNT);
        private final AtomicLong zeros = new AtomicLong();
        private final AtomicLong lower = new AtomicLong();
        private final AtomicLong upper = new AtomicLong();
        private final DoubleAdder sum = new DoubleAdder();

        private Child(Labels labels, Buckets buckets) {
            this.labels = labels;
            this.buckets = buckets;
        }

        private void record(long amount, TimeUnit unit) {
            if (amount < 0) {
                return;
            }
            this.record(unit.toNanos(amount) / 1_000_000_000.0);
        }

        private void record(double value) {
            if (Double.isNaN(value) || value < 0) {
                return;
            }
            var idx = getBucketIdxAndOffset(value);
            this.sum.add(value);
            if (idx.bucketIdx < 0) {
                if (idx.offset == 0) {
                    this.zeros.incrementAndGet();
                } else if (idx.offset == 1) {
                    this.lower.incrementAndGet();
                } else {
                    this.upper.incrementAndGet();
                }
                return;
            }

            var bucket = this.values.get(idx.bucketIdx);
            if (bucket == null) {
                bucket = new AtomicLongArray(BUCKET_SIZE);
                if (!this.values.compareAndSet(idx.bucketIdx, null, bucket)) {
                    bucket = this.values.get(idx.bucketIdx);
                }
            }
            bucket.incrementAndGet(idx.offset);
        }

        private void collect(UnknownSnapshot.Builder bucket, UnknownSnapshot.Builder sum, UnknownSnapshot.Builder count) {
            var total = 0L;
            var observedBuckets = new ArrayList<Bucket>();
            total += collectSpecial(observedBuckets, ZERO, this.zeros.get());
            total += collectSpecial(observedBuckets, LOWER, this.lower.get());
            total += collectSpecial(observedBuckets, UPPER, this.upper.get());

            for (int i = 0; i < this.values.length(); i++) {
                var bucketCounters = this.values.get(i);
                if (bucketCounters != null) {
                    for (int j = 0; j < bucketCounters.length(); j++) {
                        var value = bucketCounters.get(j);
                        if (value > 0) {
                            total += collectSpecial(observedBuckets, new IdxOffset(i, j), value);
                        }
                    }
                }
            }

            if (total == 0) {
                return;
            }

            if (observedBuckets.size() <= this.buckets.size()) {
                for (var observedBucket : observedBuckets) {
                    bucket.dataPoint(new UnknownSnapshot.UnknownDataPointSnapshot(
                        observedBucket.value(),
                        this.labels.add("vmrange", observedBucket.range()),
                        null
                    ));
                }
            } else {
                for (int i = 0; i < this.buckets.size(); i++) {
                    var from = i * observedBuckets.size() / this.buckets.size();
                    var to = (i + 1) * observedBuckets.size() / this.buckets.size();
                    var value = 0L;
                    for (int j = from; j < to; j++) {
                        value += observedBuckets.get(j).value();
                    }
                    bucket.dataPoint(new UnknownSnapshot.UnknownDataPointSnapshot(
                        value,
                        this.labels.add("vmrange", range(observedBuckets.get(from), observedBuckets.get(to - 1))),
                        null
                    ));
                }
            }

            sum.dataPoint(new UnknownSnapshot.UnknownDataPointSnapshot(this.sum.sum(), this.labels, null));
            count.dataPoint(new UnknownSnapshot.UnknownDataPointSnapshot(total, this.labels, null));
        }

        private long collectSpecial(ArrayList<Bucket> bucket, IdxOffset idx, long value) {
            if (value > 0) {
                var rangeIndex = getRangeIndex(idx.bucketIdx, idx.offset);
                var minIndex = getRangeIndex(this.buckets.minBucket().bucketIdx, this.buckets.minBucket().offset);
                var maxIndex = getRangeIndex(this.buckets.maxBucket().bucketIdx, this.buckets.maxBucket().offset);
                if (rangeIndex < minIndex) {
                    merge(bucket, new Bucket(idx, "0..." + format(this.buckets.min()), value));
                } else if (rangeIndex > maxIndex) {
                    merge(bucket, new Bucket(idx, format(this.buckets.max()) + "...+Inf", value));
                } else {
                    bucket.add(new Bucket(idx, getRangeTagValue(idx), value));
                }
            }
            return value;
        }

        private static void merge(ArrayList<Bucket> buckets, Bucket bucket) {
            if (!buckets.isEmpty()) {
                var last = buckets.get(buckets.size() - 1);
                if (last.range().equals(bucket.range())) {
                    buckets.set(buckets.size() - 1, new Bucket(last.idx(), last.range(), last.value() + bucket.value()));
                    return;
                }
            }
            buckets.add(bucket);
        }

        private static String range(Bucket from, Bucket to) {
            if (from.range().startsWith("0...") || to.range().endsWith("...+Inf")) {
                var start = from.range().substring(0, from.range().indexOf("..."));
                var end = to.range().substring(to.range().indexOf("...") + 3);
                return start + "..." + end;
            }
            return getRangeTagValue(from.idx(), to.idx());
        }

        private record Bucket(IdxOffset idx, String range, long value) {}
    }
}
