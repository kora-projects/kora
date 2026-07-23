package io.koraframework.logging.common.masking;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.util.JsonGeneratorDelegate;

import java.math.BigDecimal;
import java.math.BigInteger;
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

    private void popContainer() {
        if (this.pathSegmentPushed.isEmpty()) {
            return;
        }
        if (this.pathSegmentPushed.pop() && !this.path.isEmpty()) {
            this.path.removeLast();
        }
    }
}
