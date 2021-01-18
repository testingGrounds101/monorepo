package edu.nyu.classes.saml.api;

import java.security.cert.X509Certificate;
import java.util.List;

public interface SamlCertificateService
{
  public List<X509Certificate> getCertificatesFor(String entityId);
}
