package ru.tinkoff.kora.soap.client.common;

import org.w3c.dom.Element;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.function.Function;

public class SoapEnvelopeProcessors {
    public static Function<SoapEnvelope, SoapEnvelope> wssAuth(String username, String password) {
        return soapEnvelope -> {
            try {
                soapEnvelope.getHeader().getAny().add(buildWssHeader(username, password));
            } catch (ParserConfigurationException e) {
                throw new RuntimeException(e);// never going to happen
            }
            return soapEnvelope;
        };
    }

    private static Element buildWssHeader(String username, String password) throws ParserConfigurationException {
        var wsse = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
        var passwordTextType = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

        var document = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
        var security = document.createElementNS(wsse, "Security");
        security.setPrefix("wsse");
        var usernameToken = document.createElementNS(wsse, "UsernameToken");
        usernameToken.setPrefix("wsse");
        var usernameElement = document.createElementNS(wsse, "Username");
        usernameElement.setPrefix("wsse");
        var passwordElement = document.createElementNS(wsse, "Password");
        passwordElement.setPrefix("wsse");
        usernameElement.appendChild(document.createTextNode(username));
        passwordElement.setAttribute("Type", passwordTextType);
        passwordElement.appendChild(document.createTextNode(password));

        usernameToken.appendChild(usernameElement);
        usernameToken.appendChild(passwordElement);
        security.appendChild(usernameToken);
        document.appendChild(security);
        return document.getDocumentElement();
    }
}
