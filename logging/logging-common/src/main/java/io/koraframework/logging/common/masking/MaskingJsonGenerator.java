package io.koraframework.logging.common.masking;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.Base64Variant;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.util.JsonGeneratorDelegate;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;

public final class MaskingJsonGenerator extends JsonGeneratorDelegate {
    private final MaskingRules<?> rules;
    private final Deque<String> path = new ArrayDeque<>();
    private final Deque<Boolean> pathSegmentPushed = new ArrayDeque<>();
    private @Nullable String pendingFieldName;
    private int suppressDepth;

    public MaskingJsonGenerator(JsonGenerator delegate, MaskingRules<?> rules) {
        super(delegate);
        this.rules = rules;
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
    public JsonGenerator writeStartObject() throws JacksonException {
        if (this.suppressDepth > 0) {
            this.suppressDepth++;
            return this;
        }
        var fieldName = this.pendingFieldName;
        if (fieldName != null) {
            var strategy = this.strategy(fieldName);
            if (strategy != null) {
                this.writeMask(strategy, null);
                this.pendingFieldName = null;
                this.suppressDepth = 1;
                return this;
            }
            this.path.addLast(fieldName);
            this.pathSegmentPushed.push(true);
            this.pendingFieldName = null;
        } else {
            this.pathSegmentPushed.push(false);
        }
        return super.writeStartObject();
    }

    @Override
    public JsonGenerator writeEndObject() throws JacksonException {
        if (this.suppressDepth > 0) {
            this.suppressDepth--;
            return this;
        }
        super.writeEndObject();
        this.popContainer();
        return this;
    }

    @Override
    public JsonGenerator writeStartArray() throws JacksonException {
        if (this.suppressDepth > 0) {
            this.suppressDepth++;
            return this;
        }
        var fieldName = this.pendingFieldName;
        if (fieldName != null) {
            var strategy = this.strategy(fieldName);
            if (strategy != null) {
                this.writeMask(strategy, null);
                this.pendingFieldName = null;
                this.suppressDepth = 1;
                return this;
            }
            this.path.addLast(fieldName);
            this.pathSegmentPushed.push(true);
            this.pendingFieldName = null;
        } else {
            this.pathSegmentPushed.push(false);
        }
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
        if (this.suppressDepth > 0) {
            this.suppressDepth--;
            return this;
        }
        super.writeEndArray();
        this.popContainer();
        return this;
    }

    @Override
    public JsonGenerator writeName(String name) throws JacksonException {
        if (this.suppressDepth > 0) {
            return this;
        }
        super.writeName(name);
        this.pendingFieldName = name;
        return this;
    }

    @Override
    public JsonGenerator writeName(SerializableString name) throws JacksonException {
        return this.writeName(name.getValue());
    }

    @Override
    public JsonGenerator writeString(String text) throws JacksonException {
        if (this.skipOrMaskScalar(text)) {
            return this;
        }
        return super.writeString(text);
    }

    @Override
    public JsonGenerator writeString(char[] text, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar(new String(Arrays.copyOfRange(text, offset, offset + len)))) {
            return this;
        }
        return super.writeString(text, offset, len);
    }

    @Override
    public JsonGenerator writeString(SerializableString text) throws JacksonException {
        if (this.skipOrMaskScalar(text.getValue())) {
            return this;
        }
        return super.writeString(text);
    }

