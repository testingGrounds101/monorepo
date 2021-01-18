package edu.nyu.classes.seats;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.tool.cover.ToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;
import edu.nyu.classes.seats.handlers.*;

public class ToolServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ToolServlet.class);
    private SeatingHandlerBackgroundThread backgroundThread = null;

    private AtomicBoolean developmentMode = new AtomicBoolean(false);

    private Long logQueryThresholdMs = null;

    public void init(ServletConfig config) throws ServletException {
        if ("true".equals(HotReloadConfigurationService.getString("seats.development-mode", "false"))) {
            developmentMode.set(true);
        }

        if ("true".equals(HotReloadConfigurationService.getString("auto.ddl.seats", "false")) ||
            developmentMode.get()) {
            new SeatsStorage().runDBMigrations();
        }

        String thresholdFromConfig = HotReloadConfigurationService.getString("seats.log-query-threshold-ms", null);
        if (thresholdFromConfig != null) {
            try {
                logQueryThresholdMs = Long.valueOf(thresholdFromConfig);
            } catch (NumberFormatException e) {
                logQueryThresholdMs = null;
            }
        }

        String runBackgroundTask = HotReloadConfigurationService.getString("seats.run-background-task", null);

        if ("true".equals(runBackgroundTask) || (developmentMode.get() && runBackgroundTask == null)) {
            this.backgroundThread = new SeatingHandlerBackgroundThread().startThread();
            this.backgroundThread.setDBTimingThresholdMs(dbTimingThresholdMs());
        }

        super.init(config);
    }

    private long dbTimingThresholdMs() {
        if (logQueryThresholdMs != null) {
            return logQueryThresholdMs;
        } else {
            return developmentMode.get() ? 10 : 1000;
        }
    }

    public void destroy() {
        super.destroy();
        this.backgroundThread.shutdown();
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        I18n i18n = new I18n(this.getClass().getClassLoader(), "edu.nyu.classes.seats.i18n.seats");

        URL toolBaseURL = determineBaseURL();
        Handlebars handlebars = loadHandlebars(toolBaseURL, i18n);

        try {
            Map<String, Object> context = new HashMap<String, Object>();
            context.put("baseURL", toolBaseURL);
            context.put("layout", true);
            context.put("skinRepo", HotReloadConfigurationService.getString("skin.repo", ""));
            context.put("randomSakaiHeadStuff", request.getAttribute("sakai.html.head"));

            if (ToolManager.getCurrentPlacement() != null) {
                context.put("siteId", ToolManager.getCurrentPlacement().getContext());
            }

            context.put("developmentMode", developmentMode.get());

            context.put("hasSiteUpd", hasSiteUpd((String)context.get("siteId")));

            context.put("portalCdnQuery",
                        developmentMode.get() ?
                        java.util.UUID.randomUUID().toString() :
                        HotReloadConfigurationService.getString("portal.cdn.version", java.util.UUID.randomUUID().toString()));

            if ("true".equals(HotReloadConfigurationService.getString("seats.enable-fiddler", "false"))) {
                context.put("rosterFiddlerEnabled", "true");
            }

            Handler handler = handlerForRequest(request);

            if (handler.isSiteUpdRequired() && !(boolean)context.get("hasSiteUpd")) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            } else if (!hasSiteVisit((String)context.get("siteId"))) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setHeader("Content-Type", handler.getContentType());
            response.setHeader("Cache-Control", "Cache-Control: no-cache, max-age=0");

            DB.transaction("Handle seats API request",
                           (DBConnection db) -> {
                               db.setTimingEnabled(dbTimingThresholdMs());

                               context.put("db", db);
                               try {
                                   handler.handle(request, response, context);
                                   db.commit();
                                   return null;
                               } catch (Exception e) {
                                   db.rollback();
                                   throw new RuntimeException(e);
                               }
                           });

            if (handler.hasRedirect()) {
                if (handler.getRedirect().startsWith("http")) {
                    response.sendRedirect(handler.getRedirect());
                } else {
                    response.sendRedirect(toolBaseURL + handler.getRedirect());
                }
            } else if (handler.hasTemplate()) {
                if (Boolean.TRUE.equals(context.get("layout"))) {
                    Template template = handlebars.compile("edu/nyu/classes/seats/views/layout");
                    response.getWriter().write(template.apply(context));
                } else {
                    Template template = handlebars.compile("edu/nyu/classes/seats/views/" + context.get("subpage"));
                    response.getWriter().write(template.apply(context));
                }
            }
        } catch (IOException e) {
            LOG.warn("Write failed", e);
        } catch (Exception e) {
            LOG.error("Error caught by ToolServlet: " + e);
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Handler handlerForRequest(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null) {
            path = "";
        }

        if (path.startsWith("/sections")) {
            return new SectionsHandler();
        }

        if (path.startsWith("/section")) {
            return new SectionHandler();
        }

        if (path.startsWith("/seat-assignment")) {
            return new SeatAssignmentHandler();
        }

        if (path.startsWith("/split-section")) {
            return new SplitSectionHandler();
        }

        if (path.startsWith("/available-site-members")) {
            return new MembersForAddHandler();
        }

        if (path.startsWith("/add-group-users")) {
            return new GroupAddMembersHandler();
        }

        if (path.startsWith("/student-meetings")) {
            return new StudentMeetingsHandler();
        }

        if (path.startsWith("/save-group-description")) {
            return new GroupDescriptionHandler();
        }

        if (path.startsWith("/add-group")) {
            return new AddGroupHandler();
        }

        if (path.startsWith("/delete-group")) {
            return new DeleteGroupHandler();
        }

        if (path.startsWith("/transfer-group")) {
            return new TransferGroupsHandler();
        }

        if (path.startsWith("/email-group")) {
            return new EmailGroupHandler();
        }

        if (path.startsWith("/remove-group-user")) {
            return new GroupRemoveMembersHandler();
        }

        if ("true".equals(HotReloadConfigurationService.getString("seats.enable-fiddler", "false"))) {
            if (path.startsWith("/roster-fiddler")) {
                return new RosterFiddler();
            }
        }

        return new HomeHandler();
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

    private Handlebars loadHandlebars(final URL baseURL, final I18n i18n) {
        Handlebars handlebars = new Handlebars();

        handlebars.setInfiniteLoops(true);

        handlebars.registerHelper("subpage", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String subpage = options.param(0);
                try {
                    Template template = handlebars.compile("edu/nyu/classes/seats/views/" + subpage);
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

    private boolean hasSiteUpd(String siteId) {
        return SecurityService.unlock("site.upd", "/site/" + siteId);
    }

    private boolean hasSiteVisit(String siteId) {
        return SecurityService.unlock("site.visit", "/site/" + siteId);
    }
}
