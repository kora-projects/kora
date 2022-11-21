package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonWriter;

@JsonWriter
public class DtoJavaBean {
    @JsonField("string_field")
    private String field1;
    @JsonField("int_field")
    private int field2;

    public DtoJavaBean() {
    }

    public DtoJavaBean(String field1, int field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    public String getField1() {
        return field1;
    }

    public void setField1(String field1) {
        this.field1 = field1;
    }

    public int getField2() {
        return field2;
    }

    public void setField2(int field2) {
        this.field2 = field2;
    }
}
