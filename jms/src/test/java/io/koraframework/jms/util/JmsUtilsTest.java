package io.koraframework.jms.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.TextMessage;
import org.junit.jupiter.api.Test;

class JmsUtilsTest {

    @Test
    void nestedBodyLimitsAreRestoredAfterSuccessAndFailure() throws Exception {
        var message = mock(TextMessage.class);
        when(message.getText()).thenReturn("abc");
        var seed = ScopedValue.<Boolean>newInstance();
        JmsUtils.withBodySizeLimit(ScopedValue.where(seed, true), 2).run(() -> {
            assertThatThrownBy(() -> JmsUtils.text(message)).isInstanceOf(JMSException.class);
            JmsUtils.withBodySizeLimit(ScopedValue.where(seed, true), 3).run(() -> {
                assertThatCode(() -> JmsUtils.text(message)).doesNotThrowAnyException();
            });
            assertThatThrownBy(() -> JmsUtils.bytes(message)).isInstanceOf(JMSException.class);
            assertThatThrownBy(() -> JmsUtils.withBodySizeLimit(ScopedValue.where(seed, true), 0).call(() -> JmsUtils.text(message)))
                .isInstanceOf(JMSException.class);
            assertThatThrownBy(() -> JmsUtils.text(message)).isInstanceOf(JMSException.class);
        });
        assertThat(JmsUtils.text(message)).isEqualTo("abc");
        assertThatThrownBy(() -> JmsUtils.withBodySizeLimit(ScopedValue.where(seed, true), -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textUsesUtf8ByteLimitAndHandlesNull() throws Exception {
        var message = mock(TextMessage.class);
        assertThat(JmsUtils.text(message)).isNull();
        assertThat(JmsUtils.bytes(message)).isEmpty();
        when(message.getText()).thenReturn("Привет😀");
        var encoded = "Привет😀".getBytes(StandardCharsets.UTF_8);
        assertThat(JmsUtils.bytes(message, encoded.length)).isEqualTo(encoded);
        assertThat(JmsUtils.text(message, encoded.length)).isEqualTo("Привет😀");
        assertThatThrownBy(() -> JmsUtils.bytes(message, encoded.length - 1)).isInstanceOf(JMSException.class);
        when(message.getText()).thenReturn("\uD800");
        assertThat(JmsUtils.bytes(message, 1)).isEqualTo(new byte[] {
                '?'
        });
    }

    @Test
    void bytesAreReadAndReset() throws Exception {
        var message = mock(BytesMessage.class);
        when(message.getBodyLength()).thenReturn(3L);
        when(message.readBytes(any(byte[].class))).thenAnswer(invocation -> {
            System.arraycopy(new byte[] {
                    1, 2, 3
            }, 0, invocation.getArgument(0), 0, 3);
            return 3;
        });
        assertThat(JmsUtils.bytes(message, 3)).containsExactly(1, 2, 3);
        verify(message, times(2)).reset();
    }

    @Test
    void rejectsOversizedBytesBeforeReadingOrAllocation() throws Exception {
        var message = mock(BytesMessage.class);
        when(message.getBodyLength()).thenReturn(2_147_483_648L);
        assertThatThrownBy(() -> JmsUtils.bytes(message)).isInstanceOf(JMSException.class).hasMessageContaining("exceeds limit");
        verify(message, never()).readBytes(any(byte[].class));
        verify(message, times(2)).reset();
    }

    @Test
    void readAndResetFailuresPreserveOriginalError() throws Exception {
        var message = mock(BytesMessage.class);
        var readFailure = new JMSException("read failure");
        var resetFailure = new JMSException("reset failure");
        when(message.getBodyLength()).thenReturn(1L);
        when(message.readBytes(any(byte[].class))).thenThrow(readFailure);
        doNothing().doThrow(resetFailure).when(message).reset();
        assertThatThrownBy(() -> JmsUtils.bytes(message)).isSameAs(readFailure).hasSuppressedException(resetFailure);
    }

    @Test
    void rejectsIncorrectProviderLengthAndSupportsEmptyBody() throws Exception {
        var message = mock(BytesMessage.class);
        assertThat(JmsUtils.bytes(message, 0)).isEmpty();
        when(message.getBodyLength()).thenReturn(2L);
        when(message.readBytes(any(byte[].class))).thenReturn(1);
        assertThatThrownBy(() -> JmsUtils.bytes(message, 2)).isInstanceOf(JMSException.class).hasMessageContaining("does not match");
    }

    @Test
    void appendHeadersSupportsJmsTypesNullAndReplyTo() throws Exception {
        var message = mock(Message.class);
        var destination = mock(Destination.class);
        var values = new HashMap<String, Object>();
        values.put("boolean", true);
        values.put("byte", (byte) 1);
        values.put("short", (short) 2);
        values.put("int", 3);
        values.put("long", 4L);
        values.put("float", 5F);
        values.put("double", 6D);
        values.put("string", "text");
        values.put("null", null);
        JmsUtils.appendHeaders(message, values);
        for (var entry : values.entrySet()) {
            verify(message).setObjectProperty(entry.getKey(), entry.getValue());
        }
        JmsUtils.appendHeaders(message, Map.of(JmsUtils.REPLY_TO, destination));
        verify(message).setJMSReplyTo(destination);
        assertThatThrownBy(() -> JmsUtils.appendHeaders(message, Map.of("invalid", new Object())))
            .isInstanceOf(MessageFormatException.class)
            .hasMessageContaining("invalid");
        assertThatThrownBy(() -> JmsUtils.appendHeaders(message, Map.of(JmsUtils.REPLY_TO, "invalid")))
            .isInstanceOf(MessageFormatException.class);
    }
}
