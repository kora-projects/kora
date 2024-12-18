package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.core.instrument.distribution.CountAtBucket;

import java.util.Arrays;
import java.util.Iterator;

class FixedBoundaryDoubleHistogram {

    final AtomicDoubleArray values;

    private final double[] buckets;

    private final boolean isCumulativeBucketCounts;

    /**
     * Creates a FixedBoundaryHistogram which tracks the count of values for each bucket
     * bound.
     *
     * @param buckets                  sorted bucket boundaries
     * @param isCumulativeBucketCounts - whether the count values should be cumulative
     *                                 count of lower buckets and current bucket.
     */
    FixedBoundaryDoubleHistogram(double[] buckets, boolean isCumulativeBucketCounts) {
        this.buckets = buckets;
        this.values = new AtomicDoubleArray(buckets.length);
        this.isCumulativeBucketCounts = isCumulativeBucketCounts;
    }

    double[] getBuckets() {
        return this.buckets;
    }

    /**
     * Returns the number of values that was recorded between previous bucket and the
     * queried bucket (upper bound inclusive).
     *
     * @param bucket - the bucket to find values for
     * @return 0 if bucket is not a valid bucket otherwise number of values recorded
     * between (previous bucket, bucket]
     */
    private double countAtBucket(double bucket) {
        int index = Arrays.binarySearch(buckets, bucket);
        if (index < 0)
            return 0;
        return values.get(index);
    }

    void reset() {
        for (int i = 0; i < values.length(); i++) {
            values.set(i, 0);
        }
    }

    void record(double value) {
        int index = leastLessThanOrEqualTo(value);
        if (index > -1)
            values.incrementAndGet(index);
    }

    /**
     * The least bucket that is less than or equal to a valueToRecord. Returns -1, if the
     * valueToRecord is greater than the highest bucket.
     */
    // VisibleForTesting
    int leastLessThanOrEqualTo(double valueToRecord) {
        int low = 0;
        int high = buckets.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            double bucket = buckets[mid];
            if (bucket < valueToRecord)
                low = mid + 1;
            else if (bucket > valueToRecord)
                high = mid - 1;
            else
                return mid; // exact match
        }

        return low < buckets.length ? low : -1;
    }

    Iterator<CountAtBucket> countsAtValues(Iterator<Double> buckets) {
        return new Iterator<>() {
            private double cumulativeCount = 0.0;

            @Override
            public boolean hasNext() {
                return buckets.hasNext();
            }

            @Override
            public CountAtBucket next() {
                double bucket = buckets.next();
                double count = countAtBucket(bucket);
                if (isCumulativeBucketCounts) {
                    cumulativeCount += count;
                    return new CountAtBucket(bucket, cumulativeCount);
                } else {
                    return new CountAtBucket(bucket, count);
                }
            }
        };
    }

    /**
     * Returns the list of {@link CountAtBucket} for each of the buckets tracked by this
     * histogram.
     */
    CountAtBucket[] getCountAtBuckets() {
        CountAtBucket[] countAtBuckets = new CountAtBucket[this.buckets.length];
        long cumulativeCount = 0;

        for (int i = 0; i < this.buckets.length; i++) {
            final double valueAtCurrentBucket = values.get(i);
            if (isCumulativeBucketCounts) {
                cumulativeCount += valueAtCurrentBucket;
                countAtBuckets[i] = new CountAtBucket(buckets[i], cumulativeCount);
            } else {
                countAtBuckets[i] = new CountAtBucket(buckets[i], valueAtCurrentBucket);
            }
        }
        return countAtBuckets;
    }
}
