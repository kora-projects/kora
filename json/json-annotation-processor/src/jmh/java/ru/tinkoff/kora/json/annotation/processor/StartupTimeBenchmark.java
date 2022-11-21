package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.annotations.*;
import ru.tinkoff.kora.json.annotation.processor.dto.*;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@Measurement(iterations = 1)
@Warmup(iterations = 0)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(10)
public class StartupTimeBenchmark {
    @Benchmark
    public byte[] vanilla() throws Exception {
        return singleShot(new ObjectMapper());
    }

    @Benchmark
    public byte[] blackbird() throws Exception {
        return singleShot(new ObjectMapper().registerModule(new BlackbirdModule()));
    }

    @Benchmark
    public byte[] afterburner() throws Exception {
        return singleShot(new ObjectMapper().registerModule(new AfterburnerModule()));
    }

    @Benchmark
    public byte[] kora() throws Exception {
        return new StartupTimeBenchmarkTestRecordJsonWriter(
            new SomeBeanJsonWriter(),
            new ClassicBeanJsonWriter(),
            new BeanWithPropertyConstructorJsonWriter(new SomeBeanJsonWriter()))
            .toByteArray(TestRecord.test());
    }

    @Json
    public static record TestRecord(SomeBean someBean, ClassicBean classicBean, BeanWithPropertyConstructor beanWithPropertyConstructor) {
        public static TestRecord test() {
            final Random random = new Random();
            return new TestRecord(
                SomeBean.random(random),
                new ClassicBean().setUp(),
                new BeanWithPropertyConstructor(42, "foo", 8675309, SomeBean.random(random), SomeEnum.EB));
        }
    }

    private static byte[] singleShot(ObjectMapper mapper) throws JsonProcessingException {
        return mapper.writeValueAsBytes(TestRecord.test());
    }
}
