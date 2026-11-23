package io.koraframework.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * Shared by all container workers and therefore must be thread-safe. The session is confined to the
 * calling worker. Its transaction is owned by the container; listeners must not commit, roll back,
 * close it, or use it asynchronously. Sends through this session participate in the receive
 * transaction; external side effects do not.
 */
@FunctionalInterface
public interface JmsMessageListener {

    void onMessage(Session session, Message message) throws JMSException;
}
