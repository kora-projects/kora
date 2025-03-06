package ru.tinkoff.kora.soap.client.symbol.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

interface SoapClasses {
    fun httpClientTypeName() = ClassName("ru.tinkoff.kora.http.client.common", "HttpClient")
    fun soapEnvelopeObjectFactory() = ClassName("ru.tinkoff.kora.soap.client.common.envelope", "ObjectFactory")
    fun soapEnvelopeTypeName() = ClassName("ru.tinkoff.kora.soap.client.common.envelope", "SoapEnvelope")

    fun jaxbContextTypeName(): TypeName
    fun jaxbExceptionTypeName(): TypeName
    fun jaxbElementTypeName(): TypeName
    fun xmlSeeAlsoType(): ClassName
    fun webMethodType(): ClassName
    fun responseWrapperType(): ClassName
    fun requestWrapperType(): ClassName
    fun webResultType(): ClassName
    fun webParamType(): ClassName
    fun xmlToolsType(): ClassName
    fun holderType(): ClassName
    fun webFaultType(): ClassName
    fun webServiceType(): ClassName
    fun soapBindingType(): ClassName
    fun xmlRootElementClassName(): ClassName
    fun xmlAccessorTypeClassName(): ClassName
    fun xmlAccessTypeClassName(): ClassName
    fun xmlElementClassName(): ClassName

    object JakartaClasses : SoapClasses {
        override fun jaxbContextTypeName() = ClassName("jakarta.xml.bind", "JAXBContext")

        override fun jaxbExceptionTypeName() = ClassName("jakarta.xml.bind", "JAXBException")

        override fun jaxbElementTypeName() = ClassName("jakarta.xml.bind", "JAXBElement")

        override fun xmlSeeAlsoType() = ClassName("jakarta.xml.bind.annotation", "XmlSeeAlso")

        override fun webMethodType() = ClassName("jakarta.jws", "WebMethod")

        override fun responseWrapperType() = ClassName("jakarta.xml.ws", "ResponseWrapper")

        override fun requestWrapperType() = ClassName("jakarta.xml.ws", "RequestWrapper")

        override fun webResultType() = ClassName("jakarta.jws", "WebResult")

        override fun webParamType() = ClassName("jakarta.jws", "WebParam")

        override fun xmlToolsType() = ClassName("ru.tinkoff.kora.soap.client.common.jakarta", "JakartaXmlTools")

        override fun holderType() = ClassName("jakarta.xml.ws", "Holder")

        override fun webFaultType() = ClassName("jakarta.xml.ws", "WebFault")

        override fun webServiceType() = ClassName("jakarta.jws", "WebService")

        override fun soapBindingType() = ClassName("jakarta.jws.soap", "SOAPBinding")

        override fun xmlRootElementClassName() = ClassName("jakarta.xml.bind.annotation", "XmlRootElement")

        override fun xmlAccessorTypeClassName() = ClassName("jakarta.xml.bind.annotation", "XmlAccessorType")

        override fun xmlAccessTypeClassName() = ClassName("jakarta.xml.bind.annotation", "XmlAccessType")

        override fun xmlElementClassName() = ClassName("jakarta.xml.bind.annotation", "XmlElement")
    }

    object JavaxClasses : SoapClasses {
        override fun jaxbContextTypeName() = ClassName("javax.xml.bind", "JAXBContext")

        override fun jaxbExceptionTypeName() = ClassName("javax.xml.bind", "JAXBException")

        override fun jaxbElementTypeName() = ClassName("javax.xml.bind", "JAXBElement")

        override fun xmlSeeAlsoType() = ClassName("javax.xml.bind.annotation", "XmlSeeAlso")

        override fun webMethodType() = ClassName("javax.jws", "WebMethod")

        override fun responseWrapperType() = ClassName("javax.xml.ws", "ResponseWrapper")

        override fun requestWrapperType() = ClassName("javax.xml.ws", "RequestWrapper")

        override fun webResultType() = ClassName("javax.jws", "WebResult")

        override fun webParamType() = ClassName("javax.jws", "WebParam")

        override fun xmlToolsType() = ClassName("ru.tinkoff.kora.soap.client.common.javax", "JavaxXmlTools")

        override fun holderType() = ClassName("javax.xml.ws", "Holder")

        override fun webFaultType() = ClassName("javax.xml.ws", "WebFault")

        override fun webServiceType() = ClassName("javax.jws", "WebService")

        override fun soapBindingType() = ClassName("javax.jws.soap", "SOAPBinding")

        override fun xmlRootElementClassName() = ClassName("javax.xml.bind.annotation", "XmlRootElement")

        override fun xmlAccessorTypeClassName() = ClassName("javax.xml.bind.annotation", "XmlAccessorType")

        override fun xmlAccessTypeClassName() = ClassName("javax.xml.bind.annotation", "XmlAccessType")

        override fun xmlElementClassName() = ClassName("javax.xml.bind.annotation", "XmlElement")
    }
}
