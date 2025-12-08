package ru.tinkoff.kora.aws.s3.impl.xml;


import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;

//<?xml version="1.0" encoding="UTF-8"?>
//<CompleteMultipartUploadResult>
//   <Location>string</Location>
//   <Bucket>string</Bucket>
//   <Key>string</Key>
//   <ETag>string</ETag>
//   <ChecksumCRC32>string</ChecksumCRC32>
//   <ChecksumCRC32C>string</ChecksumCRC32C>
//   <ChecksumCRC64NVME>string</ChecksumCRC64NVME>
//   <ChecksumSHA1>string</ChecksumSHA1>
//   <ChecksumSHA256>string</ChecksumSHA256>
//   <ChecksumType>string</ChecksumType>
//</CompleteMultipartUploadResult>
public record CompleteMultipartUploadResult(String location, String bucket, String key, String etag) {
    public static CompleteMultipartUploadResult fromXml(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        var handler = new CompleteMultipartUploadResultSaxHandler();
        SAXParserFactory.newDefaultInstance()
            .newSAXParser()
            .parse(is, handler);

        return handler.toResult();
    }
}
