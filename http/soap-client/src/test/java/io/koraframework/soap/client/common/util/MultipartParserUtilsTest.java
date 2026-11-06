package io.koraframework.soap.client.common.util;

import io.koraframework.soap.client.common.jakarta.JakartaXopAttachmentUnmarshaller;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class MultipartParserUtilsTest {

    private static final String BOUNDARY = "uuid:503a0c8a-82a4-4c6d-843e-5c3c1389048c";
    private static final String XML = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><r/></soap:Body></soap:Envelope>";
    private static final String ATTACHMENT = "some-binary-data";

    private static byte[] body() {
        return ("--" + BOUNDARY + "\r\n"
            + "Content-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\"\r\n"
            + "Content-Transfer-Encoding: binary\r\n"
            + "Content-ID: <root.message@cxf.apache.org>\r\n"
            + "\r\n"
            + XML + "\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Transfer-Encoding: binary\r\n"
            + "Content-ID: <att-1>\r\n"
            + "\r\n"
            + ATTACHMENT + "\r\n"
            + "--" + BOUNDARY + "--\r\n").getBytes(UTF_8);
    }

    @Test
    void partContentArrayEqualsContentStream() throws IOException {
        var parts = MultipartParserUtils.parse(body(), BOUNDARY);

        var xml = parts.get("<root.message@cxf.apache.org>");
        assertThat(xml.getContentStream().readAllBytes()).isEqualTo(XML.getBytes(UTF_8));
        assertThat(xml.getContentArray()).isEqualTo(XML.getBytes(UTF_8));

        var attachment = parts.get("<att-1>");
        assertThat(attachment.getContentStream().readAllBytes()).isEqualTo(ATTACHMENT.getBytes(UTF_8));
        assertThat(attachment.getContentArray()).isEqualTo(ATTACHMENT.getBytes(UTF_8));
    }

    @Test
    void xopAttachmentAsByteArray() {
        var parts = MultipartParserUtils.parse(body(), BOUNDARY);
        var unmarshaller = new JakartaXopAttachmentUnmarshaller(parts);

        assertThat(unmarshaller.getAttachmentAsByteArray("cid:att-1")).isEqualTo(ATTACHMENT.getBytes(UTF_8));
    }
}
