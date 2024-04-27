package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import ru.tinkoff.kora.bpmn.camunda8.worker.annotation.JobVariable;
import ru.tinkoff.kora.bpmn.camunda8.worker.annotation.JobVariables;
import ru.tinkoff.kora.bpmn.camunda8.worker.annotation.JobWorker;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;

import java.util.List;

public class SampleJobHandler {

    record SomeVariables(String name, String id) {}

    record SomeResponse(String name, String id) {}

    @JobWorker("my-worker")
    void process2() {
        // do something
    }

    @JobWorker("my-worker")
    void process3(@JobVariables SomeVariables variables) {
        // do something
    }

    @JobWorker("my-worker")
    SomeResponse process4(@JobVariable String var1) {
        return new SomeResponse("Bob", "1");
    }

    @JobWorker("my-worker")
    void process5(@JobVariable("var12345") String var1) {
        // do something
    }

    @JobWorker("my-worker")
    void process6(JobContext context) {
        // do something
    }

    static final class Job2 implements KoraJobWorker {

        private final SampleJobHandler handler;

        Job2(SampleJobHandler handler) {
            this.handler = handler;
        }

        @Override
        public String type() {
            return "job2";
        }

        @Override
        public FinalCommandStep<?> handle(JobClient client, ActivatedJob job) {
            try {
                handler.process2();
                return client.newCompleteCommand(job);
            } catch (JobWorkerException e) {
                throw e;
            } catch (Exception e) {
                throw new JobWorkerException("500", e);
            }
        }
    }

    static final class Job3 implements KoraJobWorker {

        private final SampleJobHandler handler;
        private final JsonReader<SomeVariables> variablesJsonReader;

        Job3(SampleJobHandler handler, JsonReader<SomeVariables> variablesJsonReader) {
            this.handler = handler;
            this.variablesJsonReader = variablesJsonReader;
        }

        @Override
        public String type() {
            return "job3";
        }

        @Override
        public List<String> fetchVariables() {
            return List.of("var1");
        }

        @Override
        public FinalCommandStep<?> handle(JobClient client, ActivatedJob job) {
            try {
                var variables = variablesJsonReader.read(job.getVariables());
                handler.process3(variables);
                return client.newCompleteCommand(job);
            } catch (JobWorkerException e) {
                throw e;
            } catch (Exception e) {
                throw new JobWorkerException("500", e);
            }
        }
    }

    static final class Job4 implements KoraJobWorker {

        private final JsonWriter<SomeResponse> jsonWriter;
        private final SampleJobHandler handler;

        Job4(JsonWriter<SomeResponse> jsonWriter, SampleJobHandler handler) {
            this.jsonWriter = jsonWriter;
            this.handler = handler;
        }

        @Override
        public String type() {
            return "job4";
        }

        @Override
        public FinalCommandStep<?> handle(JobClient client, ActivatedJob job) {
            try {
                SomeResponse response = handler.process4(((String) job.getVariable("var1")));
                return client.newCompleteCommand(job)
                    .variables(jsonWriter.toStringUnchecked(response));
            } catch (JobWorkerException e) {
                throw e;
            } catch (Exception e) {
                throw new JobWorkerException("500", e);
            }
        }
    }

    static final class Job5 implements KoraJobWorker {

        private final SampleJobHandler handler;

        Job5(SampleJobHandler handler) {
            this.handler = handler;
        }

        @Override
        public String type() {
            return "job5";
        }

        @Override
        public FinalCommandStep<?> handle(JobClient client, ActivatedJob job) {
            try {
                handler.process5(((String) job.getVariable("var12345")));
                return client.newCompleteCommand(job);
            } catch (JobWorkerException e) {
                throw e;
            } catch (Exception e) {
                throw new JobWorkerException("500", e);
            }
        }
    }

    static final class Job6 implements KoraJobWorker {

        private final SampleJobHandler handler;

        Job6(SampleJobHandler handler) {
            this.handler = handler;
        }

        @Override
        public String type() {
            return "job6";
        }

        @Override
        public FinalCommandStep<?> handle(JobClient client, ActivatedJob job) {
            try {
                handler.process6(new ActiveJobContext(type(), job));
                return client.newCompleteCommand(job);
            } catch (JobWorkerException e) {
                throw e;
            } catch (Exception e) {
                throw new JobWorkerException("500", e);
            }
        }
    }
}
