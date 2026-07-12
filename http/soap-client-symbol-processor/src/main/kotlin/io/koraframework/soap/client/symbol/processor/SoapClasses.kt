package io.koraframework.soap.client.symbol.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

interface SoapClasses {
    fun httpClientTypeName() = ClassName("io.koraframework.http.client.common", "HttpClient")
    fun soapEnvelopeObjectFactory() = ClassName("io.koraframework.soap.client.common.envelope", "ObjectFactory")
    fun soapEnvelopeTypeName() = ClassName("io.koraframework.soap.client.common.envelope", "SoapEnvelope")

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

        override fun xmlToolsType() = ClassName("io.koraframework.soap.client.common.jakarta", "JakartaSoapEnvelopeMapper")

        override fun holderType() = ClassName("jakarta.xml.ws", "Holder")

        override fun webFaultType() = ClassName("jakarta.xml.ws", "WebFault")

        override fun webServiceType() = ClassName("jakarta.jws", "WebService")

        override fun soapBindingType() = ClassName("jakarta.jws.soap", "SOAPBinding")

        override fun xmlRootElementClassName() = ClassName("jakarta.xml.bind.annotation", "XmlRootElement")

        override fun xmlAccessorTypeClassName() = ClassName("jakarta.xml.bind.annotation", "XmlAccessorType")

        override fun xmlAccessTypeClassName() = ClassName("jakarta.xml.bind.annotation", "XmlAccessType")

        override fun xmlElementClassName() = ClassName("jakarta.xml.bind.annotation", "XmlElement")
    }

}
