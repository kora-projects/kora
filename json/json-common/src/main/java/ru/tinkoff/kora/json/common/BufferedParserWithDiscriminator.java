package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BufferedParserWithDiscriminator extends JsonParser {

    private int currentPosition = 0;
    private final List<BufferSegment> cache = new ArrayList<>();
    private final JsonParser originalParser;

    public BufferedParserWithDiscriminator(JsonParser originalParser) {
        this.originalParser = originalParser;
    }

    @Override
    public ObjectCodec getCodec() {
        return originalParser.getCodec();
    }

    @Override
    public void setCodec(ObjectCodec oc) {
        originalParser.setCodec(oc);
    }

    @Override
    public Version version() {
        return originalParser.version();
    }

    @Override
    public void close() throws IOException {
        originalParser.close();
    }

    @Override
    public boolean isClosed() {
        return originalParser.isClosed();
    }

    @Override
    public JsonStreamContext getParsingContext() {
        return originalParser.getParsingContext();
    }

    @Override
    public JsonLocation getTokenLocation() {
        return originalParser.getTokenLocation();
    }

    @Override
    public JsonLocation getCurrentLocation() {
        return originalParser.getCurrentLocation();
    }

    @Override
    public JsonToken nextToken() throws IOException {
        currentPosition++;
        if (cache.size() > currentPosition) {
            return cache.get(currentPosition).token;
        }
        return originalParser.nextToken();
    }

    @Override
    public JsonToken nextValue() throws IOException {
        return originalParser.nextValue();
    }

    @Override
    public JsonParser skipChildren() throws IOException {
        return originalParser.skipChildren();
    }

    @Override
    public JsonToken getCurrentToken() {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getCurrentToken();
        } else return cache.get(currentPosition).token;
    }

    @Override
    @Deprecated
    public int getCurrentTokenId() {
        return originalParser.getCurrentTokenId();
    }

    @Override
    public boolean hasCurrentToken() {
        return originalParser.hasCurrentToken();
    }

    @Override
    public boolean hasTokenId(int id) {
        return originalParser.hasTokenId(id);
    }

    @Override
    public boolean hasToken(JsonToken t) {
        return originalParser.hasToken(t);
    }

    @Override
    public void clearCurrentToken() {
        originalParser.clearCurrentToken();
    }

    @Override
    public JsonToken getLastClearedToken() {
        return originalParser.getLastClearedToken();
    }

    @Override
    public void overrideCurrentName(String name) {
        originalParser.overrideCurrentName(name);
    }

    @Override
    public String getCurrentName() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getCurrentName();
        } else {
            return cache.get(currentPosition).name;
        }
    }

    @Override
    public String getText() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getText();
        } else {
            return (String) cache.get(currentPosition).value;
        }
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        return originalParser.getTextCharacters();
    }

    @Override
    public int getTextLength() throws IOException {
        return originalParser.getTextLength();
    }

    @Override
    public int getTextOffset() throws IOException {
        return originalParser.getTextOffset();
    }

    @Override
    public boolean hasTextCharacters() {
        return originalParser.hasTextCharacters();
    }

    @Override
    public Number getNumberValue() throws IOException {
        return originalParser.getNumberValue();
    }

    @Override
    public NumberType getNumberType() throws IOException {
        return originalParser.getNumberType();
    }

    @Override
    public int getIntValue() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getIntValue();
        } else {
            return (int) cache.get(currentPosition).value;
        }
    }

    @Override
    public long getLongValue() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getLongValue();
        } else {
            return (long) cache.get(currentPosition).value;
        }
    }

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getBigIntegerValue();
        } else {
            return (BigInteger) cache.get(currentPosition).value;
        }
    }

    @Override
    public float getFloatValue() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getFloatValue();
        } else {
            return (float) cache.get(currentPosition).value;
        }
    }

    @Override
    public double getDoubleValue() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getDoubleValue();
        } else {
            return (double) cache.get(currentPosition).value;
        }
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getDecimalValue();
        } else {
            return (BigDecimal) cache.get(currentPosition).value;
        }
    }

    @Override
    public boolean getBooleanValue() throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getBooleanValue();
        } else {
            return (boolean) cache.get(currentPosition).value;
        }
    }

    @Override
    public byte[] getBinaryValue(Base64Variant variant) throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getBinaryValue();
        } else {
            ByteArrayBuilder builder = new ByteArrayBuilder();
            variant.decode((String) cache.get(currentPosition).value, builder);
            return builder.toByteArray();
        }
    }

    @Override
    public String getValueAsString(String def) throws IOException {
        if (currentPosition > cache.size() - 1) {
            return originalParser.getValueAsString();
        } else {
            return (String) cache.get(currentPosition).value;
        }
    }

    public void resetPosition() {
        currentPosition = 0;
    }

    @Nullable
    public final String getDiscriminator(String discriminatorField) throws IOException {
        var discriminatorName = new SerializedString(discriminatorField);
        var level  = 0;
        var _token = originalParser.currentToken();
        cache.add(new BufferSegment(_token, null, null));
        while (_token != null) {
            if (_token == JsonToken.START_OBJECT) level++;
            if (_token == JsonToken.END_OBJECT) level--;
            currentPosition++;
            _token = originalParser.nextToken();
            var currentName = originalParser.getCurrentName();
            var value = switch (_token) {
                case VALUE_STRING:
                    yield originalParser.getText();
                case VALUE_NUMBER_INT:
                    var numberType = originalParser.getNumberType();
                    yield switch (numberType) {
                        case INT:
                            yield originalParser.getIntValue();
                        case LONG:
                            yield originalParser.getLongValue();
                        case BIG_INTEGER:
                            yield originalParser.getBigIntegerValue();
                        case FLOAT:
                            yield originalParser.getFloatValue();
                        case DOUBLE:
                            yield originalParser.getDoubleValue();
                        case BIG_DECIMAL:
                            yield originalParser.getDecimalValue();
                    };
                case VALUE_NUMBER_FLOAT:
                    yield originalParser.getFloatValue();
                case VALUE_TRUE:
                    yield true;
                case VALUE_FALSE:
                    yield false;
                default:
                    yield null;
            };
            if ((_token == JsonToken.FIELD_NAME) && discriminatorName.getValue().equals(currentName) && level == 1) {
                originalParser.nextToken();
                return originalParser.getText();
            }
            cache.add(new BufferSegment(_token, currentName, value));
        }
        return null;
    }

    private final record BufferSegment(JsonToken token, String name, Object value) {}
}
