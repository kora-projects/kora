package ru.tinkoff.kora.soap.client.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

interface SoapClasses {
    fun httpClientTypeName(): TypeName? {
        return ClassName("ru.tinkoff.kora.http.client.common", "HttpClient")
    }

    fun soapEnvelopeObjectFactory(): TypeName? {
        return ClassName("ru.tinkoff.kora.soap.client.common.envelope", "ObjectFactory")
    }

    fun soapEnvelopeTypeName(): TypeName? {
        return ClassName("ru.tinkoff.kora.soap.client.common.envelope", "SoapEnvelope")
    }

    fun jaxbContextTypeName(): TypeName?
    fun jaxbExceptionTypeName(): TypeName?
    fun xmlSeeAlsoType(): KSType?
    fun webMethodType(): KSType?
    fun responseWrapperType(): KSType?
    fun requestWrapperType(): KSType?
    fun webResultType(): KSType?
    fun webParamType(): KSType?
    fun xmlToolsType(): TypeName?
    fun holderTypeErasure(): KSType?
    fun webFaultType(): KSType?
    fun webServiceType(): KSType?
    fun soapBindingType(): KSType?
    fun xmlRootElementClassName(): ClassName?
    fun xmlAccessorTypeClassName(): ClassName?
    fun xmlAccessTypeClassName(): ClassName?
    fun xmlElementClassName(): ClassName?
    class JakartaClasses(private val resolver: Resolver) : SoapClasses {
        override fun jaxbContextTypeName(): TypeName {
            return ClassName("jakarta.xml.bind", "JAXBContext")
        }

        override fun jaxbExceptionTypeName(): TypeName {
            return ClassName("jakarta.xml.bind", "JAXBException")
        }

        override fun xmlSeeAlsoType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.xml.bind.annotation.XmlSeeAlso")!!.asStarProjectedType()
        }

        override fun webMethodType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.jws.WebMethod")!!.asStarProjectedType()
        }

        override fun responseWrapperType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.xml.ws.ResponseWrapper")!!.asStarProjectedType()
        }

        override fun requestWrapperType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.xml.ws.RequestWrapper")!!.asStarProjectedType()
        }

        override fun webResultType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.jws.WebResult")!!.asStarProjectedType()
        }

        override fun webParamType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.jws.WebParam")!!.asStarProjectedType()
        }

        override fun xmlToolsType(): TypeName {
            return ClassName("ru.tinkoff.kora.soap.client.common.jakarta", "JakartaXmlTools")
        }

        override fun holderTypeErasure(): KSType {
            return resolver.getClassDeclarationByName("jakarta.xml.ws.Holder")!!.asStarProjectedType().starProjection()
        }

        override fun webFaultType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.xml.ws.WebFault")!!.asStarProjectedType()
        }

        override fun webServiceType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.jws.WebService")!!.asStarProjectedType()
        }

        override fun soapBindingType(): KSType {
            return resolver.getClassDeclarationByName("jakarta.jws.soap.SOAPBinding")!!.asStarProjectedType()
        }

        override fun xmlRootElementClassName(): ClassName {
            return ClassName("jakarta.xml.bind.annotation", "XmlRootElement")
        }

        override fun xmlAccessorTypeClassName(): ClassName {
            return ClassName("jakarta.xml.bind.annotation", "XmlAccessorType")
        }

        override fun xmlAccessTypeClassName(): ClassName {
            return ClassName("jakarta.xml.bind.annotation", "XmlAccessType")
        }

        override fun xmlElementClassName(): ClassName {
            return ClassName("jakarta.xml.bind.annotation", "XmlElement")
        }
    }

    class JavaxClasses(private val resolver: Resolver) : SoapClasses {
        override fun jaxbContextTypeName(): TypeName {
            return ClassName("javax.xml.bind", "JAXBContext")
        }

        override fun jaxbExceptionTypeName(): TypeName {
            return ClassName("javax.xml.bind", "JAXBException")
        }

        override fun xmlSeeAlsoType(): KSType {
            return resolver.getClassDeclarationByName("javax.xml.bind.annotation.XmlSeeAlso")!!.asStarProjectedType()
        }

        override fun webMethodType(): KSType {
            return resolver.getClassDeclarationByName("javax.jws.WebMethod")!!.asStarProjectedType()
        }

        override fun responseWrapperType(): KSType {
            return resolver.getClassDeclarationByName("javax.xml.ws.ResponseWrapper")!!.asStarProjectedType()
        }

        override fun requestWrapperType(): KSType {
            return resolver.getClassDeclarationByName("javax.xml.ws.RequestWrapper")!!.asStarProjectedType()
        }

        override fun webResultType(): KSType {
            return resolver.getClassDeclarationByName("javax.jws.WebResult")!!.asStarProjectedType()
        }

        override fun webParamType(): KSType {
            return resolver.getClassDeclarationByName("javax.jws.WebParam")!!.asStarProjectedType()
        }

        override fun xmlToolsType(): TypeName {
            return ClassName("ru.tinkoff.kora.soap.client.common.javax", "JavaxXmlTools")
        }

        override fun holderTypeErasure(): KSType {
            return resolver.getClassDeclarationByName("javax.xml.ws.Holder")!!.asStarProjectedType().starProjection()
        }

        override fun webFaultType(): KSType {
            return resolver.getClassDeclarationByName("javax.xml.ws.WebFault")!!.asStarProjectedType()
        }

        override fun webServiceType(): KSType {
            return resolver.getClassDeclarationByName("javax.jws.WebService")!!.asStarProjectedType()
        }

        override fun soapBindingType(): KSType {
            return resolver.getClassDeclarationByName("javax.jws.soap.SOAPBinding")!!.asStarProjectedType()
        }

        override fun xmlRootElementClassName(): ClassName {
            return ClassName("javax.xml.bind.annotation", "XmlRootElement")
        }

        override fun xmlAccessorTypeClassName(): ClassName {
            return ClassName("javax.xml.bind.annotation", "XmlAccessorType")
        }

        override fun xmlAccessTypeClassName(): ClassName {
            return ClassName("javax.xml.bind.annotation", "XmlAccessType")
        }

        override fun xmlElementClassName(): ClassName {
            return ClassName("javax.xml.bind.annotation", "XmlElement")
        }
    }
}
