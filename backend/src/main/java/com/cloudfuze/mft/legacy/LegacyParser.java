package com.cloudfuze.mft.legacy;

import com.cloudfuze.mft.common.ApiException;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses legacy MFT config exports into {@link LegacyJob}s. Two representative vendor formats are
 * supported (MOVEit-style task lists and GoAnywhere-style projects); the shapes are simplified but
 * faithful to how those tools model source → process → destination. Real proprietary exports vary,
 * so anything we can't map is surfaced for review rather than guessed.
 *
 * <p>The XML reader is hardened against XXE (external entities and DTDs disabled) — this ingests
 * untrusted customer files.
 */
public final class LegacyParser {

    private LegacyParser() {
    }

    public static List<LegacyJob> parse(String sourceType, byte[] xml) {
        Document doc = readSecure(xml);
        return switch (sourceType.toUpperCase()) {
            case "MOVEIT" -> parseMoveit(doc);
            case "GOANYWHERE" -> parseGoAnywhere(doc);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Unsupported source '" + sourceType + "' (supported: MOVEIT, GOANYWHERE)");
        };
    }

    // <MOVEitTasks><Task name="" schedule="">
    //   <Source type="sftp|s3|folder" host="" path=""/>
    //   <Process pgpEncrypt="true" pgpDecrypt="false"/>
    //   <Destination type="" host="" path=""/></Task></MOVEitTasks>
    private static List<LegacyJob> parseMoveit(Document doc) {
        List<LegacyJob> jobs = new ArrayList<>();
        NodeList tasks = doc.getElementsByTagName("Task");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element t = (Element) tasks.item(i);
            Element src = child(t, "Source");
            Element dst = child(t, "Destination");
            Element proc = child(t, "Process");
            jobs.add(new LegacyJob(
                    attr(t, "name", "Imported task " + (i + 1)),
                    blankToNull(attr(t, "schedule", "")),
                    src != null ? attr(src, "type", "folder") : "folder",
                    src != null ? attr(src, "host", "") : "",
                    src != null ? attr(src, "path", "") : "",
                    proc != null && Boolean.parseBoolean(attr(proc, "pgpEncrypt", "false")),
                    proc != null && Boolean.parseBoolean(attr(proc, "pgpDecrypt", "false")),
                    dst != null ? attr(dst, "type", "folder") : "folder",
                    dst != null ? attr(dst, "host", "") : "",
                    dst != null ? attr(dst, "path", "") : ""));
        }
        return jobs;
    }

    // <project><job name="" cron="">
    //   <input protocol="sftp|s3|folder" host="" dir=""/>
    //   <encrypt>true</encrypt>
    //   <output protocol="" host="" dir=""/></job></project>
    private static List<LegacyJob> parseGoAnywhere(Document doc) {
        List<LegacyJob> jobs = new ArrayList<>();
        NodeList tasks = doc.getElementsByTagName("job");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element t = (Element) tasks.item(i);
            Element in = child(t, "input");
            Element out = child(t, "output");
            Element enc = child(t, "encrypt");
            jobs.add(new LegacyJob(
                    attr(t, "name", "Imported job " + (i + 1)),
                    blankToNull(attr(t, "cron", "")),
                    in != null ? attr(in, "protocol", "folder") : "folder",
                    in != null ? attr(in, "host", "") : "",
                    in != null ? attr(in, "dir", "") : "",
                    enc != null && "true".equalsIgnoreCase(text(enc)),
                    false,
                    out != null ? attr(out, "protocol", "folder") : "folder",
                    out != null ? attr(out, "host", "") : "",
                    out != null ? attr(out, "dir", "") : ""));
        }
        return jobs;
    }

    private static Document readSecure(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // XXE hardening: no DTDs, no external entities.
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new ByteArrayInputStream(xml));
            doc.getDocumentElement().normalize();
            return doc;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not parse config file: " + e.getMessage());
        }
    }

    private static Element child(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getParentNode() == parent && n instanceof Element el) {
                return el;
            }
        }
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private static String attr(Element e, String name, String dflt) {
        String v = e.getAttribute(name);
        return v == null || v.isBlank() ? dflt : v.trim();
    }

    private static String text(Element e) {
        return e.getTextContent() == null ? "" : e.getTextContent().trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
