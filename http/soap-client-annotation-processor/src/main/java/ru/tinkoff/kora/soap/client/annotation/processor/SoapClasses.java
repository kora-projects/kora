package ru.tinkoff.kora.soap.client.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public interface SoapClasses {
    default TypeName httpClientTypeName() {
        return ClassName.get("ru.tinkoff.kora.http.client.common", "HttpClient");
    }

    default TypeName soapEnvelopeObjectFactory() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common.envelope", "ObjectFactory");
    }

    default TypeName soapEnvelopeTypeName() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common.envelope", "SoapEnvelope");
    }

    default TypeName soapClientTelemetryFactory() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common.telemetry", "SoapClientTelemetryFactory");
    }

    default TypeName soapServiceConfig() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapServiceConfig");
    }

    default TypeName soapRequestExecutor() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapRequestExecutor");
    }

    default TypeName soapResult() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapResult");
    }

    default TypeName soapResultFailure() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapResult", "Failure");
    }

    default TypeName soapResultSuccess() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapResult", "Success");
    }

    default TypeName soapFaultException() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapFaultException");
    }

    default TypeName soapException() {
        return ClassName.get("ru.tinkoff.kora.soap.client.common", "SoapException");
    }

    TypeName jaxbContextTypeName();

    TypeName jaxbExceptionTypeName();

    TypeMirror xmlSeeAlsoType();

    TypeMirror webMethodType();

    TypeMirror responseWrapperType();

    TypeMirror requestWrapperType();

    TypeMirror webResultType();

    TypeMirror webParamType();

    TypeName xmlToolsType();

    TypeMirror holderTypeErasure();

    TypeMirror webFaultType();

    TypeMirror webServiceType();

    TypeMirror soapBindingType();

    ClassName xmlRootElementClassName();

    ClassName xmlAccessorTypeClassName();

    ClassName xmlAccessTypeClassName();

    ClassName xmlElementClassName();

    class JakartaClasses implements SoapClasses {
        private final Types types;
        private final Elements elements;

        public JakartaClasses(Types types, Elements elements) {
            this.types = types;
            this.elements = elements;
        }

        @Override
        public TypeName jaxbContextTypeName() {
            return ClassName.get("jakarta.xml.bind", "JAXBContext");
        }

        @Override
        public TypeName jaxbExceptionTypeName() {
            return ClassName.get("jakarta.xml.bind", "JAXBException");
        }

        @Override
        public TypeMirror xmlSeeAlsoType() {
            return elements.getTypeElement("jakarta.xml.bind.annotation.XmlSeeAlso").asType();
        }

        @Override
        public TypeMirror webMethodType() {
            return elements.getTypeElement("jakarta.jws.WebMethod").asType();
        }

        @Override
        public TypeMirror responseWrapperType() {
            return elements.getTypeElement("jakarta.xml.ws.ResponseWrapper").asType();
        }

        @Override
        public TypeMirror requestWrapperType() {
            return elements.getTypeElement("jakarta.xml.ws.RequestWrapper").asType();
        }

        @Override
        public TypeMirror webResultType() {
            return elements.getTypeElement("jakarta.jws.WebResult").asType();
        }

        @Override
        public TypeMirror webParamType() {
            return elements.getTypeElement("jakarta.jws.WebParam").asType();
        }

        @Override
        public TypeName xmlToolsType() {
            return ClassName.get("ru.tinkoff.kora.soap.client.common.jakarta", "JakartaXmlTools");
        }

        @Override
        public TypeMirror holderTypeErasure() {
            return types.erasure(elements.getTypeElement("jakarta.xml.ws.Holder").asType());
        }

        @Override
        public TypeMirror webFaultType() {
            return elements.getTypeElement("jakarta.xml.ws.WebFault").asType();
        }

        @Override
        public TypeMirror webServiceType() {
            return elements.getTypeElement("jakarta.jws.WebService").asType();
        }

        @Override
        public TypeMirror soapBindingType() {
            return elements.getTypeElement("jakarta.jws.soap.SOAPBinding").asType();
        }

        @Override
        public ClassName xmlRootElementClassName() {
            return ClassName.get("jakarta.xml.bind.annotation", "XmlRootElement");
        }

        @Override
        public ClassName xmlAccessorTypeClassName() {
            return ClassName.get("jakarta.xml.bind.annotation", "XmlAccessorType");
        }

        @Override
        public ClassName xmlAccessTypeClassName() {
            return ClassName.get("jakarta.xml.bind.annotation", "XmlAccessType");
        }

        @Override
        public ClassName xmlElementClassName() {
            return ClassName.get("jakarta.xml.bind.annotation", "XmlElement");
        }
    }

    class JavaxClasses implements SoapClasses {
        private final Types types;
        private final Elements elements;

        public JavaxClasses(Types types, Elements elements) {
            this.types = types;
            this.elements = elements;
        }

        @Override
        public TypeName jaxbContextTypeName() {
            return ClassName.get("javax.xml.bind", "JAXBContext");
        }

        @Override
        public TypeName jaxbExceptionTypeName() {
            return ClassName.get("javax.xml.bind", "JAXBException");
        }

        @Override
        public TypeMirror xmlSeeAlsoType() {
            return elements.getTypeElement("javax.xml.bind.annotation.XmlSeeAlso").asType();
        }

        @Override
        public TypeMirror webMethodType() {
            return elements.getTypeElement("javax.jws.WebMethod").asType();
        }

        @Override
        public TypeMirror responseWrapperType() {
            return elements.getTypeElement("javax.xml.ws.ResponseWrapper").asType();
        }

        @Override
        public TypeMirror requestWrapperType() {
            return elements.getTypeElement("javax.xml.ws.RequestWrapper").asType();
        }

        @Override
        public TypeMirror webResultType() {
            return elements.getTypeElement("javax.jws.WebResult").asType();
        }

        @Override
        public TypeMirror webParamType() {
            return elements.getTypeElement("javax.jws.WebParam").asType();
        }

        @Override
        public TypeName xmlToolsType() {
            return ClassName.get("ru.tinkoff.kora.soap.client.common.javax", "JavaxXmlTools");
        }

        @Override
        public TypeMirror holderTypeErasure() {
            return types.erasure(elements.getTypeElement("javax.xml.ws.Holder").asType());
        }

        @Override
        public TypeMirror webFaultType() {
            return elements.getTypeElement("javax.xml.ws.WebFault").asType();
        }

        @Override
        public TypeMirror webServiceType() {
            return elements.getTypeElement("javax.jws.WebService").asType();
        }

        @Override
        public TypeMirror soapBindingType() {
            return elements.getTypeElement("javax.jws.soap.SOAPBinding").asType();
        }


        @Override
        public ClassName xmlRootElementClassName() {
            return ClassName.get("javax.xml.bind.annotation", "XmlRootElement");
        }

        @Override
        public ClassName xmlAccessorTypeClassName() {
            return ClassName.get("javax.xml.bind.annotation", "XmlAccessorType");
        }

        @Override
        public ClassName xmlAccessTypeClassName() {
            return ClassName.get("javax.xml.bind.annotation", "XmlAccessType");
        }

        @Override
        public ClassName xmlElementClassName() {
            return ClassName.get("javax.xml.bind.annotation", "XmlElement");
        }
    }
}
