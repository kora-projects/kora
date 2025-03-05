package ru.tinkoff.kora.s3.client.impl.xml;

import jakarta.annotation.Nullable;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;

//<CompleteMultipartUpload xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
//   <Part>
//      <ChecksumCRC32>string</ChecksumCRC32>
//      <ChecksumCRC32C>string</ChecksumCRC32C>
//      <ChecksumCRC64NVME>string</ChecksumCRC64NVME>
//      <ChecksumSHA1>string</ChecksumSHA1>
//      <ChecksumSHA256>string</ChecksumSHA256>
//      <ETag>string</ETag>
//      <PartNumber>integer</PartNumber>
//   </Part>
//   ...
//</CompleteMultipartUpload>
public record CompleteMultipartUploadRequest(List<Part> parts) {
    public record Part(String etag, int partNumber, @Nullable String checksumSha256) {}

    public byte[] toXml() {
        try (var baos = new ByteArrayOutputStream()) {
            writeXml(baos);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    void writeXml(OutputStream os) throws IOException, XMLStreamException {
        var xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(os, "UTF-8");
        try {
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("CompleteMultipartUpload");
            xml.writeAttribute("xmlns", "http://s3.amazonaws.com/doc/2006-03-01/");
            for (var part : this.parts) {
                xml.writeStartElement("Part");
                xml.writeStartElement("ChecksumSHA256");
                xml.writeCharacters(part.checksumSha256());
                xml.writeEndElement();
                xml.writeStartElement("ETag");
                xml.writeCharacters(part.etag());
                xml.writeEndElement();
                xml.writeStartElement("PartNumber");
                xml.writeCharacters(Integer.toString(part.partNumber()));
                xml.writeEndElement();
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
        } finally {
            xml.close();
        }
    }
}
