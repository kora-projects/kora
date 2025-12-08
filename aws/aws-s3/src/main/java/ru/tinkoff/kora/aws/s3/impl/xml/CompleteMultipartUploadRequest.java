package ru.tinkoff.kora.aws.s3.impl.xml;

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
    public record Part(
        @Nullable
        String checksumCRC32,
        @Nullable
        String checksumCRC32C,
        @Nullable
        String checksumCRC64NVME,
        @Nullable
        String checksumSHA1,
        @Nullable
        String checksumSHA256,
        String etag,
        int partNumber) {}

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
                if  (part.checksumCRC32() != null) {
                    xml.writeStartElement("ChecksumCRC32");
                    xml.writeCharacters(part.checksumCRC32());
                    xml.writeEndElement();
                }
                if (part.checksumCRC32C() != null) {
                    xml.writeStartElement("ChecksumCRC32C");
                    xml.writeCharacters(part.checksumCRC32C());
                    xml.writeEndElement();
                }
                if (part.checksumCRC64NVME() != null) {
                    xml.writeStartElement("ChecksumCRC64NVME");
                    xml.writeCharacters(part.checksumCRC64NVME());
                    xml.writeEndElement();
                }
                if (part.checksumSHA1() != null) {
                    xml.writeStartElement("ChecksumSHA1");
                    xml.writeCharacters(part.checksumSHA1());
                    xml.writeEndElement();
                }
                if (part.checksumSHA256() != null) {
                    xml.writeStartElement("ChecksumSHA256");
                    xml.writeCharacters(part.checksumSHA256());
                    xml.writeEndElement();
                }
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
