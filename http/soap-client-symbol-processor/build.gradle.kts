plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    alias(libs.plugins.kora.soap)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.symbolProcessorCommon)

    cxf(libs.cxf.tools.wsdlto.core)
    cxf(libs.cxf.tools.wsdlto.frontend.jaxws)
    cxf(libs.cxf.tools.wsdlto.databinding.jaxb)
    cxf(libs.jakarta.xml.bind.api)
    cxf(libs.jakarta.xml.ws.api)

    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(libs.jakarta.xml.bind.api)
    testImplementation(libs.glassfish.jaxb.jakarta)
    testImplementation(libs.jakarta.xml.ws.api)
    testImplementation(libs.cxf.rt.bindings.soap)
    testImplementation(libs.cxf.rt.transports.http.jetty)
    testImplementation(libs.cxf.rt.frontend.jaxws)
    testImplementation(projects.http.soapClient)
    testImplementation(projects.http.httpClientJdk)
}

koraSoap {
    wsdl2Java("src/test/wsdl/simple-service.wsdl")
    wsdl2Java("src/test/wsdl/service-with-multipart-response.wsdl")
    wsdl2Java("src/test/wsdl/service-with-rpc.wsdl")
}
