package ru.tinkoff.kora.json.annotation.processor;

import org.openjdk.jmh.annotations.Setup;
import ru.tinkoff.kora.json.annotation.processor.dto.*;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.ListJsonReader;
import ru.tinkoff.kora.json.common.ListJsonWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class KoraBenchmark extends BaseBenchmark {
    private JsonWriter<SomeBean> someBeanWriter;
    private JsonReader<SomeBean> someBeanReader;
    private JsonWriter<List<SomeBean>> someBeanListJsonWriter;
    private JsonReader<List<SomeBean>> someBeanListJsonReader;
    private BeanWithPropertyConstructorJsonReader beanWithConstructorReader;
    private JsonReader<List<BeanWithPropertyConstructor>> beanWithConstructorListReader;
    private ClassicBeanJsonWriter classicBeanWriter;
    private ClassicBeanJsonReader classicBeanReader;
    private JsonWriter<List<ClassicBean>> classicBeanListWriter;
    private ListJsonReader<ClassicBean> classicBeanListReader;
    private MediaItemJsonWriter mediaItemWriter;
    private MediaItemJsonReader mediaItemReader;
    private BeanWithPropertyConstructorJsonWriter beanWithConstructorWriter;
    private ListJsonWriter<BeanWithPropertyConstructor> beanWithConstructorListWriter;
    private SimpleRecordJsonReader ottsDtoJsonReader;
    private SimpleRecordJsonWriter ottsDtoJsonWriter;

    @Setup
    public void setup() throws Exception {
        someBeanWriter = new SomeBeanJsonWriter();
        someBeanReader = new SomeBeanJsonReader();
        someBeanListJsonWriter = new ListJsonWriter<>(someBeanWriter);
        someBeanListJsonReader = new ListJsonReader<>(someBeanReader);
        beanWithConstructorReader = new BeanWithPropertyConstructorJsonReader(someBeanReader);
        beanWithConstructorListReader = new ListJsonReader<>(beanWithConstructorReader);
        beanWithConstructorWriter = new BeanWithPropertyConstructorJsonWriter(someBeanWriter);
        beanWithConstructorListWriter = new ListJsonWriter<>(beanWithConstructorWriter);
        classicBeanWriter = new ClassicBeanJsonWriter();
        classicBeanReader = new ClassicBeanJsonReader();
        classicBeanListWriter = new ListJsonWriter<>(classicBeanWriter);
        classicBeanListReader = new ListJsonReader<>(classicBeanReader);
        mediaItemWriter = new MediaItemJsonWriter(new ListJsonWriter<>(new MediaItemPhotoJsonWriter()), new MediaItemContentJsonWriter());
        mediaItemReader = new MediaItemJsonReader(new ListJsonReader<>(new MediaItemPhotoJsonReader()), new MediaItemContentJsonReader());
        ottsDtoJsonReader = new SimpleRecordJsonReader();
        ottsDtoJsonWriter = new SimpleRecordJsonWriter();
        super.setup();
    }

    @Override
    protected byte[] someBeanListToJson(List<SomeBean> beans) throws IOException {
        return this.someBeanListJsonWriter.toByteArray(beans);
    }

    @Override
    protected List<SomeBean> someBeanListFromJson(byte[] bytes) throws IOException {
        return this.someBeanListJsonReader.read(bytes);
    }

    @Override
    protected byte[] beanWithConstructorListToJson(List<BeanWithPropertyConstructor> list) throws IOException {
        return this.beanWithConstructorListWriter.toByteArray(list);
    }

    @Override
    protected List<BeanWithPropertyConstructor> beanWithConstructorFromJson(byte[] bytes) throws IOException {
        return this.beanWithConstructorListReader.read(bytes);
    }

    @Override
    protected void mediaItemToOutputStream(OutputStream os, MediaItem mediaItem) throws IOException {
        os.write(this.mediaItemWriter.toByteArray(mediaItem));
    }

    @Override
    protected MediaItem mediaItemFromJson(byte[] bytes) throws IOException {
        return this.mediaItemReader.read(bytes);
    }

    @Override
    protected void classicBeanListToOutputStream(OutputStream os, List<ClassicBean> classicBeans) throws IOException {
        os.write(this.classicBeanListWriter.toByteArray(classicBeans));
    }

    @Override
    protected List<ClassicBean> classicBeanFromJson(byte[] bytes) throws IOException {
        return this.classicBeanListReader.read(bytes);
    }

    @Override
    protected byte[] simpleRecordToJson(SimpleRecord ottsDto) throws IOException {
        return ottsDtoJsonWriter.toByteArray(ottsDto);
    }

    @Override
    protected SimpleRecord simpleRecordFromJson(byte[] json) throws IOException {
        return ottsDtoJsonReader.read(json);
    }
}
