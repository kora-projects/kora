package io.koraframework.kafka.common.consumer.containers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerRecordsWrapperConcurrencyTest {

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void partitionsReadConcurrentlyFromOneBatch() throws Exception {
        int partitions = 32, perPartition = 200;
        var executor = Executors.newCachedThreadPool(r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        try {
            for (int round = 0; round < 300; round++) {
                var wrapper = new ConsumerRecordsWrapper<>(batch(partitions, perPartition), new StringDeserializer(), new StringDeserializer());
                var start = new CountDownLatch(1);
                var futures = new ArrayList<Future<?>>();
                for (var tp : wrapper.partitions()) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        var records = wrapper.records(tp);
                        assertEquals(perPartition, records.size());
                        for (var r : records) {
                            assertEquals(tp.partition(), r.partition());
                            assertEquals("v" + tp.partition() + "-" + r.offset(), r.value());
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (var f : futures) {
                    try {
                        f.get(20, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        fail("records(tp) hung in round " + round);
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameRecordIsWrappedOnce() {
        var wrapper = new ConsumerRecordsWrapper<>(batch(2, 3), new StringDeserializer(), new StringDeserializer());
        var tp = new TopicPartition("t", 1);
        var byPartition = wrapper.records(tp);
        var byIterator = new ArrayList<ConsumerRecord<String, String>>();
        wrapper.forEach(r -> {
            if (r.partition() == 1) byIterator.add(r);
        });
        assertEquals(byPartition.size(), byIterator.size());
        for (int i = 0; i < byPartition.size(); i++) {
            assertSame(byPartition.get(i), byIterator.get(i));
        }
    }

    private static ConsumerRecords<byte[], byte[]> batch(int partitions, int perPartition) {
        var map = new HashMap<TopicPartition, List<ConsumerRecord<byte[], byte[]>>>();
        for (int p = 0; p < partitions; p++) {
            var list = new ArrayList<ConsumerRecord<byte[], byte[]>>();
            for (int o = 0; o < perPartition; o++) {
                list.add(new ConsumerRecord<>("t", p, o, ("k" + p + "-" + o).getBytes(StandardCharsets.UTF_8), ("v" + p + "-" + o).getBytes(StandardCharsets.UTF_8)));
            }
            map.put(new TopicPartition("t", p), list);
        }
        return new ConsumerRecords<>(map, Map.of());
    }
}
