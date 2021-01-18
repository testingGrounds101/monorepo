/**********************************************************************************
 *
 * Copyright (c) 2017 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.pages.tool.handlers;

import org.sakaiproject.pages.tool.model.Errors;
import org.sakaiproject.pages.tool.model.Page;
import org.sakaiproject.pages.tool.storage.PagesStorage;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.cover.SiteService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SaveHandler implements Handler {

    private String redirectTo = null;
    private Errors errors = new Errors();;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            PagesStorage storage = new PagesStorage();
            Optional<Page> page = storage.getForContext((String) context.get("toolID"));

            Page toUpdate = null;

            // Process content through AntiSamy
            StringBuilder warnings = new StringBuilder();
            String cleanTitle = org.sakaiproject.util.FormattedText.processFormattedText(request.getParameter("title"), warnings);
            String cleanContent = org.sakaiproject.util.FormattedText.processFormattedText(request.getParameter("content"), warnings);

            if (cleanTitle.isEmpty()) {
                errors.addError("title", "title_missing");
            }

            if (errors.hasErrors()) {
                context.put("subpage", "edit");
                context.put("page", new Page((String) context.get("toolID"), cleanTitle, cleanContent));
                return;
            }

            if (page.isPresent()) {
                Page update = page.get();
                update.setTitle(cleanTitle);
                update.setContent(cleanContent);
                storage.updatePage(update);
                context.put("page", update);
                toUpdate = update;
            } else {
                Page create = new Page((String) context.get("toolID"), cleanTitle, cleanContent);
                storage.createPage(create);
                context.put("page", create);
                toUpdate = create;
            }

            // update the page's title
            Site site = SiteService.getSite((String) context.get("siteID"));
            for (SitePage sitePage : site.getPages()) {
                if (sitePage.getTool((String) context.get("toolID")) != null) {
                    sitePage.setTitle(toUpdate.getTitle());
                    sitePage.setTitleCustom(true);
                }
            }
            SiteService.save(site);

            redirectTo = "";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasRedirect() {
        return (redirectTo != null);
    }

    public String getRedirect() {
        return redirectTo;
    }

    public Errors getErrors() {
        return errors;
    }

    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<>();
    }
}
