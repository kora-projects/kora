package ru.tinkoff.kora.aws.s3.impl.xml;


import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;

//<Delete xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
//   <Object>
//      <ETag>string</ETag>
//      <Key>string</Key>
//      <LastModifiedTime>timestamp</LastModifiedTime>
//      <Size>long</Size>
//      <VersionId>string</VersionId>
//   </Object>
//   ...
//   <Quiet>boolean</Quiet>
//</Delete>
public record DeleteObjectsRequest(List<S3Object> objects) {
    public record S3Object(String key) {
        public void writeXml(XMLStreamWriter xml) throws XMLStreamException {
            xml.writeStartElement("Object");
            xml.writeStartElement("Key");
            xml.writeCharacters(key);
            xml.writeEndElement();
            xml.writeEndElement();
        }
    }

    public static byte[] toXml(Iterable<S3Object> objects) {
        try (var baos = new ByteArrayOutputStream()) {
            toXml(objects, baos);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    public static void toXml(Iterable<S3Object> objects, OutputStream os) throws XMLStreamException {
        var xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(os, "UTF-8");
        try {
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("Delete");
            xml.writeAttribute("xmlns", "http://s3.amazonaws.com/doc/2006-03-01/");
            for (var object : objects) {
                object.writeXml(xml);
            }
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
        } finally {
            xml.close();
        }
    }
}
