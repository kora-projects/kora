package ru.tinkoff.kora.bpmn.camunda8.worker;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.bpmn.camunda8.worker.util.ClasspathResourceUtils;
import ru.tinkoff.kora.bpmn.camunda8.worker.util.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClasspathResourceUtilsTests {

    @Test
    void fileByName() {
        List<Resource> resources = ClasspathResourceUtils.findResources("camunda-license.txt");
        assertEquals(1, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByRegex() {
        List<Resource> resources = ClasspathResourceUtils.findResources(".*\\.txt");
        assertEquals(1, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void filesFromDirectory() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm");
        assertEquals(4, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByNameFromDirectory() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm/ProcessEmpty.bpmn");
        assertEquals(1, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByRegexFromDirectory() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm/.*\\.bpmn");
        assertEquals(2, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }
}
