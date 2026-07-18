package io.koraframework.logging.common.masking;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.util.JsonGeneratorDelegate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public final class MaskingJsonGenerator extends JsonGeneratorDelegate {
    private final MaskingMetadata<?> metadata;
    private final Deque<Context> stack = new ArrayDeque<>();
    private int suppressDepth;

    public MaskingJsonGenerator(JsonGenerator delegate, MaskingMetadata<?> metadata) {
        super(delegate);
        this.metadata = metadata;
    }

    @Override
    public JsonGenerator writeStartObject(Object forValue) throws JacksonException {
        if (this.suppressDepth > 0) {
            this.suppressDepth++;
            return this;
        }
        var pending = this.pending();
        if (pending != null && pending.kind() == MaskingFieldMeta.Kind.MASK) {
            this.writeMask(pending, null);
            this.clearPending();
            this.suppressDepth = 1;
            return this;
        }
        this.clearPending();
        super.writeStartObject(forValue);
        this.stack.push(new Context(this.findMeta(forValue), null, null));
        return this;
    }

    @Override
    public JsonGenerator writeStartObject() throws JacksonException {
        if (this.suppressDepth > 0) {
            this.suppressDepth++;
            return this;
        }
        var pending = this.pending();
        if (pending != null && pending.kind() == MaskingFieldMeta.Kind.MASK) {
            this.writeMask(pending, null);
            this.clearPending();
            this.suppressDepth = 1;
            return this;
        }
        var meta = this.findMeta(this.nextObjectType());
        this.clearPending();
        super.writeStartObject();
        this.stack.push(new Context(meta, null, null));
        return this;
    }

    @Override
    public JsonGenerator writeEndObject() throws JacksonException {
        if (this.suppressDepth > 0) {
            this.suppressDepth--;
            return this;
        }
        super.writeEndObject();
        if (!this.stack.isEmpty()) {
            this.stack.pop();
        }
        return this;
    }

    @Override
    public JsonGenerator writeStartArray() throws JacksonException {
        if (this.suppressDepth > 0) {
            this.suppressDepth++;
            return this;
        }
        var pending = this.pending();
        if (pending != null && pending.kind() == MaskingFieldMeta.Kind.MASK) {
            this.writeMask(pending, null);
            this.clearPending();
            this.suppressDepth = 1;
            return this;
        }
        var elementType = pending != null
            && (pending.kind() == MaskingFieldMeta.Kind.COLLECTION || pending.kind() == MaskingFieldMeta.Kind.MAP_VALUE)
            ? pending.type()
            : null;
        this.clearPending();
        super.writeStartArray();
        this.stack.push(new Context(null, null, elementType));
        return this;
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
        if (!this.stack.isEmpty()) {
            this.stack.pop();
        }
        return this;
    }

    @Override
    public JsonGenerator writeName(String name) throws JacksonException {
        if (this.suppressDepth > 0) {
            return this;
        }
        super.writeName(name);
        if (!this.stack.isEmpty() && this.stack.peek().classMeta() != null) {
            this.stack.peek().pending = this.stack.peek().classMeta().field(name);
        }
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
        var pending = this.pending();
        if (pending != null) {
            if (pending.kind() == MaskingFieldMeta.Kind.MASK) {
                this.writeMask(pending, value);
            }
            this.clearPending();
            return pending.kind() == MaskingFieldMeta.Kind.MASK;
        }
        return false;
    }

    private void writeMask(MaskingFieldMeta fieldMeta, @Nullable Object value) throws JacksonException {
        var rule = fieldMeta.rule();
        super.writeString(rule == null ? "***" : rule.apply(value));
    }

    @Nullable
    private MaskingFieldMeta pending() {
        return this.stack.isEmpty() ? null : this.stack.peek().pending;
    }

    private void clearPending() {
        if (!this.stack.isEmpty()) {
            this.stack.peek().pending = null;
        }
    }

    @Nullable
    private Class<?> nextObjectType() {
        var pending = this.pending();
        if (pending != null && pending.type() != null) {
            return pending.type();
        }
        if (!this.stack.isEmpty()) {
            return this.stack.peek().arrayElementType();
        }
        return null;
    }

    @Nullable
    private MaskingClassMeta findMeta(@Nullable Object value) {
        return value == null ? null : this.metadata.metadata(value.getClass());
    }

    @Nullable
    private MaskingClassMeta findMeta(@Nullable Class<?> type) {
        return type == null ? null : this.metadata.metadata(type);
    }

    private static final class Context {
        private final @Nullable MaskingClassMeta classMeta;
        private @Nullable MaskingFieldMeta pending;
        private final @Nullable Class<?> arrayElementType;

        private Context(@Nullable MaskingClassMeta classMeta, @Nullable MaskingFieldMeta pending, @Nullable Class<?> arrayElementType) {
            this.classMeta = classMeta;
            this.pending = pending;
            this.arrayElementType = arrayElementType;
        }

        @Nullable
        private MaskingClassMeta classMeta() {
            return this.classMeta;
        }

        @Nullable
        private Class<?> arrayElementType() {
            return this.arrayElementType;
        }
    }
}
