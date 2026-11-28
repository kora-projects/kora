package io.koraframework.json.common.jackson.writer;

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
}
