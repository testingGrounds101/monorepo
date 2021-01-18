/**********************************************************************************
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008, 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.site.tool;

import java.io.IOException;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupProvider;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.AcademicSession;
import org.sakaiproject.site.cover.SiteService;

public class CollabSitesService extends HttpServlet
{
    public void init() throws ServletException {
        super.init();
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
    {
        try {
            if ("list-sessions".equals(req.getParameter("action"))) {
                listSessions(req, res);
            } else if ("sections-for-session".equals(req.getParameter("action"))) {
                handleSectionsForSession(req, res);
            } else {
                throw new ServletException("Not found");
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private String getParameterOrDie(HttpServletRequest req, String param) throws ServletException {
        String result = req.getParameter(param);

        if (result == null) {
            throw new ServletException("Missing value for parameter: " + param);
        } else {
            return result;
        }
    }

    private void listSessions(HttpServletRequest req, HttpServletResponse res) throws Exception {
        JSONArray result = new JSONArray();

        CourseManagementService cms = (CourseManagementService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.coursemanagement.api.CourseManagementService");

        for (AcademicSession session : cms.getCurrentAcademicSessions()) {
            JSONObject jsonSession = new JSONObject();
            jsonSession.put("eid", session.getEid());
            jsonSession.put("title", session.getTitle());
            result.add(jsonSession);
        }

        res.addHeader("Content-Type", "text/json;charset=utf-8");
        res.getWriter().write(result.toString());
    }

    private void handleSectionsForSession(HttpServletRequest req, HttpServletResponse res) throws Exception {
        String sessionEid = getParameterOrDie(req, "sessionEid");
        String siteId = getParameterOrDie(req, "siteId");

        SiteAction siteAction = new SiteAction();
        Collection<SiteAction.SectionObject> sections = siteAction.getAvailableSectionsForCurrentUser(sessionEid);

        AuthzGroupService ags = (AuthzGroupService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.authz.api.AuthzGroupService");
        GroupProvider groupProvider = (GroupProvider) ComponentManager.get("org.sakaiproject.authz.api.GroupProvider");

        AuthzGroup siteRealm = ags.getAuthzGroup(SiteService.siteReference(siteId));
        List<String> realmProviders = Arrays.asList(groupProvider.unpackId(siteRealm.getProviderGroupId()));

        Map<String, SectionOfInterest> result = new HashMap<>();

        for (SiteAction.SectionObject section : sections) {
            result.put(section.getEid(),
                       new SectionOfInterest(section.getEid(), section.getTitle(), section.getSponsorSectionEid(), realmProviders.contains(section.getEid())));
        }

        List<String> sectionKeys = new ArrayList(result.keySet());
        for (String sectionEid : sectionKeys) {
            SectionOfInterest section = result.get(sectionEid);

            if (section != null && section.sponsorSection != null) {
                SectionOfInterest sponsorSection = result.get(section.sponsorSection);
                sponsorSection.crosslistedNonSponsors.add(result.remove(sectionEid));
            }
        }

        res.addHeader("Content-Type", "text/json;charset=utf-8");
        res.getWriter().write(toJSON(result));
    }

    private String toJSON(Map<String, SectionOfInterest> sections) {
        JSONArray result = new JSONArray();

        for (SectionOfInterest section : sections.values()) {
            result.add(section.toJSON());
        }

        return result.toString();
    }

    private class SectionOfInterest {
        public String sectionEid;
        public String sectionTitle;
        public String sponsorSection;
        public Boolean added;

        public List<SectionOfInterest> crosslistedNonSponsors;

        public SectionOfInterest(String sectionEid, String sectionTitle, String sponsorSection, boolean added) {
            this.sectionEid = sectionEid;
            this.sectionTitle = sectionTitle;
            this.sponsorSection = sponsorSection;
            this.crosslistedNonSponsors = new ArrayList<>();
            this.added = added; 
        }

        public JSONObject toJSON() {
            JSONObject result = new JSONObject();

            result.put("sectionEid", sectionEid);
            result.put("sectionTitle", sectionTitle);
            result.put("added", added);

            JSONArray crosslisted = new JSONArray();
            for (SectionOfInterest nonsponsor : this.crosslistedNonSponsors) {
                crosslisted.add(nonsponsor.toJSON());
            }

            result.put("crosslistedNonSponsors", crosslisted);

            return result;
        }
    }
}
