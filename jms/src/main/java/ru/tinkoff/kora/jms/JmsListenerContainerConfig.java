package ru.tinkoff.kora.jms;


public record JmsListenerContainerConfig(String queueName, int threads) {}
