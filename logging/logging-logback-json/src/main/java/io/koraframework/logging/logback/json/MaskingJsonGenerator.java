package io.koraframework.logging.logback.json;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.util.JsonGeneratorDelegate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;

final class MaskingJsonGenerator extends JsonGeneratorDelegate {

    private static final String ROOT = "";

    private final LoggingEventJsonMasker masker;
    private final ArrayDeque<String> path = new ArrayDeque<>();
    @Nullable
    private String pendingName;
    private int suppressDepth;

    MaskingJsonGenerator(JsonGenerator delegate, LoggingEventJsonMasker masker) {
        super(delegate);
        this.masker = masker;
    }

    @Override
    public JsonGenerator writeName(String name) throws JacksonException {
        if (this.isSuppressing()) {
            return this;
        }
        this.pendingName = name;
        return super.writeName(name);
    }

    @Override
    public JsonGenerator writeName(SerializableString name) throws JacksonException {
        if (this.isSuppressing()) {
            return this;
        }

        this.pendingName = name.getValue();
        return super.writeName(name);
    }

    @Override
    public JsonGenerator writeStartObject() throws JacksonException {
        if (this.isSuppressing()) {
            this.suppressDepth++;
            return this;
        }
        if (this.maskPendingValue(true)) {
            return this;
        }
        this.pushPendingName();
        return super.writeStartObject();
    }

    @Override
    public JsonGenerator writeStartObject(Object forValue) throws JacksonException {
        return this.writeStartObject();
    }

    @Override
    public JsonGenerator writeStartObject(Object forValue, int size) throws JacksonException {
        return this.writeStartObject();
    }

    @Override
    public JsonGenerator writeEndObject() throws JacksonException {
        if (this.isSuppressing()) {
            this.suppressDepth--;
            return this;
        }
        this.popPath();
        return super.writeEndObject();
    }

    @Override
    public JsonGenerator writeStartArray() throws JacksonException {
        if (this.isSuppressing()) {
            this.suppressDepth++;
            return this;
        }
        if (this.maskPendingValue(true)) {
            return this;
        }
        this.pushPendingName();
        return super.writeStartArray();
    }

    @Override
    public JsonGenerator writeStartArray(Object forValue) throws JacksonException {
        return this.writeStartArray();
    }

    @Override
    public JsonGenerator writeStartArray(Object forValue, int size) throws JacksonException {
        return this.writeStartArray();
    }

    @Override
    public JsonGenerator writeEndArray() throws JacksonException {
        if (this.isSuppressing()) {
            this.suppressDepth--;
            return this;
        }
        this.popPath();
        return super.writeEndArray();
    }

    @Override
    public JsonGenerator writeString(String text) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeString(text);
    }

    @Override
    public JsonGenerator writeString(char[] text, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeString(text, offset, len);
    }

    @Override
    public JsonGenerator writeString(SerializableString text) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeString(text);
    }

    @Override
    public JsonGenerator writeNumber(int v) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(long v) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(double v) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(float v) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(BigInteger v) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(BigDecimal v) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeBoolean(boolean state) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeBoolean(state);
    }

    @Override
    public JsonGenerator writeNull() throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNull();
    }

    private boolean skipOrMaskScalar() throws JacksonException {
        if (this.isSuppressing()) {
            return true;
        }
        if (this.maskPendingValue(false)) {
            return true;
        }
        this.pendingName = null;
        return false;
    }

    private boolean isSuppressing() {
        return this.suppressDepth > 0;
    }

    private boolean maskPendingValue(boolean structuredValue) throws JacksonException {
        if (this.pendingName == null) {
            return false;
        }
        var fieldName = this.pendingName;
        var fieldPath = this.path(fieldName);
        if (!this.masker.shouldMask(fieldPath, fieldName)) {
            return false;
        }
        this.pendingName = null;
        this.masker.writeMasked(fieldPath, fieldName, super.delegate);
        if (structuredValue) {
            this.suppressDepth = 1;
        }
        return true;
    }

    private void pushPendingName() {
        if (this.pendingName == null) {
            this.path.addLast(ROOT);
        } else {
            this.path.addLast(this.pendingName);
            this.pendingName = null;
        }
    }

    private void popPath() {
        if (!this.path.isEmpty()) {
            this.path.removeLast();
        }
    }

    private String path(String fieldName) {
        if (this.path.isEmpty()) {
            return fieldName;
        }

        var b = new StringBuilder();
        for (String part : this.path) {
            if (!part.isEmpty()) {
                if (!b.isEmpty()) {
                    b.append('.');
                }
                b.append(part);
            }
        }
        if (!b.isEmpty()) {
            b.append('.');
        }
        b.append(fieldName);
        return b.toString();
    }
}
