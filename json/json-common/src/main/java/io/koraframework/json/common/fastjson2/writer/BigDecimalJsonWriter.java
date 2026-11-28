package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.math.*;

public class BigDecimalJsonWriter implements JsonWriter<BigDecimal> {

    @Override
    public void write(JSONWriter writer, BigDecimal object) {
        writer.writeDecimal(object);
    }
}
