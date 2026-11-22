package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.math.*;

public class BigDecimalJsonReader implements JsonReader<BigDecimal> {

    @Override
    public BigDecimal read(JSONReader reader) {
        return reader.readBigDecimal();
    }

}
