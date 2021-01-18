package edu.nyu.classes.saml.impl;

import java.net.URISyntaxException;
import java.net.URI;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.NoVelocitySamlBootstrap;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.SignableSAMLObject;
import org.opensaml.common.binding.BasicSAMLMessageContext;
import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.common.binding.decoding.URIComparator;
import org.opensaml.saml2.binding.decoding.HTTPPostDecoder;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.Subject;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.core.SubjectConfirmationData;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.ws.message.decoder.MessageDecodingException;
import org.opensaml.ws.transport.http.HttpServletRequestAdapter;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.validation.ValidationException;
import java.util.Date;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.w3c.dom.Element;
import java.util.List;
import java.util.ArrayList;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;

import edu.nyu.classes.saml.api.SamlService;
import edu.nyu.classes.saml.api.SamlAuthResponse;
import edu.nyu.classes.saml.api.SamlCertificateService;

import org.sakaiproject.component.cover.HotReloadConfigurationService;


public class SamlServiceImpl implements SamlService
{
  private static final Logger LOG = LoggerFactory.getLogger(SamlServiceImpl.class);

  private static String EDU_PRINCIPAL_NAME = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";


  private HTTPPostDecoder decoder;
  private URIComparator uriComparator = new URIComparator() {
      public boolean compare(String uri1, String uri2) {
        try {
          URI urii1 = new URI(uri1);
          URI urii2 = new URI(uri2);
          return urii1.getHost().equals(urii2.getHost());
        } catch (URISyntaxException e) {
          return false;
        }
      }
    };

  private SAMLSignatureProfileValidator profileValidator;

  private int skewAdjustmentSeconds =
    ServerConfigurationService.getInt("edu.nyu.classes.saml.clockSkewAdjustmentSeconds", 180);


  private SamlCertificateService certificateService;


  public void init()
  {
    LOG.info("init()");
    try {
      NoVelocitySamlBootstrap.bootstrap();
    } catch (ConfigurationException e) {
      throw new RuntimeException(e.getMessage(), e);
    }

    BasicParserPool parserPool = new BasicParserPool();

    decoder = new HTTPPostDecoder(parserPool);
    profileValidator = new SAMLSignatureProfileValidator();

    certificateService = (SamlCertificateService)ComponentManager.get("edu.nyu.classes.saml.api.SamlCertificateService");
  }


  public void destroy()
  {
    LOG.info("destroy()");
  }


  private void ensureKnownEntityId(String entityId)
    throws ValidationException
  {
    String knownEntities = ServerConfigurationService.getString("edu.nyu.classes.saml.acceptedEntityIDs", "");

    for (String entity : knownEntities.split(",")) {
      if (entity.equals(entityId)) {
        return;
      }
    }

    throw new ValidationException("Entity ID not known: " + entityId);
  }


  private Map<String,String> processSAMLRequest(HttpServletRequest request)
    throws MessageDecodingException, SecurityException, UnmarshallingException,
           ValidationException, KeyStoreException, NoSuchAlgorithmException,
           CertificateException, IOException, ConfigurationException {

    HttpServletRequestAdapter adapter = new HttpServletRequestAdapter(request);
    SAMLMessageContext<SignableSAMLObject, SignableSAMLObject, SAMLObject> context = new BasicSAMLMessageContext<SignableSAMLObject, SignableSAMLObject, SAMLObject>();
    context.setInboundMessageTransport(adapter);
    decoder.decode(context);
    SignableSAMLObject message = context.getInboundSAMLMessage();
    String entityId = context.getInboundMessageIssuer();

    ensureKnownEntityId(entityId);

    // Either the message is signed, or we require ALL individual assertions to be signed.
    boolean signatureAccepted = false;

    if (message.isSigned()) {
        validateSignature(message, entityId);
        signatureAccepted = true;
    }

    Element samlElement = message.getDOM();
    UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
    Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(samlElement);

    Response response = (Response) unmarshaller.unmarshall(samlElement);
    List<Assertion> assertions = response.getAssertions();

    HashMap<String,String> authnInfo = new HashMap<String,String>();

    authnInfo.put("relayState", context.getRelayState());

    if (assertions == null && assertions.size() == 0) {
        throw new ValidationException("Expected at least one assertion");
    }

    Assertion assertion = assertions.get(0);

    if (assertion.isSigned()) {
        validateSignature(assertion, entityId);
    }

    if (!signatureAccepted && !assertion.isSigned()) {
        throw new ValidationException("Either message or assertion must be signed");
    }

    Subject subject = assertion.getSubject();
    Conditions conditions = assertion.getConditions();
    String url = request.getRequestURL().toString();
    Date date = new Date();

    // validate the date range of the subject confirmation
    boolean validConfirmation = validConfirmation(subject, url, date);

    // validate the date range of the conditions
    boolean validConditions = validConditions(conditions, url, date);

    // assertion has the proper date range and we're the intended audience
    LOG.debug(String.format("Assertion validation: confirmation: %s, conditions: %s",
                            validConfirmation, validConditions));
    if (validConfirmation && validConditions) {
        NameID nameId = subject.getNameID();
        String netId = nameId.getValue();
        String principal = netId;

        authnInfo.put("credentials", principal);

        for (AttributeStatement attrStmt : assertion.getAttributeStatements()) {
            for (Attribute attr : attrStmt.getAttributes()) {
                List<Object> vals = new ArrayList<Object>();
                List<XMLObject> values = attr.getAttributeValues();
                for (XMLObject xml : values) {
                    vals.add(xml.getDOM().getTextContent());
                }
                if (vals.size() == 1) {
                    authnInfo.put(attr.getName(), vals.get(0).toString());
                } else if (vals.size() > 1) {
                    authnInfo.put(attr.getName(), vals.toString());
                }
            }
        }
    }
    return authnInfo;
  }

