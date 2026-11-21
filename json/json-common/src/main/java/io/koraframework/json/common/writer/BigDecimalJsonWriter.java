package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.math.*;

public class BigDecimalJsonWriter implements JsonWriter<BigDecimal> {
    @Override
    public void write(JsonGenerator gen, BigDecimal object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(object);
        }
    }

    @Override
    public void write(JSONWriter writer, BigDecimal object) {
        writer.writeDecimal(object);
    }
}
