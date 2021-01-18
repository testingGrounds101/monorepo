package org.sakaiproject.content.googledrive;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.googledrive.handlers.*;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GoogleDriveFilter implements Filter {
    protected String GOOGLE_DRIVE_PATH = "/google-drive";

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveFilter.class);


    public void init(FilterConfig config) throws ServletException {
        try {
            new GoogleClient().migrateOAuthTokens();
        } catch (IOException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        String path = ((HttpServletRequest) servletRequest).getPathInfo();
        if (path != null && path.startsWith(GOOGLE_DRIVE_PATH)) {
            handleRequest((HttpServletRequest)servletRequest, (HttpServletResponse)servletResponse);
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) {
        String googleUser = GoogleClient.getCurrentGoogleUser();

        I18n i18n = new I18n(this.getClass().getClassLoader(), "org.sakaiproject.content.googledrive.i18n.drive");

        URL toolBaseURL = determineBaseURL();
        URL googleDriveURL = determineGoogleDriveURL(toolBaseURL);
        Handlebars handlebars = loadHandlebars(toolBaseURL, googleDriveURL, i18n);

        try {
            Map<String, Object> context = new HashMap<String, Object>();

            context.put("baseURL", toolBaseURL);
            context.put("googleDriveURL", googleDriveURL);
            context.put("layout", true);
            context.put("skinRepo", ServerConfigurationService.getString("skin.repo", ""));
            context.put("randomSakaiHeadStuff", request.getAttribute("sakai.html.head"));
            context.put("googleUser", googleUser);

            // context may be null on handle-google-login callback 
            if (ToolManager.getCurrentPlacement() != null) {
                context.put("siteId", ToolManager.getCurrentPlacement().getContext());
            }

            Handler handler = handlerForRequest(request);

            handler.handle(request, response, context);

            if (!response.containsHeader("Content-Type")) {
                response.setHeader("Content-Type", handler.getContentType());
            }

            if (handler.hasRedirect()) {
                String redirectURL = handler.getRedirect();
                if (!redirectURL.startsWith("http")) {
                    redirectURL = toolBaseURL + redirectURL;
                }
                context.put("redirectURL", redirectURL);
                Template template = handlebars.compile("org/sakaiproject/content/googledrive/views/redirect");
                response.getWriter().write(template.apply(context));
            } else if (handler.hasTemplate()) {
                if (Boolean.TRUE.equals(context.get("layout"))) {
                    Template template = handlebars.compile("org/sakaiproject/content/googledrive/views/layout");
                    response.getWriter().write(template.apply(context));
                } else {
                    Template template = handlebars.compile("org/sakaiproject/content/googledrive/views/" + context.get("subpage"));
                    response.getWriter().write(template.apply(context));
                }
            }
        } catch (IOException e) {
            LOG.warn("Write failed", e);
        }
    }

    private Handler handlerForRequest(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null) {
            path = "";
        }

        path = path.replaceAll("^/google-drive/", "/");

        GoogleClient google = new GoogleClient();

        if ("/drive-tool/handle-google-login".equals(request.getRequestURI())) {
            return new OAuthHandler(OAuthHandler.HANDLE_OAUTH);
        } else if (path.startsWith("/reset-oauth")) {
            return new OAuthHandler(OAuthHandler.RESET);
        } else if (google.getCredential() == null) {
            return new OAuthHandler(OAuthHandler.SEND_TO_GOOGLE);
        } else if (path.startsWith("/show-google-drive")) {
            return new ShowGoogleDriveHandler();
        } else if (path.startsWith("/drive-data")) {
            return new DriveHandler(DriveHandler.RECENT);
        } else if (path.startsWith("/my-drive-data")) {
            return new DriveHandler(DriveHandler.MY_DRIVE);
        } else if (path.startsWith("/starred-drive-data")) {
            return new DriveHandler(DriveHandler.STARRED);
        } else if (path.startsWith("/new-google-item")) {
            return new NewGoogleItemHandler();
        } else if (path.startsWith("/create-google-item")) {
            return new CreateGoogleItemHandler();
        } else if (path.startsWith("/edit-google-item")) {
            return new EditGoogleItemHandler();
        } else if (path.startsWith("/update-google-item")) {
            return new UpdateGoogleItemHandler();
        }

        throw new RuntimeException("Path not supported by googledrive handlers: " + path);
    }

    private URL determineBaseURL() {
        try {
            return new URL(ServerConfigurationService.getPortalUrl() + getBaseURI() + "/");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't determine tool URL", e);
        }
    }

    private URL determineGoogleDriveURL(URL baseURL) {
        try {
            URI uri = baseURL.toURI();
            String newPath = uri.getPath() + "/google-drive/";
            URI newUri = uri.resolve(newPath);
            return newUri.toURL();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Couldn't determine google drive URL", e);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't determine google drive URL", e);
        }
    }

    private String getBaseURI() {
        String result = "";

        String siteId = null;
        String toolId = null;

        if (ToolManager.getCurrentPlacement() != null) {
            siteId = ToolManager.getCurrentPlacement().getContext();
            toolId = ToolManager.getCurrentPlacement().getId();
        }

        if (siteId != null) {
            result += "/site/" + siteId;
            if (toolId != null) {
                result += "/tool/" + toolId;
            }
        }

        return result;
    }

    private Handlebars loadHandlebars(final URL baseURL, final URL googleDriveURL, final I18n i18n) {
        Handlebars handlebars = new Handlebars();

        handlebars.setInfiniteLoops(true);

        handlebars.registerHelper("subpage", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String subpage = options.param(0);
                try {
                    Template template = handlebars.compile("org/sakaiproject/content/googledrive/views/" + subpage);
                    return template.apply(context);
                } catch (IOException e) {
                    LOG.warn("IOException while loading subpage", e);
                    return "";
                }
            }
        });

        handlebars.registerHelper(Handlebars.HELPER_MISSING, new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) throws IOException {
                throw new RuntimeException("Failed to find a match for: " + options.fn.text());
            }
        });

        handlebars.registerHelper("show-time", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                long utcTime = options.param(0) == null ? 0 : options.param(0);

                if (utcTime == 0) {
                    return "-";
                }

                Time time = TimeService.newTime(utcTime);

                return time.toStringLocalFull();
            }
        });

        handlebars.registerHelper("actionURL", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String type = options.param(0);
                String uuid = options.param(1);
                String action = options.param(2);

                try {
                    return new URL(baseURL, type + "/" + uuid + "/" + action).toString();
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Failed while building action URL", e);
                }
            }
        });

        handlebars.registerHelper("newURL", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String type = options.param(0);
                String action = options.param(1);

                try {
                    return new URL(baseURL, type + "/" + action).toString();
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Failed while building newURL", e);
                }
            }
        });

        handlebars.registerHelper("t", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String key = Arrays.stream(options.params).map(Object::toString).collect(Collectors.joining("_"));
                return i18n.t(key);
            }
        });

        handlebars.registerHelper("selected", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String option = options.param(0);
                String value = options.param(1);

                return option.equals(value) ? "selected" : "";
            }
        });

        return handlebars;
    }
}
