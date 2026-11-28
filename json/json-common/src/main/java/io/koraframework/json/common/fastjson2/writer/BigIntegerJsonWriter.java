package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.math.*;

public class BigIntegerJsonWriter implements JsonWriter<BigInteger> {

    @Override
    public void write(JSONWriter writer, BigInteger object) {
        writer.writeBigInt(object);
    }
}
