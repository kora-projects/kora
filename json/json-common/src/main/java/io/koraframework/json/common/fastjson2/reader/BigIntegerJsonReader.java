package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.math.*;

public class BigIntegerJsonReader implements JsonReader<BigInteger> {

    @Override
    public BigInteger read(JSONReader reader) {
        return reader.readBigInteger();
    }
}
