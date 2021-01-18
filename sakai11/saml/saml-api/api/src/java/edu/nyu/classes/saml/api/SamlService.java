package edu.nyu.classes.saml.api;

import javax.servlet.http.HttpServletRequest;

public interface SamlService
{
  public SamlAuthResponse handleSamlAuthentication(HttpServletRequest req);
}