  /**
   * Validate that the signature found matches our certificate.
   *
   * @param signature
   * @throws ValidationException
   */
  private void validateSignature(SignableSAMLObject samlObj, String entityId) throws ValidationException {

    Signature signature = samlObj.getSignature();
    profileValidator.validate(signature);

    List<X509Certificate> validCertificates = certificateService.getCertificatesFor(entityId);

    boolean success = false;

    if (validCertificates != null) {
      for (X509Certificate certificate : validCertificates) {
        BasicX509Credential credential = new BasicX509Credential();
        credential.setEntityCertificate(certificate);
        SignatureValidator sigValidator = new SignatureValidator(credential);

        try {
          sigValidator.validate(signature);
          success = true;
          break;
        } catch (ValidationException e) {
            LOG.debug("Couldn't validate against certificate: {}",
                      certificate.getSubjectDN());

          // Try the next one...
        }
      }
    }

    if (!success) {
      throw new ValidationException("No matching certificates found for entity ID: " + entityId);
    }
  }

  private enum SkewAdjustment {
    EARLIER, LATER
  }


  private DateTime offsetForClockSkew(DateTime orig, SkewAdjustment direction)
  {
    int offset = (direction == SkewAdjustment.EARLIER) ? -1 : 1;

    DateTime result = orig.plusSeconds(skewAdjustmentSeconds * offset);
    LOG.debug("*** offsetForClockSkew adjusted {} to {}", orig, result);
    return result;
  }


  private boolean validConditions(Conditions conditions, String url, Date date) {
    if (conditions == null) {
      return true;
    }

    boolean validConditions = false;
    DateTime notBefore = offsetForClockSkew(conditions.getNotBefore(), SkewAdjustment.EARLIER);
    DateTime notOnOrAfter = offsetForClockSkew(conditions.getNotOnOrAfter(), SkewAdjustment.LATER);

    if (date.after(notBefore.toDate()) && date.before(notOnOrAfter.toDate())) {
      List<AudienceRestriction> restrictions = conditions.getAudienceRestrictions();
      if (restrictions != null && restrictions.size() > 0) {
        for (AudienceRestriction restriction : restrictions) {
          List<Audience> audiences = restriction.getAudiences();
          for (Audience audience : audiences) {
            LOG.debug("Compare {} to {}", url, audience.getAudienceURI());
            if (uriComparator.compare(url, audience.getAudienceURI())) {
              validConditions = true;
              break;
            }
          }
        }
      } else {
        validConditions = true;
      }
    }
    return validConditions;
  }

  private boolean validConfirmation(Subject subject, String url, Date date) {
    boolean validConfirmation = false;
    List<SubjectConfirmation> confirmations = subject.getSubjectConfirmations();
    if (confirmations != null && confirmations.size() > 0) {
      SubjectConfirmationData data = confirmations.get(0).getSubjectConfirmationData();
      DateTime notOnOrAfter = offsetForClockSkew(data.getNotOnOrAfter(), SkewAdjustment.LATER);

      if (date.before(notOnOrAfter.toDate()) && uriComparator.compare(url, data.getRecipient())) {
        validConfirmation = true;
      }
    } else {
      validConfirmation = true;
    }
    return validConfirmation;
  }


  private void dumpRequestMaybe(HttpServletRequest req) {
    if ("true".equals(HotReloadConfigurationService.getString("nyu.shibboleth.dump-failed-requests", "true"))) {
      StringBuilder sb = new StringBuilder();

      sb.append("FAILED REQUEST FOLLOWS:\n\n");

      sb.append("  Remote address: " + req.getRemoteAddr() + "\n");

      Map<String, String[]> params = req.getParameterMap();

      for (String k : params.keySet()) {
        for (String v : params.get(k)) {
          sb.append(String.format("  param[%s]=%s\n", k, v));
        }
      }

      LOG.info(sb.toString());
    }
  }

  public SamlAuthResponse handleSamlAuthentication(HttpServletRequest req)
  {
    try {
      final Map<String,String> response = processSAMLRequest(req);
      final String qualifiedNetID = response.get(EDU_PRINCIPAL_NAME);

      LOG.debug("Response from SAML authentication was: {}", response);

      if (response == null || qualifiedNetID == null) {
        dumpRequestMaybe(req);
        return null;
      }

      return new SamlAuthResponse() {
        public String getUser() {
          return qualifiedNetID.split("@")[0];
        }

        public String getDestination() {
          return response.get("relayState");
        }
      };

    } catch (MessageDecodingException e1) {
      LOG.error("Unable to decode incoming SAML Request", e1);
    } catch (KeyStoreException e1) {
      LOG.error("An exception occured when accessing keystore", e1);
    } catch (NoSuchAlgorithmException e1) {
      LOG.error("Exception where Algorithm not found", e1);
    } catch (CertificateException e1) {
      LOG.error("Error in accessing certificate", e1);
    } catch (SecurityException e1) {
      LOG.error("Unable to decode incoming SAML Request", e1);
    } catch (UnmarshallingException e1) {
      LOG.error("Unable to Unmarshal incoming SAML Assertion", e1);
    } catch (ValidationException e1) {
      LOG.error("Unable to validate SAML assertion", e1);
    } catch (IOException e1) {
      LOG.error("Error occured accessing keystore", e1);
    } catch (ConfigurationException e1) {
      LOG.error("Error occured in openSAML config initialization", e1);
    }

    dumpRequestMaybe(req);
    return null;
  }
}
