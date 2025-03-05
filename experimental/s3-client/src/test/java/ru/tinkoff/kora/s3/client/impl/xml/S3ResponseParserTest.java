package ru.tinkoff.kora.s3.client.impl.xml;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ResponseParserTest {
    @Test
    void testS3Parser() throws Exception {
        var xml = "<Error><Code>AccessDenied</Code><Message>Access Denied.</Message><Key>test</Key><BucketName>test</BucketName><Resource>/test/test</Resource><RequestId>18219C0F1714109C</RequestId><HostId>dd9025bab4ad464b049177c95eb6ebf374d3b3fd1af9251148b658df7ac2e3e8</HostId></Error>";
        var handler = new S3ErrorSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);

        var dto = handler.toResult();

        assertThat(dto.code()).isEqualTo("AccessDenied");
        assertThat(dto.message()).isEqualTo("Access Denied.");
    }
}
