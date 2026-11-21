package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
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

    @Override
    public void write(JSONWriter writer, BigInteger object) {
        writer.writeBigInt(object);
    }
}
