package edu.nyu.classes.groupersync.tool;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import edu.nyu.classes.groupersync.api.AddressFormatter;
import edu.nyu.classes.groupersync.api.GrouperSyncService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;

public class IndexHandler extends BaseHandler {

    enum MessageStrings {
        GROUP_IN_USE("That group name is taken - please choose another"),
        GROUP_CREATED("Your new group is now being created"),
        GROUP_UPDATED("Group details updated"),
        UPDATE_FAILED("Group update could not be completed"),
        GROUP_DELETED("Group successfully deleted"),
        DELETE_FAILED("Group delete could not be completed");

        private String msg;

        MessageStrings(String msg) {
            this.msg = msg;
        }

        public String toString() {
            return msg;
        }
    }


    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setHeader("Content-Type", "text/html");

        GrouperSyncService grouper = getGrouperSyncService();
        String siteId = ToolManager.getCurrentPlacement().getContext();

        try {
            Site site = SiteService.getSite(siteId);

            List<GroupView> wholeSite = new ArrayList<GroupView>();
            List<GroupView> sections = new ArrayList<GroupView>();
            List<GroupView> adhocGroups = new ArrayList<GroupView>();

            wholeSite.add(new GroupView(site, buildAllMembersTitle(site), grouper));
            for (Group group : site.getGroups()) {
                GroupView groupView = new GroupView(group, grouper);

                if (group.getProviderGroupId() == null) {
                    adhocGroups.add(groupView);
                } else {
                    sections.add(groupView);
                }
            }

            Collections.sort(sections);
            Collections.sort(adhocGroups);

            Map<String, Object> context = new HashMap<String, Object>();
            context.put("baseUrl", determineBaseURL());
            context.put("skinRepo", Configuration.getSkinRepo());
            context.put("randomSakaiHeadStuff", request.getAttribute("sakai.html.head"));
            context.put("requiredSuffix", AddressFormatter.format(buildRequiredSuffix(site)));

            context.put("csrfToken", SessionManager.getCurrentSession().getAttribute("sakai.csrf.token"));

            context.put("wholeSite", wholeSite);
            context.put("sections", sections);
            context.put("adhocGroups", adhocGroups);

            // Configuration bits we need
            context.put("maxDescriptionLength", Configuration.getMaxDescriptionLength());
            context.put("maxAddressLength", Configuration.getMaxAddressLength());
            context.put("descriptionExcludedCharacters", Configuration.getDescriptionExcludedCharacters());
            context.put("addressAllowedCharacters", Configuration.getAddressAllowedCharacters());
            context.put("whitespaceReplacementCharacter", Configuration.getWhitespaceReplacementCharacter());

            context.put("subpage", "index");

            if (request.getParameter("error") != null) {
                context.put("error", MessageStrings.valueOf(request.getParameter("error").toUpperCase()));
            }

            if (request.getParameter("success") != null) {
                context.put("success", MessageStrings.valueOf(request.getParameter("success").toUpperCase()));
            }

            if (request.getParameter("info") != null) {
                context.put("info", MessageStrings.valueOf(request.getParameter("info").toUpperCase()));
            }

            Handlebars handlebars = loadHandlebars();
            Template template = handlebars.compile("edu/nyu/classes/groupersync/tool/views/layout");
            response.getWriter().write(template.apply(context));
        } catch (IdUnusedException e) {
            throw new ServletException("Couldn't find site", e);
        }
    }


    private String buildAllMembersTitle(Site site) {
        String title = site.getTitle();
        String suffix = Configuration.getAllSiteMembersSuffix();
        int maxLength = Configuration.getMaxDescriptionLength();

        if ((title.length() + suffix.length()) > maxLength) {
            title = title.substring(0, maxLength - suffix.length());
        }

        return title + suffix;
    }


    private Handlebars loadHandlebars() {
        final Handlebars handlebars = new Handlebars();

        handlebars.registerHelper("subpage", new Helper<Object>() {
            @Override
            public CharSequence apply(final Object context, final Options options) {
                String subpage = options.param(0);
                try {
                    Template template = handlebars.compile("edu/nyu/classes/groupersync/tool/views/" + subpage);
                    return template.apply(context);
                } catch (IOException e) {
                    return "";
                }
            }
        });

        return handlebars;
    }

}
