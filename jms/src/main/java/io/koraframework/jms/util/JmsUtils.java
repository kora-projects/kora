package io.koraframework.jms.util;

import io.koraframework.jms.JmsListenerContainerConfig;
import org.jspecify.annotations.Nullable;

import javax.jms.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class JmsUtils {

    private JmsUtils() {}

    public static final String REPLY_TO = "JMSReplyTo";
    private static final ScopedValue<Integer> MAX_BODY_SIZE = ScopedValue.newInstance();

    /**
     * Binds the listener's conversion limit to its delivery scope without leaking it to other
     * listeners.
     */
    public static ScopedValue.Carrier withBodySizeLimit(ScopedValue.Carrier scope, int maxBodySize) {
        checkLimit(maxBodySize);
        return scope.where(MAX_BODY_SIZE, maxBodySize);
    }

    private static int maxBodySize() {
        return MAX_BODY_SIZE.orElse((int) JmsListenerContainerConfig.DEFAULT_MAX_BODY_SIZE.toBytes());
    }

    @Nullable
    public static String text(Message message) throws JMSException {
        return text(message, maxBodySize());
    }

    /**
     * Converts text/bytes bodies with a UTF-8 byte limit; a null text body remains null.
     */
    @Nullable
    public static String text(Message message, int maxBodySize) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            var text = textMessage.getText();
            if (text != null) {
                checkTextSize(text, maxBodySize);
            } else {
                checkLimit(maxBodySize);
            }
            return text;
        }
        if (message instanceof BytesMessage bytesMessage) {
            return new String(readBytes(bytesMessage, maxBodySize), StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Can't parse to string message of class " + message.getClass().getSimpleName());
    }

    /**
     * Converts text/bytes bodies; a null text body becomes an empty byte array.
     */
    public static byte[] bytes(Message message) throws JMSException {
        return bytes(message, maxBodySize());
    }

    public static byte[] bytes(Message message, int maxBodySize) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            var text = textMessage.getText();
            checkLimit(maxBodySize);
            if (text == null) {
                return new byte[0];
            }
            checkTextSize(text, maxBodySize);
            return text.getBytes(StandardCharsets.UTF_8);
        }
        if (message instanceof BytesMessage bytesMessage) {
            return readBytes(bytesMessage, maxBodySize);
        }
        throw new IllegalArgumentException("Can't parse to bytes message of class " + message.getClass().getSimpleName());
    }

    private static void checkLimit(int maxBodySize) {
        if (maxBodySize < 0) {
            throw new IllegalArgumentException("maxBodySize must not be negative");
        }
    }

    private static void checkTextSize(String text, int maxBodySize) throws JMSException {
        checkLimit(maxBodySize);
        // Count UTF-8 bytes before allocating the encoded body, including replacement of unpaired
        // surrogates.
        long size = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                size++;
            } else if (c < 0x800) {
                size += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                size += 4;
                i++;
            } else {
                size += Character.isSurrogate(c) ? 1 : 3;
            }
            if (size > maxBodySize) {
                throw new JMSException("JMS text body exceeds limit of " + maxBodySize + " bytes");
            }
        }
    }

    private static byte[] readBytes(BytesMessage message, int maxBodySize) throws JMSException {
        checkLimit(maxBodySize);
        message.reset();
        Throwable failure = null;
        try {
            long length = message.getBodyLength();
            if (length < 0 || length > maxBodySize) {
                throw new JMSException("JMS bytes body size " + length + " exceeds limit of " + maxBodySize + " bytes");
            }
            var bytes = new byte[(int) length];
            if (length > 0 && message.readBytes(bytes) != length) {
                throw new JMSException("JMS bytes body length does not match getBodyLength()");
            }
            return bytes;
        } catch (JMSException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            try {
                message.reset();
            } catch (JMSException | RuntimeException e) {
                if (failure == null) {
                    throw e;
                }
                if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
    }

    public static Map<String, String> dumpHeaders(Message message) throws JMSException {
        var result = new HashMap<String, String>();
        var propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements()) {
            var name = (String) propertyNames.nextElement();
            var value = message.getObjectProperty(name);
            result.put(name, Objects.toString(value));
        }
        return result;
    }

    public static void appendHeaders(Message jmsMessage, Map<String, @Nullable Object> headers) throws JMSException {
        for (var header : headers.entrySet()) {
            var name = header.getKey();
            var value = header.getValue();
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("JMS property name must not be empty");
            }
            if (name.equals(REPLY_TO)) {
                if (value != null && !(value instanceof Destination)) {
                    throw new MessageFormatException("JMSReplyTo must be a Destination");
                }
                jmsMessage.setJMSReplyTo((Destination) value);
            } else if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float
                || value instanceof Double) {
                jmsMessage.setObjectProperty(name, value);
            } else {
                throw new MessageFormatException("Invalid property type for '" + name + "': " + value.getClass().getName());
            }
        }
    }
}
