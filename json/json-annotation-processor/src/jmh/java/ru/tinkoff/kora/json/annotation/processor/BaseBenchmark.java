package ru.tinkoff.kora.json.annotation.processor;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ru.tinkoff.kora.json.annotation.processor.dto.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Measurement(time = 5, iterations = 5)
@Warmup(time = 5, iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
public abstract class BaseBenchmark {
    SimpleRecord simpleRecord;
    byte[] simpleRecordJson;
    byte[] simpleRecordShuffledJson;

    Random random = new Random(1337);

    List<SomeBean> someBeanList;
    byte[] someBeanListJson;

    List<BeanWithPropertyConstructor> beanWithPropertyConstructorList;
    byte[] beanWithPropertyConstructorListJson;

    byte[] mediaItemJson;

    List<ClassicBean> classicBeans;
    byte[] classicBeansJson;


    @Setup
    public void setup() throws Exception {
        var om = new ObjectMapper();

        simpleRecord = new SimpleRecord(42, "str", false);
        simpleRecordJson = om.writeValueAsBytes(simpleRecord);
        test(simpleRecord, this::simpleRecordToJson, this::simpleRecordFromJson);
        simpleRecordShuffledJson = """
            {"field2": "test", "field1": 42, "field3": false}
            """.getBytes(StandardCharsets.UTF_8);


        someBeanList = IntStream.range(0, 1000)
            .mapToObj(i -> SomeBean.random(random))
            .toList();
        someBeanListJson = om.writeValueAsBytes(someBeanList);
        test(someBeanList, this::someBeanListToJson, this::someBeanListFromJson);

        beanWithPropertyConstructorList = IntStream.range(0, 1000)
            .mapToObj(i -> BeanWithPropertyConstructor.random(random))
            .toList();
        beanWithPropertyConstructorListJson = om.writeValueAsBytes(beanWithPropertyConstructorList);
        test(beanWithPropertyConstructorList, this::beanWithConstructorListToJson, this::beanWithConstructorFromJson);


        mediaItemJson = om.writeValueAsBytes(MediaItem.SAMPLE);
        test(MediaItem.SAMPLE, mediaItem -> {
            var baos = new ByteArrayOutputStream();
            this.mediaItemToOutputStream(baos, mediaItem);
            return baos.toByteArray();
        }, this::mediaItemFromJson);


        final ClassicBean classicBean = new ClassicBean();
        classicBean.setUp();
        classicBeans = Collections.nCopies(1000, classicBean);
        classicBeansJson = om.writeValueAsBytes(classicBeans);
        test(classicBeans, beans -> {
            var baos = new ByteArrayOutputStream();
            this.classicBeanListToOutputStream(baos, beans);
            return baos.toByteArray();
        }, this::classicBeanFromJson);
    }

    public interface IoFunction<T, R> {
        R apply(T t) throws IOException;
    }

    private static <T> void test(T object, IoFunction<T, byte[]> toJson, IoFunction<byte[], T> fromJson) throws IOException {
        var json = toJson.apply(object);
        var parsed = fromJson.apply(json);
        if (!parsed.equals(object)) {
            throw new RuntimeException();
        }
    }

    protected abstract byte[] someBeanListToJson(List<SomeBean> beans) throws IOException;

    protected abstract List<SomeBean> someBeanListFromJson(byte[] bytes) throws IOException;


    protected abstract byte[] beanWithConstructorListToJson(List<BeanWithPropertyConstructor> list) throws IOException;

    protected abstract List<BeanWithPropertyConstructor> beanWithConstructorFromJson(byte[] bytes) throws IOException;


    protected abstract void mediaItemToOutputStream(OutputStream os, MediaItem mediaItem) throws IOException;

    protected abstract MediaItem mediaItemFromJson(byte[] bytes) throws IOException;


    protected abstract void classicBeanListToOutputStream(OutputStream os, List<ClassicBean> classicBeans) throws IOException;

    protected abstract List<ClassicBean> classicBeanFromJson(byte[] bytes) throws IOException;


    protected abstract byte[] simpleRecordToJson(SimpleRecord ottsDto) throws IOException;

    protected abstract SimpleRecord simpleRecordFromJson(byte[] json) throws IOException;


    @Benchmark
    public byte[] simpleRecordToJson() throws Exception {
        return this.simpleRecordToJson(simpleRecord);
    }

    @Benchmark
    public SimpleRecord simpleRecordFromJson() throws Exception {
        return this.simpleRecordFromJson(simpleRecordJson);
    }

    @Benchmark
    public SimpleRecord simpleRecordFromShuffledJson() throws Exception {
        return this.simpleRecordFromJson(simpleRecordShuffledJson);
    }

    @Benchmark
    public byte[] someBeanListToJson() throws Exception {
        return this.someBeanListToJson(someBeanList);
    }

    @Benchmark
    public List<SomeBean> someBeanListFromJson() throws Exception {
        return this.someBeanListFromJson(someBeanListJson);
    }

    @Benchmark
    public List<BeanWithPropertyConstructor> beanWithConstructorFromJson() throws Exception {
        return this.beanWithConstructorFromJson(someBeanListJson);
    }

    @Benchmark
    public void mediaItemToJson(final Blackhole bh) throws Exception {
        this.mediaItemToOutputStream(new NopOutputStream(bh), MediaItem.SAMPLE);
    }

    @Benchmark
    public MediaItem mediaItemFromJson() throws Exception {
        return this.mediaItemFromJson(mediaItemJson);
    }

    @Benchmark
    public void classicBeanToJson(final Blackhole bh) throws Exception {
        this.classicBeanListToOutputStream(new NopOutputStream(bh), classicBeans);
    }

    @Benchmark
    public List<ClassicBean> classicBeanFromJson() throws Exception {
        return this.classicBeanFromJson(classicBeansJson);
    }
}
