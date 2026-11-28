package io.koraframework.netty.common.loom;

import io.koraframework.config.common.annotation.*;

import java.time.*;

@ConfigMapper
public interface LoomCarrierConfig {

    default boolean enabled() {
        return true;
    }

    default Duration timeSliceLatency() {
        return Duration.ofNanos(500_000);
    }

    default Duration timeSliceThroughput() {
        return Duration.ofMillis(1);
    }

    default Duration fifoSwitchTime() {
        return Duration.ofNanos(250_000);
    }

    default Duration taskFifoThreshold() {
        return Duration.ofNanos(250_000);
    }

    default Duration blockTime() {
        return Duration.ofMillis(1);
    }

    default int globalQueueChunkSize() {
        return 1024;
    }

    default int localQueueInitialCapacity() {
        return 256;
    }

    default int throughputModeThreshold() {
        return 2;
    }

    default int workSpillThreshold() {
        return 12;
    }

    default Duration workSpillMinQueueAge() {
        return Duration.ofMillis(1);
    }

    default int normalWarmupTasks() {
        return 0;
    }
}
