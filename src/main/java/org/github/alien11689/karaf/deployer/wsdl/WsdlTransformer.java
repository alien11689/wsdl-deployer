package org.github.alien11689.karaf.deployer.wsdl;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.karaf.util.DeployerUtils;
import org.apache.karaf.util.XmlUtils;
import org.osgi.framework.Constants;
import org.w3c.dom.Document;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

@Slf4j
public class WsdlTransformer {
    public static void transform(URL url, String options, OutputStream os) throws Exception {
        log.info("Url = " + url.toString());
        log.info("Path = " + url.getPath());
        String path = url.getPath();
        String name = path;
        log.info("Name = " + name);
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        log.info("Name with shift = " + name);

        String[] str = DeployerUtils.extractNameVersionType(name);
        log.info("Str size = " + str.length);
        for (String s : str) {
            log.info("Str... = ", s);

        }
        // Create manifest
        Manifest m = new Manifest();
        m.getMainAttributes().putValue("Manifest-Version", "2");
        m.getMainAttributes().putValue(Constants.BUNDLE_MANIFESTVERSION, "2");
        m.getMainAttributes().putValue(Constants.BUNDLE_SYMBOLICNAME, name);
        m.getMainAttributes().putValue(Constants.BUNDLE_VERSION, str[1]);
        m.getMainAttributes().putValue(Constants.IMPORT_PACKAGE, "javax.jws,javax.jws.soap,javax.xml.bind.annotation,javax.xml.namespace,javax.xml.ws");


        UUID uuid = UUID.randomUUID();
        String baseDir = "/tmp/" + uuid.toString();
        File tempRoot = new File(baseDir);
        tempRoot.mkdir();
        File target = new File(tempRoot, "target");
        target.mkdir();
        File src = new File(tempRoot, "src");
        src.mkdir();

        File jaxbBinding = createJaxbBinding(tempRoot);
        File jaxwsBinding = createJaxwsBinding(tempRoot, options);

        File serviceWsdl = createWsdlFile(url, tempRoot);

        Process process = new ProcessBuilder().command(
            "/usr/bin/wsimport",
            "-verbose",
            "-J-Djavax.xml.accessExternalSchema=file",
            "-d", target.getName(),
            "-keep",
            "-b", jaxbBinding.getName(),
            "-b", jaxwsBinding.getName(),
            "-s", "src",
            serviceWsdl.getName()
        ).directory(tempRoot).start();
        InputStream is = process.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
            log.info(line);
        }
        log.info("WSImport finished!");

        Set<File> dirs = new HashSet<>();
        Stack<File> toProcess = new Stack<>();
        toProcess.add(target);
        while (!toProcess.isEmpty()) {
            File file = toProcess.pop();
            if (file.isFile()) {
                dirs.add(file.getParentFile());
            } else {
                toProcess.addAll(Arrays.asList(file.listFiles()));
            }
        }
        String packages = dirs.stream().map(File::getAbsolutePath).map((f) -> f.substring(target.getAbsolutePath().length() + 1).replaceAll("/", ".") + ";version=\"" + str[1] + "\"").collect(Collectors.joining(","));

        m.getMainAttributes().putValue(Constants.EXPORT_PACKAGE, packages);

        createJar(target);

