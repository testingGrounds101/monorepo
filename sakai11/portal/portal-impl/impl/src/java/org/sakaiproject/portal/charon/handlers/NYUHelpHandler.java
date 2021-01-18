package org.sakaiproject.portal.charon.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.component.cover.ServerConfigurationService;

public class NYUHelpHandler extends BasePortalHandler
{
    private static final Log log = LogFactory.getLog(NYUHelpHandler.class);
    private static final String URL_FRAGMENT = "show-help";

    public NYUHelpHandler()
    {
        setUrlFragment(URL_FRAGMENT);
    }

    public int doPost(String[] parts, HttpServletRequest req, HttpServletResponse res, Session session)
        throws PortalHandlerException
    {
        return NEXT;
    }

    public int doGet(String[] parts, HttpServletRequest req, HttpServletResponse res, Session session)
        throws PortalHandlerException
    {
        if ((parts.length == 2) && (parts[1].equals(URL_FRAGMENT)))
        {
            String url = req.getParameter("url");
            String key = req.getParameter("key");

            if (url == null || key == null || !key.startsWith("externalHelp")) {
                log.error("Couldn't read parameters on show-help request");
                throw new PortalHandlerException("Couldn't read parameters");
            }

            // Validate the URL and key we've been given.
            if (!url.equals(ServerConfigurationService.getString(key + ".url"))) {
                log.error("URL mismatch");
                throw new PortalHandlerException("Couldn't read parameters");
            }

            String tool = ServerConfigurationService.getString(key + ".tool");

            String role = (key.indexOf(".instructor") >= 0) ? "instructor" : "student";
            String userEid = session.getUserEid();

            if (userEid == null) {
                userEid = "(unknown)";
            }

            org.sakaiproject.telemetry.cover.Telemetry.addToCount("help_views", tool + "::" + role + "::" + userEid, 1);

            try {
                res.sendRedirect(url);
            } catch (IOException e) {
                throw new PortalHandlerException(e);
            }

            return END;
        }

        return NEXT;
    }
}