    @Override
    public JsonGenerator writeNumber(short v) throws JacksonException {
        if (this.skipOrMaskScalar(v)) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(int v) throws JacksonException {
        if (this.skipOrMaskScalar(v)) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(long v) throws JacksonException {
        if (this.skipOrMaskScalar(v)) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(BigInteger v) throws JacksonException {
        if (this.skipOrMaskScalar(v)) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(double v) throws JacksonException {
        if (this.skipOrMaskScalar(v)) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(float v) throws JacksonException {
        if (this.skipOrMaskScalar(v)) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(BigDecimal v) throws JacksonException {
        if (this.skipOrMaskScalar(v)) {
            return this;
        }
        return super.writeNumber(v);
    }

    @Override
    public JsonGenerator writeNumber(String encodedValue) throws JacksonException {
        if (this.skipOrMaskScalar(encodedValue)) {
            return this;
        }
        return super.writeNumber(encodedValue);
    }

    @Override
    public JsonGenerator writeBoolean(boolean state) throws JacksonException {
        if (this.skipOrMaskScalar(state)) {
            return this;
        }
        return super.writeBoolean(state);
    }

    @Override
    public JsonGenerator writeNull() throws JacksonException {
        if (this.skipOrMaskScalar(null)) {
            return this;
        }
        return super.writeNull();
    }

    @Override
    public JsonGenerator writeString(Reader reader, int len) throws JacksonException {
        if (this.isMasked()) {
            this.skipOrMaskScalar(readString(reader, len));
            return this;
        }
        this.pendingFieldName = null;
        return super.writeString(reader, len);
    }

    @Override
    public JsonGenerator writeUTF8String(byte[] text, int offset, int length) throws JacksonException {
        if (this.skipOrMaskScalar(new String(text, offset, length, StandardCharsets.UTF_8))) {
            return this;
        }
        return super.writeUTF8String(text, offset, length);
    }

    @Override
    public JsonGenerator writeRawUTF8String(byte[] text, int offset, int length) throws JacksonException {
        if (this.skipOrMaskScalar(new String(text, offset, length, StandardCharsets.UTF_8))) {
            return this;
        }
        return super.writeRawUTF8String(text, offset, length);
    }

    @Override
    public JsonGenerator writeNumber(char[] encodedValueBuffer, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar(new String(encodedValueBuffer, offset, len))) {
            return this;
        }
        return super.writeNumber(encodedValueBuffer, offset, len);
    }

    @Override
    public JsonGenerator writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws JacksonException {
        var value = offset == 0 && len == data.length ? data : Arrays.copyOfRange(data, offset, offset + len);
        if (this.skipOrMaskScalar(value)) {
            return this;
        }
        return super.writeBinary(bv, data, offset, len);
    }

    @Override
    public int writeBinary(Base64Variant bv, InputStream data, int dataLength) throws JacksonException {
        if (this.isMasked()) {
            var value = readBytes(data, dataLength);
            this.skipOrMaskScalar(value);
            return value.length;
        }
        this.pendingFieldName = null;
        return super.writeBinary(bv, data, dataLength);
    }

    @Override
    public JsonGenerator writeRawValue(String text) throws JacksonException {
        if (this.skipOrMaskScalar(unquote(text))) {
            return this;
        }
        return super.writeRawValue(text);
    }

    @Override
    public JsonGenerator writeRawValue(String text, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar(unquote(text.substring(offset, offset + len)))) {
            return this;
        }
        return super.writeRawValue(text, offset, len);
    }

    @Override
    public JsonGenerator writeRawValue(char[] text, int offset, int len) throws JacksonException {
        if (this.skipOrMaskScalar(unquote(new String(text, offset, len)))) {
            return this;
        }
        return super.writeRawValue(text, offset, len);
    }

    @Override
    public JsonGenerator writePOJO(@Nullable Object pojo) throws JacksonException {
        if (this.skipOrMaskScalar(pojo)) {
            return this;
        }
        return super.writePOJO(pojo);
    }

    @Override
    public JsonGenerator writeEmbeddedObject(@Nullable Object object) throws JacksonException {
        if (this.skipOrMaskScalar(object)) {
            return this;
        }
        return super.writeEmbeddedObject(object);
    }

    private boolean isMasked() {
        if (this.suppressDepth > 0) {
            return true;
        }
        var fieldName = this.pendingFieldName;
        return fieldName != null && this.strategy(fieldName) != null;
    }

    private boolean skipOrMaskScalar(@Nullable Object value) throws JacksonException {
        if (this.suppressDepth > 0) {
            return true;
        }
        var fieldName = this.pendingFieldName;
        if (fieldName == null) {
            return false;
        }
        var strategy = this.strategy(fieldName);
        if (strategy != null) {
            this.writeMask(strategy, value);
            this.pendingFieldName = null;
            return true;
        }
        this.pendingFieldName = null;
        return false;
    }

    @Nullable
    private MaskingStrategy strategy(String fieldName) {
        var fullPath = new ArrayList<String>(this.path.size() + 1);
        fullPath.addAll(this.path);
        fullPath.add(fieldName);
        return this.rules.strategy(fullPath, fieldName);
    }

    private void writeMask(MaskingStrategy strategy, @Nullable Object value) throws JacksonException {
        if (value == null) {
            super.writeNull();
        } else {
            super.writeString(strategy.mask(value));
        }
    }

    private static String unquote(String rawValue) {
        if (rawValue.length() >= 2 && rawValue.charAt(0) == '"' && rawValue.charAt(rawValue.length() - 1) == '"') {
            return rawValue.substring(1, rawValue.length() - 1);
        }
        return rawValue;
    }

    private static byte[] readBytes(InputStream data, int dataLength) throws JacksonException {
        try {
            return dataLength < 0 ? data.readAllBytes() : data.readNBytes(dataLength);
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
    }

    private static String readString(Reader reader, int len) throws JacksonException {
        try {
            if (len < 0) {
                var writer = new StringWriter();
                reader.transferTo(writer);
                return writer.toString();
            }
            var buffer = new char[len];
            var read = 0;
            while (read < len) {
                var n = reader.read(buffer, read, len - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buffer, 0, read);
        } catch (IOException e) {
            throw JacksonIOException.construct(e);
        }
    }

    private void popContainer() {
        if (this.pathSegmentPushed.isEmpty()) {
            return;
        }
        if (this.pathSegmentPushed.pop() && !this.path.isEmpty()) {
            this.path.removeLast();
        }
    }
}
