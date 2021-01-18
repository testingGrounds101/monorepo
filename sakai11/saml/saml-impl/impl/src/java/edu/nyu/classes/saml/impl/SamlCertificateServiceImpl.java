package edu.nyu.classes.saml.impl;

import edu.nyu.classes.saml.api.SamlCertificateService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.xml.security.Init;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.content.x509.XMLX509Certificate;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.zip.*;
import java.nio.file.*;


public class SamlCertificateServiceImpl implements SamlCertificateService
{
  private final static String SIGNATURE_NAMESPACE_URI = "http://www.w3.org/2000/09/xmldsig#";
  private final static String SAML_METADATA_NAMESPACE_URI = "urn:oasis:names:tc:SAML:2.0:metadata";

  private static final Logger LOG = LoggerFactory.getLogger(SamlCertificateServiceImpl.class);
  private AtomicReference<Map<String, List<X509Certificate>>> certificateStore;
  private AtomicBoolean running = new AtomicBoolean(false);

  String incommonXMLFile;
  long incommonXMLFileModifiedTime;
  String incommonCertificateFile;

  String additionalCertificatesFile;
  long additionalCertificatesFileModifiedTime;


  public void init() throws IOException
  {
    LOG.info("init()");

    incommonXMLFile = ServerConfigurationService.getString("edu.nyu.classes.saml.incommonXMLFile", null);
    incommonCertificateFile = ServerConfigurationService.getString("edu.nyu.classes.saml.incommonCertificateFile", null);

    additionalCertificatesFile = ServerConfigurationService.getString("edu.nyu.classes.saml.additionalCertificatesFile", null);

    certificateStore = new AtomicReference<Map<String, List<X509Certificate>>>();

    try {
      atomicUpdateCertificates();

      ZipOutputStream backup = new ZipOutputStream(new FileOutputStream(new File(new File(incommonXMLFile).getParentFile(),
                                                                                 String.format("incommon_backup_%s.zip",
                                                                                               ServerConfigurationService.getServerId()))));

      for (String file : new String[] { incommonXMLFile, incommonCertificateFile, additionalCertificatesFile }) {
        if (file != null) {
          ZipEntry entry = new ZipEntry(new File(file).getName());
          backup.putNextEntry(entry);
          byte[] bytes = Files.readAllBytes(Paths.get(file));
          backup.write(bytes, 0, bytes.length);
        }
      }

      backup.close();
    } catch (IOException e) {
      LOG.error("Couldn't load the certificate list:");
      e.printStackTrace();
    }

    startBackgroundCertificateUpdate();
  }


  public void destroy()
  {
    running.set(false);

    LOG.info("destroy()");
  }


  private void startBackgroundCertificateUpdate()
  {
    running.set(true);

    Thread worker = new Thread() {
        public void run() {

          while (running.get()) {
            try {
              if ((incommonXMLFile != null && new File(incommonXMLFile).lastModified() != incommonXMLFileModifiedTime) ||
                  (additionalCertificatesFile != null && new File(additionalCertificatesFile).lastModified() != additionalCertificatesFileModifiedTime)) {
                atomicUpdateCertificates();
              }
            } catch (IOException ex) {
              LOG.info("IOException while updating certificates: " + ex);
              ex.printStackTrace();
            }

            try {
              Thread.sleep(120000);
            } catch (InterruptedException ex) {}
          }
        }
      };


    worker.start();
  }


  private void atomicUpdateCertificates() throws IOException
  {
    LOG.info("Refreshing the set of stored certificates");
    certificateStore.set(loadCertificates());
    LOG.info("Done");
  }


  private Document parseDocument(InputStream is)
    throws ParserConfigurationException, SAXException, IOException
  {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setAttribute("http://xml.org/sax/features/namespaces", Boolean.TRUE);

    DocumentBuilder db = dbf.newDocumentBuilder();

    return db.parse(is);
  }


  private X509Certificate parseCertificate(InputStream is) throws CertificateException
  {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");

    return (X509Certificate)cf.generateCertificate(is);
  }


  private void loadCertificates(Document metadata, Map<String, List<X509Certificate>> result)
    throws XPathExpressionException, XMLSecurityException
  {
    // Parse all entityIDs and their certificates.
    List<Element> entityDescriptors = findMatchingElements("EntityDescriptor", SAML_METADATA_NAMESPACE_URI, metadata.getDocumentElement());

    for (Element descriptor : entityDescriptors) {
      String entityId = descriptor.getAttribute("entityID");

      if (entityId == null) {
        continue;
      }

      List<X509Certificate> certs = new ArrayList<X509Certificate>();

      List<Element> certificateElements = findMatchingElements("X509Certificate", SIGNATURE_NAMESPACE_URI, descriptor);

      for (Element elt : certificateElements) {
        XMLX509Certificate cert = new XMLX509Certificate(elt, metadata.getNamespaceURI());
        certs.add(cert.getX509Certificate());
      }

      result.put(entityId, certs);

      // We're done with this node now.  Remove it from the document to improve
      // the performance of subsequent XPath queries.  Turns out that the list
      // of nodes is stored as a linked list, so it gets slower and slower as
      // you move through the document unless you do this.
      //
      // Reference: http://stackoverflow.com/questions/3782618/xpath-evaluate-performance-slows-down-absurdly-over-multiple-calls
      //
      descriptor.getParentNode().removeChild(descriptor);
    }
  }


