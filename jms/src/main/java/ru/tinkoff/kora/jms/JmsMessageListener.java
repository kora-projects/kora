package ru.tinkoff.kora.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

public interface JmsMessageListener {
    void onMessage(Session session, Message message) throws JMSException;
}
