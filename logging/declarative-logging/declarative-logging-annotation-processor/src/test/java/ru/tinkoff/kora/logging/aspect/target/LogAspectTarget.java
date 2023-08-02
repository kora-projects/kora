package ru.tinkoff.kora.logging.aspect.target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.logging.common.annotation.Log;

import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.TRACE;


public class LogAspectTarget {

    public static final String TEST_RESULT_VALUE = "testResult";

    private final Logger emptyMethodLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".emptyMethod");
    private final Logger methodWithArgsInfoLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithArgsInfo");
    private final Logger methodWithArgsDebugLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithArgsDebug");
    private final Logger methodWithDtoInArgsLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithDtoInArgs");
    private final Logger methodWithReturnLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturn");
    private final Logger methodWithReturnAndArgsOnInfoLevelLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturnAndArgsOnInfoLevel");
    private final Logger methodWithReturnAndArgsSeparatelyOnInfoLevelLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".methodWithReturnAndArgsSeparatelyOnInfoLevel");
    private final Logger suspendEmptyMethodLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".suspendEmptyMethod");
    private final Logger suspendMethodWithArgsInfoLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".suspendMethodWithArgsInfo");
    private final Logger suspendMethodWithArgsDebugLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".suspendMethodWithArgsDebug");
    private final Logger suspendMethodWithDtoInArgsLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".suspendMethodWithDtoInArgs");
    private final Logger suspendMethodWithReturnLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".suspendMethodWithReturn");
    private final Logger suspendMethodWithReturnAndArgsOnInfoLevelLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".suspendMethodWithReturnAndArgsOnInfoLevel");
    private final Logger suspendMethodWithReturnAndArgsSeparatelyOnInfoLevelLogger = LoggerFactory.getLogger(LogAspectTarget.class.getCanonicalName() + ".suspendMethodWithReturnAndArgsSeparatelyOnInfoLevel");

    public record SimpleDto(String name, int age, boolean isMale) {
        @Override
        public String toString() {
            return "SimpleDto{" +
                "name: '" + name +
                "; age: " + age +
                "; isMale: " + isMale +
                '}';
        }
    }

    @Log
    public void emptyMethod() {
        emptyMethodLogger.info("EmptyMethod called");
    }

    @Log
    public void methodWithArgsDebug(String strParam, int numParam) {
        methodWithArgsDebugLogger.info("MethodWithArgsDebug called");
    }

    @Log(INFO)
    public void methodWithArgsInfo(String strParam, @Log(TRACE) int numParam) {
        methodWithArgsInfoLogger.info("MethodWithArgsInfo called");
    }

    @Log
    public String methodWithReturn() {
        methodWithReturnLogger.info("MethodWithReturn called");
        return TEST_RESULT_VALUE;
    }

    @Log
    public String methodWithReturnAndArgs(String strParam, int numParam) {
        return TEST_RESULT_VALUE;
    }

    @Log.in
    @Log.out
    public String methodWithReturnAndOffArgs(@Log.off String strParam, int numParam) {
        return TEST_RESULT_VALUE;
    }

    @Log.in
    public String methodWithReturnAndOnlyLogArgs(String strParam, int numParam) {
        return TEST_RESULT_VALUE;
    }

    @Log.out
    public String methodWithOnlyLogReturnAndArgs(String strParam, int numParam) {
        return TEST_RESULT_VALUE;
    }

    @Log(INFO)
    public String methodWithReturnAndArgsOnInfoLevel(String strParam, int numParam) {
        methodWithReturnAndArgsOnInfoLevelLogger.info("MethodWithReturnAndArgsOnInfoLevel called");
        return TEST_RESULT_VALUE;
    }

    @Log.in(INFO)
    @Log.out(INFO)
    public String methodWithReturnAndArgsSeparatelyOnInfoLevel(String strParam, int numParam) {
        methodWithReturnAndArgsSeparatelyOnInfoLevelLogger.info("MethodWithReturnAndArgsSeparatelyOnInfoLevel called");
        return TEST_RESULT_VALUE;
    }

    @Log.out
    public SimpleDto methodWithReturningDto() {
        return new SimpleDto("Ivan", 20, true);
    }

    @Log.in
    public void methodWithDtoInArgs(SimpleDto simpleDto) {
        methodWithDtoInArgsLogger.info("methodWithDtoInArgs called");
    }

    @Log.out
    public Mono<SimpleDto> methodWithReturningMonoWithDtoLogOut() {
        return Mono.just(new SimpleDto("Ivan", 20, true));
    }

    @Log.out(INFO)
    public Mono<SimpleDto> methodWithReturningMonoWithDtoLogInfo() {
        return Mono.just(new SimpleDto("Ivan", 20, true));
    }


}
