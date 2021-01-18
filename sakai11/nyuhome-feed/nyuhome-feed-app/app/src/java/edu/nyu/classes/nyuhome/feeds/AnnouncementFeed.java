package edu.nyu.classes.nyuhome.feeds;

import lombok.*;
import lombok.experimental.Builder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import edu.nyu.classes.nyuhome.api.QueryUser;
import edu.nyu.classes.nyuhome.api.DataFeed;
import edu.nyu.classes.nyuhome.api.DataFeedEntry;
import edu.nyu.classes.nyuhome.api.Resolver;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.component.cover.ComponentManager;

import org.sakaiproject.javax.Filter;

import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;

import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.cover.TimeService;

import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.announcement.api.AnnouncementService;
import org.sakaiproject.announcement.api.AnnouncementChannel;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.AnnouncementMessageHeader;

import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;

import org.sakaiproject.exception.IdUnusedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Builder
class AnnouncementResponse implements DataFeedEntry {
    @Getter private Date sortDate;
    @Getter private String id;
    @Getter private String subject;
    @Getter private String reference;
    @Getter private String body;
    @Getter private String site__siteid;
    @Getter private String fromUser__userid;
    @Getter private String toolUrl;
}


public class AnnouncementFeed extends SakaiToolFeed {
    private static final Logger LOG = LoggerFactory.getLogger(AnnouncementFeed.class);


    private class NYUHomeFilter implements Filter {
        private MaxAgeAndCountFilter filter;

        public NYUHomeFilter(int maxAgeDays, int maxResults) {
            filter = new MaxAgeAndCountFilter(maxAgeDays, maxResults);
        }


        public boolean accept(Object o) {
            AnnouncementMessage msg = (AnnouncementMessage) o;
            AnnouncementMessageHeader header = msg.getAnnouncementHeader();
            Date messageDate = new Date(header.getDate().getTime());

            return filter.accept(messageDate);
        }
    }


    public List<DataFeedEntry> getUserData(QueryUser user, Resolver resolver, int maxAgeDays, int maxResults) {
        List<DataFeedEntry> result = new ArrayList<DataFeedEntry>();

        AnnouncementService announcementService = (AnnouncementService) ComponentManager.get("org.sakaiproject.announcement.api.AnnouncementService");

        for (String siteId : user.listSites()) {
            try {
                String channelId = announcementService.channelReference(siteId, SiteService.MAIN_CONTAINER);
                AnnouncementChannel channel = announcementService.getAnnouncementChannel(channelId);

                boolean ascending;
                for (Object msg : channel.getMessages(new NYUHomeFilter(maxAgeDays, maxResults), ascending = false)) {
                    if (isVisible((AnnouncementMessage) msg)) {
                        AnnouncementResponse response = prepareMessage((AnnouncementMessage) msg, siteId);
                        result.add(response);

                        resolver.addUser(response.getFromUser__userid());
                    }
                }

                resolver.addSite(siteId);
            } catch (IdUnusedException ex) {
                LOG.debug("Exception when operating on " + siteId, ex);
                // Skip it
            } catch (PermissionException ex) {
                LOG.debug("Exception when operating on " + siteId, ex);
                // Skip it
            }
        }

        return result;
    }


    private Time getTimeProperty(AnnouncementMessage msg, String property) {
        try {
            return msg.getProperties().getTimeProperty(property);
        } catch (EntityPropertyNotDefinedException ex) {
            LOG.debug("Exception getting property", ex);
        } catch (EntityPropertyTypeException ex) {
            LOG.debug("Exception getting property", ex);
        }

        return null;
    }


    private boolean isVisible(AnnouncementMessage msg) {
        Time currentTime = TimeService.newTime();

        Time releaseDate = getTimeProperty(msg, AnnouncementService.RELEASE_DATE);
        Time retractDate = getTimeProperty(msg, AnnouncementService.RETRACT_DATE);

        boolean postReleaseDate = (releaseDate == null || currentTime.after(releaseDate));
        boolean preRetractDate = (retractDate == null || currentTime.before(retractDate));
        boolean isDraft = msg.getAnnouncementHeader().getDraft();

        return postReleaseDate && preRetractDate && !isDraft;
    }


    private AnnouncementResponse prepareMessage(AnnouncementMessage msg, String siteId) {
        AnnouncementMessageHeader header = msg.getAnnouncementHeader();

        return new AnnouncementResponse.AnnouncementResponseBuilder()
            .sortDate(new Date(header.getDate().getTime()))
            .id(msg.getId())
            .fromUser__userid(header.getFrom().getId())
            .subject(header.getSubject())
            .reference(msg.getReference())
            .body(msg.getBody())
            .site__siteid(siteId)
            .toolUrl(buildUrl(siteId, "sakai.announcements"))
            .build();
    }

}
