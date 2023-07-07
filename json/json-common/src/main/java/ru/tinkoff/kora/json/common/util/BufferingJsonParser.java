package ru.tinkoff.kora.json.common.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;
import ru.tinkoff.kora.json.common.JsonCommonModule;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

public class BufferingJsonParser extends ParserBase {
    private final ArrayList<JsonSegment> tokens = new ArrayList<>();
    private final JsonParser delegate;
    private int currentToken;

    public BufferingJsonParser(JsonParser delegate) throws IOException {
        super(new IOContext(JsonCommonModule.JSON_FACTORY._getBufferRecycler(), delegate, false), delegate.getFeatureMask());
        this.delegate = delegate;
        this._currToken = delegate.currentToken();
        this.tokens.add(this.data(this._currToken, this.delegate));
        this.currentToken = 1;
    }

    public JsonSegmentJsonParser reset() {
        this.currentToken = -1;
        var data = this.tokens.get(0);
        this._currToken = data.token();
        this._textBuffer.resetWithShared(data.data(), 0, data.data().length);

        return new JsonSegmentJsonParser(this._ioContext, this._features, new ArrayList<>(this.tokens));
    }

    @Override
    protected void _closeInput() {

    }

    @Override
    public ObjectCodec getCodec() {
        return this.delegate.getCodec();
    }

    @Override
    public void setCodec(ObjectCodec oc) {
        this.delegate.setCodec(oc);
    }

    private JsonSegment data(JsonToken token, JsonParser parser) throws IOException {
        var textCharacters = parser.getTextCharacters();
        var textOffset = parser.getTextOffset();
        var textLength = parser.getTextLength();
        return new JsonSegment(token, Arrays.copyOfRange(textCharacters, textOffset, textOffset + textLength));
    }

    @Override
    public JsonToken nextToken() throws IOException {
        if (this.currentToken < 0) {
            this.currentToken--;
            var data = this.currentData();
            if (data != null) {
                var nextToken = data.token();
                if (nextToken == JsonToken.FIELD_NAME) {
                    this._parsingContext.setCurrentName(new String(data.data()));
                }
                this._currToken = nextToken;
                this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
                return nextToken;
            } else {
                var token = this.delegate.nextToken();
                if (token == JsonToken.FIELD_NAME) {
                    this._parsingContext.setCurrentName(this.delegate.currentName());
                }
                if (token.isNumeric()) {
                    this.reset(this.delegate.getDecimalValue().signum() < 0, 0, 0, 0);
                }
                this._textBuffer.resetWithShared(this.delegate.getTextCharacters(), this.delegate.getTextOffset(), this.delegate.getTextLength());
                this._currToken = token;
                return token;
            }
        }
        if (this.currentToken >= this.tokens.size()) {
            var nextToken = this.delegate.nextToken();
            var data = this.data(nextToken, this.delegate);
            this.currentToken++;
            this.tokens.add(data);
            this._currToken = nextToken;
            if (nextToken == JsonToken.FIELD_NAME) {
                this._parsingContext.setCurrentName(new String(data.data()));
            }
            this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
            this._currToken = nextToken;
            return nextToken;
        }
        this.currentToken++;
        var data = this.tokens.get(this.currentToken - 1);
        var nextToken = data.token();
        this._currToken = nextToken;
        if (nextToken == JsonToken.FIELD_NAME) {
            this._parsingContext.setCurrentName(new String(data.data()));
        }
        this._textBuffer.resetWithShared(data.data(), 0, data.data().length);
        this._currToken = nextToken;
        return nextToken;
    }

    @Nullable
    public JsonSegment currentData() {
        var number = Math.abs(this.currentToken);
        if (number > this.tokens.size()) {
            return null;
        }
        return this.tokens.get(number - 1);
    }

    @Override
    public String getText() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getText();
        }
        return new String(currentData.data());
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getTextCharacters();
        }
        return currentData.data();
    }

    @Override
    public int getTextLength() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getTextLength();
        }
        return currentData.data().length;
    }

    @Override
    public int getTextOffset() throws IOException {
        var currentData = this.currentData();
        if (currentData == null) {
            return delegate.getTextOffset();
        }
        return 0;
    }

    @Override
    public Number getNumberValue() throws IOException {
        return super.getNumberValue();
    }

    @Override
    public Number getNumberValueExact() throws IOException {
        return super.getNumberValueExact();
    }

    @Override
    public NumberType getNumberType() throws IOException {
        return super.getNumberType();
    }

    @Override
    public int getIntValue() throws IOException {
        return super.getIntValue();
    }

    @Override
    public long getLongValue() throws IOException {
        return super.getLongValue();
    }

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        return super.getBigIntegerValue();
    }

    @Override
    public float getFloatValue() throws IOException {
        return super.getFloatValue();
    }

    @Override
    public double getDoubleValue() throws IOException {
        return super.getDoubleValue();
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        return super.getDecimalValue();
    }
}
