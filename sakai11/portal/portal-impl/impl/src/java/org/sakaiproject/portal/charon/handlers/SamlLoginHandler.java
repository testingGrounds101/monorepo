package org.sakaiproject.portal.charon.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.tool.api.Session;

import org.sakaiproject.util.ExternalTrustedEvidence;
import org.sakaiproject.user.api.Evidence;
import org.sakaiproject.user.api.Authentication;
import org.sakaiproject.event.cover.UsageSessionService;
import org.sakaiproject.user.cover.AuthenticationManager;
import org.sakaiproject.user.api.AuthenticationException;
import org.sakaiproject.user.api.AuthenticationMissingException;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.portal.util.ErrorReporter;
import org.sakaiproject.portal.api.PortalRenderContext;

import org.sakaiproject.component.cover.ServerConfigurationService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



import edu.nyu.classes.saml.api.SamlService;
import edu.nyu.classes.saml.api.SamlAuthResponse;

import java.io.IOException;

public class SamlLoginHandler extends BasePortalHandler
{

  private static final String URL_FRAGMENT = "samllogin";
  private static final Log log = LogFactory.getLog(SamlLoginHandler.class);


  public SamlLoginHandler()
  {
    setUrlFragment(SamlLoginHandler.URL_FRAGMENT);
  }


  @Override
  public int doPost(String[] parts, HttpServletRequest req, HttpServletResponse res,
                    Session session) throws PortalHandlerException
  {
    if ((parts.length == 2) && (parts[1].equals(SamlLoginHandler.URL_FRAGMENT))) {
      try {
        SamlService saml = (SamlService)ComponentManager.get("edu.nyu.classes.saml.api.SamlService");

        SamlAuthResponse auth = saml.handleSamlAuthentication(req);

        if (auth == null) {
          throw new AuthenticationException("Couldn't validate your SSO session.");
        }

        Evidence e = new ExternalTrustedEvidence(auth.getUser());
        Authentication a = null;

        try {
          a = AuthenticationManager.authenticate(e);
        } catch (AuthenticationMissingException ex) {
          throw new AuthenticationException("User not found for '" + auth.getUser() + "'");
        }

        if (UsageSessionService.login(a, req)) {
          String destination = "/portal";

          if (auth.getDestination() != null) {
            destination = auth.getDestination();
          }

          res.sendRedirect(destination);

          return END;
        } else {
          throw new AuthenticationException("Couldn't log you in");
        }
      } catch (Exception e) {
        try {
          handleSSOError(req, res, session, e);
          return END;
        } catch (Exception ex) {
          throw new PortalHandlerException(ex);
        }
      }
    }

    return NEXT;
  }


  private void handleSSOError(HttpServletRequest req, HttpServletResponse res, Session session,
                              Exception e) throws IOException
  {
    log.error("SSO error", e);

    PortalRenderContext rcontext = portal.startPageContext("", "SAML Login", null, req, null);
    portal.sendResponse(rcontext, res, "samlfailed", null);
  }


  @Override
  public int doGet(String[] parts, HttpServletRequest req, HttpServletResponse res,
                   Session session) throws PortalHandlerException
  {
    return NEXT;
  }
}
