package org.sakaiproject.assignment.datemanager;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import org.sakaiproject.assignment.datemanager.handlers.AssignmentsFeedHandler;
import org.sakaiproject.assignment.datemanager.handlers.AssignmentsUpdateHandler;
import org.sakaiproject.assignment.datemanager.handlers.AssignmentsSmartUpdaterHandler;
import org.sakaiproject.assignment.datemanager.handlers.Handler;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.User;
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

public class DateManagerFilter implements Filter {
    protected String DATE_MANAGER_PATH = "/date-manager";

    private static final Logger LOG = LoggerFactory.getLogger(DateManagerFilter.class);


    public void init(FilterConfig config) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        String path = ((HttpServletRequest) servletRequest).getPathInfo();
        if (path != null && path.startsWith(DATE_MANAGER_PATH)) {
            handleRequest((HttpServletRequest)servletRequest, (HttpServletResponse)servletResponse);
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) {
        URL toolBaseURL = determineBaseURL();

        try {
            Map<String, Object> context = new HashMap<>();

            context.put("baseURL", toolBaseURL);

            if (ToolManager.getCurrentPlacement() != null) {
                context.put("siteId", ToolManager.getCurrentPlacement().getContext());
            }

            Handler handler = handlerForRequest(request);
            handler.handle(request, response, context);

            if (!response.containsHeader("Content-Type")) {
                response.setHeader("Content-Type", handler.getContentType());
            }
        } catch (Exception e) {
            LOG.warn("Something failed", e);
            response.reset();
            response.setStatus(500);
            try {
                e.printStackTrace(response.getWriter());
            } catch (IOException ioe) {
            }
        }
    }

    private Handler handlerForRequest(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null) {
            path = "";
        }

        path = path.replaceAll("^/date-manager/", "/");

        if (path.startsWith("/assignments")) {
            return new AssignmentsFeedHandler();
        } else if (path.startsWith("/update")) {
            return new AssignmentsUpdateHandler();
        } else if (path.startsWith("/smart-updater-calculate")) {
            return new AssignmentsSmartUpdaterHandler();
        }

        throw new RuntimeException("Path not supported by assignment-date-manager handlers: " + path);
    }

    private URL determineBaseURL() {
        try {
            return new URL(ServerConfigurationService.getPortalUrl() + getBaseURI() + "/");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Couldn't determine tool URL", e);
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

    private Handlebars loadHandlebars(final URL baseURL) {
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
