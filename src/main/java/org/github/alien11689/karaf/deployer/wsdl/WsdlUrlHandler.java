package org.github.alien11689.karaf.deployer.wsdl;

import lombok.extern.slf4j.Slf4j;
import org.osgi.service.url.AbstractURLStreamHandlerService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

@Slf4j
public class WsdlUrlHandler extends AbstractURLStreamHandlerService {
    @Override
    public URLConnection openConnection(URL url) throws IOException {
        if (url.getPath() == null || url.getPath().trim().length() == 0) {
            throw new MalformedURLException("Path cannot be null or empty. Syntax: wsdl:filePath.wsdl");
        }

        log.debug("Blueprint xml URL is: [" + url.getPath() + "]");
        return new Connection(url);
    }

    public class Connection extends URLConnection {

        public Connection(URL url) {
            super(url);
        }

        @Override
        public void connect() throws IOException {
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                String path = url.getPath();
                String[] split = path.split("\\$");
                if (split.length == 1) {
                    WsdlTransformer.transform(new URL(split[0]), "", os);
                }else {
                    WsdlTransformer.transform(new URL(split[0]), split[1], os);
                }
                os.close();
                return new ByteArrayInputStream(os.toByteArray());
            } catch (Exception e) {
                log.error("Error opening wsdl xml url", e);
                throw (IOException) new IOException("Error opening wsdl xml url").initCause(e);
            }
        }
    }

}
