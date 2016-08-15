package org.github.alien11689.karaf.deployer.wsdl;

import lombok.extern.slf4j.Slf4j;
import org.apache.felix.fileinstall.ArtifactUrlTransformer;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.net.URL;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

@Slf4j
public class WsdlDeploymentListener implements ArtifactUrlTransformer {
    public URL transform(URL artifact) throws Exception {
        try {
            return new URL("wsdl", null, artifact.toString());
        } catch (Exception e) {
            log.error("Unable to build wsdl bundle", e);
            return null;
        }
    }

    public boolean canHandle(File artifact) {
        try {
            if (artifact.isFile() && artifact.getName().endsWith(".wsdl")) {
                Document doc = parse(artifact);
                String name = doc.getDocumentElement().getLocalName();
                String uri  = doc.getDocumentElement().getNamespaceURI();
                if ("definitions".equals(name) && "http://schemas.xmlsoap.org/wsdl/".equals(uri)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Unable to parse deployed file " + artifact.getAbsolutePath(), e);
        }
        return false;
    }

    protected Document parse(File artifact) throws Exception {
        return org.apache.karaf.util.XmlUtils.parse(artifact, new ErrorHandler() {
            public void warning(SAXParseException exception) throws SAXException {
            }
            public void error(SAXParseException exception) throws SAXException {
            }
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
    }
}
