package ru.tinkoff.kora.jms;

import javax.jms.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JmsUtils {

    public static String REPLY_TO = "JMSReplyTo";

    public static String text(Message message) throws JMSException {
        if (message instanceof TextMessage) {
            return ((TextMessage) message).getText();
        }
        if (message instanceof BytesMessage) {
            var bm = (BytesMessage) message;
            bm.reset();
            var bytes = new byte[(int) bm.getBodyLength()];
            bm.readBytes(bytes);
            bm.reset();
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Can't parse to string message of class " + message.getClass().getSimpleName());
    }

    public static byte[] bytes(Message message) throws JMSException {
        if (message instanceof TextMessage) {
            return ((TextMessage) message).getText().getBytes(StandardCharsets.UTF_8);
        }
        if (message instanceof BytesMessage) {
            var bm = (BytesMessage) message;
            bm.reset();
            var bytes = new byte[(int) bm.getBodyLength()];
            bm.readBytes(bytes);
            bm.reset();
            return bytes;
        }
        throw new IllegalArgumentException("Can't parse to bytes message of class " + message.getClass().getSimpleName());
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


    public static void appendHeaders(Message jmsMessage, Map<String, Object> headers) throws JMSException {
        for (var header : headers.entrySet()) {
            if (header.getKey().equals(REPLY_TO)) {
                jmsMessage.setJMSReplyTo((Destination) header.getValue());
            } else if (header.getValue() instanceof String) {
                jmsMessage.setStringProperty(header.getKey(), (String) header.getValue());
            } else if (header.getValue() instanceof Integer) {
                jmsMessage.setIntProperty(header.getKey(), (Integer) header.getValue());
            } else {
                throw new JMSRuntimeException("Invalid property type: " + header.getValue().getClass());
            }
        }
    }
}
