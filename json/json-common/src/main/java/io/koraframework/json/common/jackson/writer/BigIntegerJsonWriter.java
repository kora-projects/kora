package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.math.*;

public class BigIntegerJsonWriter implements JsonWriter<BigInteger> {
    @Override
    public void write(JsonGenerator gen, BigInteger object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(object);
        }
    }
}
