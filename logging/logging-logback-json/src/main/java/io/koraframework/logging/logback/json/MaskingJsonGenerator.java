package io.koraframework.logging.logback.json;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.Base64Variant;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.util.JsonGeneratorDelegate;

import java.io.InputStream;
import java.io.Reader;
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
    public JsonGenerator writeNumber(String encodedValue) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(encodedValue);
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

    @Override
    public JsonGenerator writeString(Reader reader, int len) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeString(reader, len);
    }

    @Override
    public JsonGenerator writeUTF8String(byte[] text, int offset, int length) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeUTF8String(text, offset, length);
    }

    @Override
    public JsonGenerator writeRawUTF8String(byte[] text, int offset, int length) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeRawUTF8String(text, offset, length);
    }

    @Override
    public JsonGenerator writeNumber(short v) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(char[] encodedValueBuffer, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeNumber(encodedValueBuffer, offset, len);
    }

    @Override
    public JsonGenerator writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeBinary(bv, data, offset, len);
    }

    @Override
    public int writeBinary(Base64Variant bv, InputStream data, int dataLength) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return 0;
        }
        return super.writeBinary(bv, data, dataLength);
    }

    @Override
    public JsonGenerator writeRawValue(String text) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeRawValue(text);
    }

    @Override
    public JsonGenerator writeRawValue(String text, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeRawValue(text, offset, len);
    }

    @Override
    public JsonGenerator writeRawValue(char[] text, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeRawValue(text, offset, len);
    }

    @Override
    public JsonGenerator writePOJO(@Nullable Object pojo) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writePOJO(pojo);
    }

    @Override
    public JsonGenerator writeEmbeddedObject(@Nullable Object object) throws JacksonException {
        if (this.skipOrMaskScalar()) {
            return this;
        }
        return super.writeEmbeddedObject(object);
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
