package io.koraframework.json.common;

import com.alibaba.fastjson2.*;

import java.util.*;

public final class FastJsonCodecSupport {
    private FastJsonCodecSupport() {}

    public static JSONException missingRequiredFields(
        JSONReader reader,
        int receivedFields,
        int allRequiredMask,
        String packedRequiredNames,
        String className
    ) {
        int nonReceived = (~receivedFields) & allRequiredMask;
        var missing = new StringBuilder();
        int start = 0;
        int bitIndex = 0;
        int len = packedRequiredNames.length();

        while (start < len) {
            int end = packedRequiredNames.indexOf('\0', start);
            if (end < 0) end = len;
            if ((nonReceived & (1 << bitIndex)) != 0) {
                if (!missing.isEmpty()) missing.append(", ");
                missing.append(packedRequiredNames, start, end);
            }
            start = end + 1;
            bitIndex++;
        }
        return new JSONException(reader.info("Missing required fields: [" + missing + "] in class " + className));
    }

    public static JSONException missingRequiredFields(
        JSONReader reader,
        BitSet receivedFields,
        int requiredCount,
        String packedRequiredNames,
        String className
    ) {
        var missing = new StringBuilder();
        int start = 0;
        int bitIndex = 0;
        int len = packedRequiredNames.length();

        while (start < len && bitIndex < requiredCount) {
            int end = packedRequiredNames.indexOf('\0', start);
            if (end < 0) end = len;
            if (!receivedFields.get(bitIndex)) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(packedRequiredNames, start, end);
            }
            start = end + 1;
            bitIndex++;
        }
        return new JSONException(reader.info("Missing required fields: [" + missing + "] in class " + className));
    }
}
