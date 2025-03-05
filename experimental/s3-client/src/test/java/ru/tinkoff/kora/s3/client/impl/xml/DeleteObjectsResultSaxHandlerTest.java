package ru.tinkoff.kora.s3.client.impl.xml;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteObjectsResultSaxHandlerTest {
    @Test
    void test() throws Exception {
        var xml = """
            <DeleteResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                <Deleted>
                    <Key>b187dd1e-1fe8-4ccd-9920-63e1770a6dce</Key>
                </Deleted>
                <Deleted>
                    <Key>86ed0727-c3a0-4f70-9bcb-80ba30def9bf</Key>
                </Deleted>
                <Deleted>
                    <Key>c8fde66c-7ae9-46f6-91b6-43564ed01a13</Key>
                </Deleted>
            </DeleteResult>
            """;
        var handler = new DeleteObjectsResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);

        var dto = handler.toResult();

        assertThat(dto.deleted()).isEqualTo(List.of(
            new DeleteObjectsResult.Deleted("b187dd1e-1fe8-4ccd-9920-63e1770a6dce"),
            new DeleteObjectsResult.Deleted("86ed0727-c3a0-4f70-9bcb-80ba30def9bf"),
            new DeleteObjectsResult.Deleted("c8fde66c-7ae9-46f6-91b6-43564ed01a13")
        ));
    }
}
