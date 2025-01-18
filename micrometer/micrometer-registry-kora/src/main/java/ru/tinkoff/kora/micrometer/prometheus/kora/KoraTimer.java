package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.prometheus.HistogramFlavor;
import io.prometheus.client.exemplars.CounterExemplarSampler;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.ExemplarSampler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * {@link Timer} for Prometheus.
 * <p>
 * Credits to Jon Schneider
 * Credits to Jonatan Ivanov
 */
class KoraTimer extends AbstractTimer {

    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    private final LongAdder count = new LongAdder();

    private final LongAdder totalTime = new LongAdder();

    private final TimeWindowMax max;

    private final HistogramFlavor histogramFlavor;

    @Nullable
    private final Histogram histogram;

    @Nullable
    private final ExemplarSampler exemplarSampler;

    @Nullable
    private final AtomicReference<Exemplar> lastExemplar;

    private boolean histogramExemplarsEnabled = false;

    KoraTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
              PauseDetector pauseDetector, HistogramFlavor histogramFlavor, @Nullable ExemplarSampler exemplarSampler) {
        super(id, clock,
            DistributionStatisticConfig.builder()
                .percentilesHistogram(false)
                .serviceLevelObjectives()
                .build()
                .merge(distributionStatisticConfig),
            pauseDetector, TimeUnit.SECONDS, false);

        this.histogramFlavor = histogramFlavor;
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            switch (histogramFlavor) {
                case Prometheus:
                    var prometheusHistogram = new KoraLongHistogram(clock, distributionStatisticConfig, exemplarSampler);
                    this.histogram = prometheusHistogram;
                    this.histogramExemplarsEnabled = prometheusHistogram.isExemplarsEnabled();
                    break;
                case VictoriaMetrics:
                    this.histogram = new FixedBoundaryVictoriaMetricsHistogram();
                    break;
                default:
                    this.histogram = null;
                    break;
            }
        } else {
            this.histogram = null;
        }

        if (!this.histogramExemplarsEnabled && exemplarSampler != null) {
            this.exemplarSampler = exemplarSampler;
            this.lastExemplar = new AtomicReference<>();
        } else {
            this.exemplarSampler = null;
            this.lastExemplar = null;
        }
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        count.increment();
        long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
        totalTime.add(nanoAmount);
        max.record(nanoAmount, TimeUnit.NANOSECONDS);

        if (histogram != null) {
            histogram.recordLong(nanoAmount);
        }

        if (!histogramExemplarsEnabled && exemplarSampler != null) {
            updateLastExemplar(TimeUtils.nanosToUnit(amount, baseTimeUnit()), exemplarSampler);
        }
    }

    // Similar to exemplar.updateAndGet(...) but it does nothing if the next value is null
    private void updateLastExemplar(double amount, @NonNull CounterExemplarSampler exemplarSampler) {
        Exemplar prev;
        Exemplar next;
        do {
            prev = lastExemplar.get();
            next = exemplarSampler.sample(amount, prev);
        }
        while (next != null && next != prev && !lastExemplar.compareAndSet(prev, next));
    }

    @Nullable
    Exemplar[] histogramExemplars() {
        if (histogramExemplarsEnabled) {
            return ((KoraDoubleHistogram) histogram).exemplars();
        } else {
            return null;
        }
    }

    @Nullable
    Exemplar lastExemplar() {
        if (histogramExemplarsEnabled) {
            return ((KoraDoubleHistogram) histogram).lastExemplar();
        } else {
            return lastExemplar != null ? lastExemplar.get() : null;
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return max.poll(unit);
    }

    public HistogramFlavor histogramFlavor() {
        return histogramFlavor;
    }

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is
     * based on a rolling histogram. Prometheus requires a histogram that accumulates
     * values over the lifetime of the app.
     *
     * @return Cumulative histogram buckets.
     */
    public CountAtBucket[] histogramCounts() {
        return histogram == null ? EMPTY_HISTOGRAM : histogram.takeSnapshot(0, 0, 0).histogramCounts();
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();

        if (histogram == null) {
            return snapshot;
        }

        return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(), snapshot.percentileValues(),
            histogramCounts(), snapshot::outputSummary);
    }
}