  private void loadInCommonCertificates(Map<String, List<X509Certificate>> result)
    throws IOException
  {
    if (incommonCertificateFile == null || incommonXMLFile == null) {
      if (additionalCertificatesFile == null) {
        throw new IOException("edu.nyu.classes.saml.incommonXMLFile and" +
                              " edu.nyu.classes.saml.incommonCertificateFile" +
                              " must be set in sakai.properties");
      } else {
        LOG.warn("No incommonCertificateFile was set, but additionalCertificatesFile is present.  Just using that.");        
        return;
      }
    }

    try {

      incommonXMLFileModifiedTime = new File(incommonXMLFile).lastModified();
      FileInputStream fh = new FileInputStream(incommonXMLFile);
      Document metadata = parseDocument(fh);
      metadata.getDocumentElement().setIdAttribute("ID", true);
      fh.close();

      fh = new FileInputStream(incommonCertificateFile);
      X509Certificate masterCertificate = parseCertificate(fh);
      fh.close();

      // Initialise Apache XML security
      Init.init();

      // Verify the master signature
      XMLSignature sig = new XMLSignature(findMatchingElement("Signature", SIGNATURE_NAMESPACE_URI, metadata.getDocumentElement()),
                                          "");
      // XMLSignature sig = new XMLSignature((Element)xpath.evaluate("//*[name() = 'ds:Signature']",
      //                                                    metadata,
      //                                                    XPathConstants.NODE),
      //                                     "");


      if (!sig.checkSignatureValue(masterCertificate)) {
        throw new XMLSignatureException("Master signature verification failed!");
      }


      loadCertificates(metadata, result);

    } catch (IOException e) {
      LOG.error("Failed to open input required input file");
      throw new RuntimeException("loadCertificates couldn't complete", e);
    } catch (Exception e) {
      LOG.error("Failure when loading InCommon certificates: " + e);
      throw new RuntimeException("Failure when loading InCommon certificates", e);
    }
  }


  private void loadAdditionalCertificates(Map<String, List<X509Certificate>> result)
    throws IOException
  {
    if (additionalCertificatesFile == null) {
      // No problem.  This is optional.
      return;
    }

    try {
      additionalCertificatesFileModifiedTime = new File(additionalCertificatesFile).lastModified();
      FileInputStream fh = new FileInputStream(additionalCertificatesFile);
      Document metadata = parseDocument(fh);
      fh.close();

      loadCertificates(metadata, result);

    } catch (IOException e) {
      LOG.error("Failed to open input required input file");
      throw new RuntimeException("loadCertificates couldn't complete", e);
    } catch (Exception e) {
      LOG.error("Failure when loading InCommon certificates: " + e);
      throw new RuntimeException("Failure when loading additional certificates", e);
    }
  }



  private Map<String, List<X509Certificate>> loadCertificates() throws IOException
  {
    Map<String, List<X509Certificate>> result = new HashMap<String, List<X509Certificate>>();

    loadInCommonCertificates(result);
    loadAdditionalCertificates(result);

    return result;
  }


  public List<X509Certificate> getCertificatesFor(String entityId)
  {
    return certificateStore.get().get(entityId);
  }

  private Element findMatchingElement(String nodeName, String expectedNamespaceURI, Element elt) {
      List<Element> result = findMatchingElements(nodeName, expectedNamespaceURI, elt);

      if (result.isEmpty()) {
          throw new RuntimeException(String.format("Couldn't find a node '%s' in namespace '%s' for element '%s'",
                                                   nodeName,
                                                   expectedNamespaceURI,
                                                   elt));
      } else {
          return result.get(0);
      }
  }

  private List<Element> findMatchingElements(String nodeName, String expectedNamespaceURI, Element elt) {
    try {
      XPath xpath = XPathFactory.newInstance().newXPath();

      List<Element> result = new ArrayList<>();

      NodeList nodes = (NodeList)xpath.evaluate(String.format("self::node()[local-name() = '%s']|.//*[local-name() = '%s']", nodeName, nodeName),
                                                elt,
                                                XPathConstants.NODESET);

      for (int i = 0; i < nodes.getLength(); i++) {
        Node matched = nodes.item(i);

        if (expectedNamespaceURI.equals(matched.getNamespaceURI())) {
          result.add((Element)matched);
        }
      }

      return result;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
