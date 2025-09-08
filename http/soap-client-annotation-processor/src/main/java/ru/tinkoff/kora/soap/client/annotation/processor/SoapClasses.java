package ru.tinkoff.kora.soap.client.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

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

    ClassName jaxbElementTypeName();

    ClassName xmlSeeAlsoType();

    ClassName webMethodType();

    ClassName responseWrapperType();

    ClassName requestWrapperType();

    ClassName webResultType();

    ClassName webParamType();

    TypeName xmlToolsType();

    ClassName holderTypeClassName();

    ClassName webFaultType();

    ClassName webServiceType();

    ClassName soapBindingType();

    ClassName xmlRootElementClassName();

    ClassName xmlAccessorTypeClassName();

    ClassName xmlAccessTypeClassName();

    ClassName xmlElementClassName();

    class JakartaClasses implements SoapClasses {

        @Override
        public TypeName jaxbContextTypeName() {
            return ClassName.get("jakarta.xml.bind", "JAXBContext");
        }

        @Override
        public TypeName jaxbExceptionTypeName() {
            return ClassName.get("jakarta.xml.bind", "JAXBException");
        }

        @Override
        public ClassName jaxbElementTypeName() {return ClassName.get("jakarta.xml.bind", "JAXBElement");}

        @Override
        public ClassName xmlSeeAlsoType() {
            return ClassName.get("jakarta.xml.bind.annotation", "XmlSeeAlso");
        }

        @Override
        public ClassName webMethodType() {
            return ClassName.get("jakarta.jws", "WebMethod");
        }

        @Override
        public ClassName responseWrapperType() {
            return ClassName.get("jakarta.xml.ws", "ResponseWrapper");
        }

        @Override
        public ClassName requestWrapperType() {
            return ClassName.get("jakarta.xml.ws", "RequestWrapper");
        }

        @Override
        public ClassName webResultType() {
            return ClassName.get("jakarta.jws", "WebResult");
        }

        @Override
        public ClassName webParamType() {
            return ClassName.get("jakarta.jws", "WebParam");
        }

        @Override
        public TypeName xmlToolsType() {
            return ClassName.get("ru.tinkoff.kora.soap.client.common.jakarta", "JakartaXmlTools");
        }

        @Override
        public ClassName holderTypeClassName() {return ClassName.get("jakarta.xml.ws", "Holder");}

        @Override
        public ClassName webFaultType() {
            return ClassName.get("jakarta.xml.ws", "WebFault");
        }

        @Override
        public ClassName webServiceType() {
            return ClassName.get("jakarta.jws", "WebService");
        }

        @Override
        public ClassName soapBindingType() {
            return ClassName.get("jakarta.jws.soap", "SOAPBinding");
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

        public JavaxClasses() {}

        @Override
        public TypeName jaxbContextTypeName() {
            return ClassName.get("javax.xml.bind", "JAXBContext");
        }

        @Override
        public TypeName jaxbExceptionTypeName() {
            return ClassName.get("javax.xml.bind", "JAXBException");
        }

        @Override
        public ClassName jaxbElementTypeName() {return ClassName.get("javax.xml.bind", "JAXBElement");}

        @Override
        public ClassName xmlSeeAlsoType() {
            return ClassName.get("javax.xml.bind.annotation", "XmlSeeAlso");
        }

        @Override
        public ClassName webMethodType() {
            return ClassName.get("javax.jws", "WebMethod");
        }

        @Override
        public ClassName responseWrapperType() {
            return ClassName.get("javax.xml.ws", "ResponseWrapper");
        }

        @Override
        public ClassName requestWrapperType() {
            return ClassName.get("javax.xml.ws", "RequestWrapper");
        }

        @Override
        public ClassName webResultType() {
            return ClassName.get("javax.jws", "WebResult");
        }

        @Override
        public ClassName webParamType() {
            return ClassName.get("javax.jws", "WebParam");
        }

        @Override
        public TypeName xmlToolsType() {
            return ClassName.get("ru.tinkoff.kora.soap.client.common.javax", "JavaxXmlTools");
        }

        @Override
        public ClassName holderTypeClassName() {return ClassName.get("javax.xml.ws", "Holder");}

        @Override
        public ClassName webFaultType() {
            return ClassName.get("javax.xml.ws", "WebFault");
        }

        @Override
        public ClassName webServiceType() {
            return ClassName.get("javax.jws", "WebService");
        }

        @Override
        public ClassName soapBindingType() {
            return ClassName.get("javax.jws.soap", "SOAPBinding");
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
