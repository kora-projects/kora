package ru.tinkoff.kora.bpmn.camunda7.rest;

import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.http.server.common.HttpServer;

public interface CamundaRestHttpServer extends HttpServer, ReadinessProbe {}
