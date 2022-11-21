package ru.tinkoff.kora.json.annotation.processor;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.openjdk.jmh.annotations.Setup;
import ru.tinkoff.kora.json.annotation.processor.dto.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public abstract class BaseJacksonBenchmark extends BaseBenchmark {
    private static final TypeReference<List<SomeBean>> SOME_BEAN_LIST_REF = new TypeReference<>() {};
    private static final TypeReference<List<BeanWithPropertyConstructor>> BEAN_WITH_CONSTRUCTOR_LIST_REF = new TypeReference<>() {};
    private static final TypeReference<List<ClassicBean>> CLASSIC_BEAN_LIST_REF = new TypeReference<>() {};


    ObjectMapper mapper;
    ObjectWriter mediaItemWriter;
    ObjectReader mediaItemReader;
    ObjectWriter classicBeanWriter;
    ObjectReader classicBeanReader;


    protected abstract ObjectMapper createObjectMapper();

    @Setup
    public void setup() throws Exception {
        mapper = createObjectMapper();
        mediaItemReader = mapper.readerFor(MediaItem.class);
        mediaItemWriter = mapper.writerFor(MediaItem.class);
        classicBeanReader = mapper.readerFor(CLASSIC_BEAN_LIST_REF);
        classicBeanWriter = mapper.writerFor(CLASSIC_BEAN_LIST_REF);
        super.setup();
    }

    @Override
    protected byte[] simpleRecordToJson(SimpleRecord simpleRecord) throws JsonProcessingException {
        return mapper.writeValueAsBytes(simpleRecord);
    }

    @Override
    protected SimpleRecord simpleRecordFromJson(byte[] json) throws IOException {
        return mapper.readValue(json, SimpleRecord.class);
    }

    protected byte[] someBeanListToJson(List<SomeBean> beans) throws JsonProcessingException {
        return this.mapper.writeValueAsBytes(beans);
    }

    protected byte[] beanWithConstructorListToJson(List<BeanWithPropertyConstructor> list) throws IOException {
        return this.mapper.writeValueAsBytes(list);
    }

    protected List<SomeBean> someBeanListFromJson(byte[] bytes) throws IOException {
        return this.mapper.readValue(bytes, SOME_BEAN_LIST_REF);
    }

    protected List<BeanWithPropertyConstructor> beanWithConstructorFromJson(byte[] bytes) throws IOException {
        return this.mapper.readValue(bytes, BEAN_WITH_CONSTRUCTOR_LIST_REF);
    }

    protected void mediaItemToOutputStream(OutputStream os, MediaItem mediaItem) throws IOException {
        this.mediaItemWriter.writeValue(os, mediaItem);
    }

    protected MediaItem mediaItemFromJson(byte[] bytes) throws IOException {
        return this.mediaItemReader.readValue(bytes);
    }

    protected void classicBeanListToOutputStream(OutputStream os, List<ClassicBean> classicBeans) throws IOException {
        this.classicBeanWriter.writeValue(os, classicBeans);
    }

    protected List<ClassicBean> classicBeanFromJson(byte[] bytes) throws IOException {
        return this.classicBeanReader.readValue(bytes);
    }


}