        rewriteJar(os, m, new File(target, "service.jar"));
    }

    private static File createWsdlFile(URL url, File tempRoot) throws IOException {
        File serviceWsdl = new File(tempRoot, "service.wsdl");
        serviceWsdl.createNewFile();

        try (FileWriter fw = new FileWriter(serviceWsdl);
             BufferedWriter bw = new BufferedWriter(fw);
             InputStream in = url.openStream();
             InputStreamReader isr = new InputStreamReader(in);
             BufferedReader br = new BufferedReader(isr)
        ) {
            String line;
            while ((line = br.readLine()) != null) {
                bw.append(line);
            }
        }
        return serviceWsdl;
    }

    private static File createJaxwsBinding(File tempRoot, String options) throws IOException {
        File jaxwsBinding = new File(tempRoot, "Jaxws-binding.xml");
        jaxwsBinding.createNewFile();
        String packageName = null;
        String s1 = null;
        String t1 = null;
        if (!options.isEmpty()) {
            for (String option : options.split("&")) {
                String[] keyVal = option.split("=");
                switch (keyVal[0]) {
                    case "package":
                        packageName = keyVal[1];
                        break;
                    case "s1":
                        s1 = keyVal[1];
                        break;
                    case "t1":
                        t1 = keyVal[1];
                        break;
                    default:
                        break;
                }
            }
        }

        try (FileWriter fw = new FileWriter(jaxwsBinding);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            bw.append("    <jaxws:bindings xmlns:jaxws=\"http://java.sun.com/xml/ns/jaxws\"\n");
            bw.append("                    xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\"\n");
            bw.append("                    xmlns:xjc=\"http://java.sun.com/xml/ns/jaxb/xjc\"\n");
            bw.append("                    xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\"\n");
            bw.append("                    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
            bw.append("                    wsdlLocation=\"service.wsdl\"\n");
            bw.append("                    version=\"2.0\">\n");
            if (packageName != null) {
                bw.append("        <jaxws:package name=\"" + packageName + "\"/>\n");
            }
            bw.append("        <jaxws:enableWrapperStyle>false</jaxws:enableWrapperStyle>\n");
            if (s1 != null && t1 != null) {
                bw.append("        <jaxws:bindings node=\"//wsdl:definitions/wsdl:types/xs:schema[@targetNamespace='" + s1 + "']\">\n");
                bw.append("            <jaxb:schemaBindings>\n");
                bw.append("                <jaxb:package name=\"" + t1 + "\"/>\n");
                bw.append("            </jaxb:schemaBindings>\n");
                bw.append("        </jaxws:bindings>\n");
            }
            bw.append("    </jaxws:bindings>");
        }
        return jaxwsBinding;
    }

    private static File createJaxbBinding(File tempRoot) throws IOException {
        File jaxbBinding = new File(tempRoot, "Jaxb-binding.xml");
        jaxbBinding.createNewFile();

        try (FileWriter fw = new FileWriter(jaxbBinding);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jaxb:bindings xmlns:jaxb=\"http://java.sun.com/xml/ns/jaxb\" xmlns:xjc=\"http://java.sun.com/xml/ns/jaxb/xjc\" jaxb:version=\"2.1\">\n" +
                "    <jaxb:globalBindings>\n" +
                "        <xjc:simple/>\n" +
                "    </jaxb:globalBindings>\n" +
                "</jaxb:bindings>");
        }
        return jaxbBinding;
    }

    private static void rewriteJar(OutputStream os, Manifest m, File existingJar) throws IOException {
        JarInputStream jarInputStream = new JarInputStream(new FileInputStream(existingJar));
        JarOutputStream out = new JarOutputStream(os);
        ZipEntry e = new ZipEntry(JarFile.MANIFEST_NAME);
        out.putNextEntry(e);
        m.write(out);
        out.closeEntry();
        ZipEntry zipEntry = jarInputStream.getNextEntry();
        while (zipEntry != null) {
            out.putNextEntry(zipEntry);
            int BUFFER_SIZE = 10240;
            byte buffer[] = new byte[BUFFER_SIZE];
            int l = jarInputStream.read(buffer);
            while (l >= 0) {
                out.write(buffer, 0, l);
                l = jarInputStream.read(buffer);
            }
            zipEntry = jarInputStream.getNextEntry();
        }
        out.close();
    }

    private static void createJar(File target) throws IOException {
        String line;
        Process processJar = new ProcessBuilder().command(
            "/usr/bin/jar",
            "cvf",
            "service.jar",
            "."
        ).directory(target).start();
        InputStream isJar = processJar.getInputStream();
        InputStreamReader isrJar = new InputStreamReader(isJar);
        BufferedReader brJar = new BufferedReader(isrJar);
        while ((line = brJar.readLine()) != null) {
            log.info(line);
        }
        log.info("Jar created");
    }

    public static Set<String> analyze(Source source) throws Exception {
        Set<String> refers = new TreeSet<String>();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Result r = new StreamResult(bout);
        XmlUtils.transform(new StreamSource(WsdlTransformer.class.getResourceAsStream("extract.xsl")), source, r);

        ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        bout.close();

        BufferedReader br = new BufferedReader(new InputStreamReader(bin));

        String line = br.readLine();
        while (line != null) {
            line = line.trim();
            if (line.length() > 0) {
                String parts[] = line.split("\\s*,\\s*");
                for (int i = 0; i < parts.length; i++) {
                    int n = parts[i].lastIndexOf('.');
                    if (n > 0) {
                        String pkg = parts[i].substring(0, n);
                        if (!pkg.startsWith("java.")) {
                            refers.add(pkg);
                        }
                    }
                }
            }
            line = br.readLine();
        }
        br.close();
        return refers;
    }

    protected static String getImportPackages(Set<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : packages) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(pkg);
        }
        return sb.toString();
    }

    protected static Document parse(URL url) throws Exception {
        return XmlUtils.parse(url.toString());
    }

    @Value
    static class FileWithPrefix {
        File file;
        String prefix;
    }
}
