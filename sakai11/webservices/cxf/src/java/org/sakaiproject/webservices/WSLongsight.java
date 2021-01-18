package org.sakaiproject.webservices;

import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.api.app.messageforums.SynopticMsgcntrItem;
import org.sakaiproject.api.app.messageforums.SynopticMsgcntrManager;
import org.sakaiproject.api.app.messageforums.cover.SynopticMsgcntrManagerCover;
import org.sakaiproject.api.app.syllabus.SyllabusItem;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEdit;
import org.sakaiproject.calendar.api.CalendarEvent;
import org.sakaiproject.calendar.api.CalendarEventEdit;
import org.sakaiproject.calendar.api.RecurrenceRule;
import org.sakaiproject.calendar.cover.CalendarImporterService;
import org.sakaiproject.calendar.cover.CalendarService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.content.api.ResourceType;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.entity.api.EntityTransferrerRefMigrator;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.event.api.UsageSession;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.importer.api.ImportDataSource;
import org.sakaiproject.importer.api.ResetOnCloseInputStream;
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeBreakdown;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.assessment.data.dao.grading.AssessmentGradingData;
import org.sakaiproject.tool.assessment.services.assessment.PublishedAssessmentService;
import org.sakaiproject.tool.gradebook.Gradebook;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.ArrayUtil;
import org.sakaiproject.util.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * WSLongsight
 * <p/>
 * A set of custom Longsight web services for Sakai
 */

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
public class WSLongsight extends AbstractWebService {

	private static final Log LOG = LogFactory.getLog(WSLongsight.class);
	private static final String EVENT_REMOVE_CALENDAR = "calendar.delete";
	private static final String ADMIN_SITE_REALM = "/site/!admin";
	private static final String SESSION_ATTR_NAME_ORIGIN = "origin";
	private static final String SESSION_ATTR_VALUE_ORIGIN_WS = "sakai-axis";
	private static final String CACHE_MANAGER = "org.sakaiproject.memory.api.MemoryService.cacheManager";
	private static final String ID_EID_CACHE = "org.sakaiproject.user.api.UserDirectoryService";

	/* 
	 * Copied from edu-services/sections-service/sections-impl/sakai/model/src/java/org/sakaiproject/component/section/sakai/CourseImpl.java
	 * SAM Note: Beucase I could not find an easy way to get the dependency resolved in Maven
	 */
	private static final String EXTERNALLY_MAINTAINED = "sections_externally_maintained";
	private static final String STUDENT_REGISTRATION_ALLOWED = "sections_student_registration_allowed";
	private static final String STUDENT_SWITCHING_ALLOWED = "sections_student_switching_allowed";
	private static final String STUDENT_OPEN_DATE = "sections_student_open_date";

	@WebMethod
	@Path("/addNewUser")
	@Produces("text/plain")
	@GET
	public String longsightSiteExists(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) 
	{
		Session session = establishSession(sessionid);
		try {
			Boolean siteExist = siteService.siteExists(siteid);
			return siteExist.toString();
		}
		catch(Exception ex) {
		}
		return "false";
	}

	@WebMethod
	@Path("/addNewUser")
	@Produces("text/plain")
	@GET
	public String longsightGetTitle(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			return site.getTitle();
		}
		catch(Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

	}

	@WebMethod
	@Path("/addNewUser")
	@Produces("text/plain")
	@GET
	public String longsightGetIdFromTitle(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "sitetitle", partName = "sitetitle") @QueryParam("sitetitle") String sitetitle) 
	{
		Session session = establishSession(sessionid);

		List<Site> sites = siteService.getSites(org.sakaiproject.site.api.SiteService.SelectionType.ANY,
				null, sitetitle, null, org.sakaiproject.site.api.SiteService.SortType.ID_DESC, null);
		if (sites.size() == 1) {
			return sites.get(0).getId();
		} else {
			return "error: could not find the site, or I found too many with that name";
		}
	}

	@WebMethod
	@Path("/longsightSetTitle")
	@Produces("text/plain")
	@GET
	public String longsightSetTitle(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "title", partName = "title") @QueryParam("title") String title) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			site.setTitle(title);

			siteService.save(site);
			return site.getTitle();
		}
		catch(Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

	}

	@WebMethod
	@Path("/longsightAddPropertiesToSite")
	@Produces("text/plain")
	@GET
	public String longsightAddPropertiesToSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "instructorEmail", partName = "instructorEmail") @QueryParam("instructorEmail") String instructorEmail,
			@WebParam(name = "instructorName", partName = "instructorName") @QueryParam("instructorName") String instructorName,
			@WebParam(name = "currentTermDisplayString", partName = "currentTermDisplayString") @QueryParam("currentTermDisplayString") String currentTermDisplayString,
			@WebParam(name = "termeid", partName = "termeid") @QueryParam("termeid") String termeid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			ResourcePropertiesEdit sitePropertiesEdit = site.getPropertiesEdit();
			//sitePropertiesEdit.addProperty(INDIVIDUAL_COURSE, individualSiteId);
			sitePropertiesEdit.addProperty("contact-email", instructorEmail);
			sitePropertiesEdit.addProperty("contact-name", instructorName);
			sitePropertiesEdit.addProperty("term", currentTermDisplayString);
			sitePropertiesEdit.addProperty("term_eid", termeid);

			siteService.save(site);
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightCreateFolder")
	@Produces("text/plain")
	@GET
	public String longsightCreateFolder(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "folderName", partName = "folderName") @QueryParam("folderName") String folderName,
			@WebParam(name = "folderDescription", partName = "folderDescription") @QueryParam("folderDescription") String folderDescription,
			@WebParam(name = "userId", partName = "userId") @QueryParam("userId") String userId) {
		try {
			Session session = establishSession(sessionid);
			String homeCollection = contentHostingService.getSiteCollection(siteId);

			ContentCollectionEdit collection = contentHostingService.addCollection(homeCollection + folderName);

			final ResourcePropertiesEdit resourceProperties = collection.getPropertiesEdit();

			resourceProperties.addProperty( ResourceProperties.PROP_DISPLAY_NAME, folderName);
			resourceProperties.addProperty( ResourceProperties.PROP_DESCRIPTION, folderDescription);
			resourceProperties.addProperty( ResourceProperties.PROP_CREATOR, userId);

			contentHostingService.commitCollection(collection);
		}
		catch (Exception e) {
			// catches IdUnusedException, TypeException
			// InconsistentException,  IdUsedException
			// IdInvalidException, PermissionException
			// InUseException

			LOG.error(e.getMessage() + " while attempting to create Presentations folder: "
					+ " for site: " + siteId + ". NOT CREATED... " + e.getMessage(), e);
			e.printStackTrace();
			return "error: " + e.getMessage();
		}
		return "success";

	}

	@WebMethod
	@Path("/longsightCreateFolderWithPath")
	@Produces("text/plain")
	@GET
	public String longsightCreateFolderWithPath(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "folderName", partName = "folderName") @QueryParam("folderName") String folderName,
			@WebParam(name = "folderPath", partName = "folderPath") @QueryParam("folderPath") String folderPath,
			@WebParam(name = "folderDescription", partName = "folderDescription") @QueryParam("folderDescription") String folderDescription,
			@WebParam(name = "userId", partName = "userId") @QueryParam("userId") String userId) {
		try {
			Session session = establishSession(sessionid);
			String homeCollection = contentHostingService.getSiteCollection(siteId);

			ContentCollectionEdit collection = contentHostingService.addCollection(homeCollection + folderPath + folderName);

			final ResourcePropertiesEdit resourceProperties = collection.getPropertiesEdit();

			resourceProperties.addProperty( ResourceProperties.PROP_DISPLAY_NAME, folderName);
			resourceProperties.addProperty( ResourceProperties.PROP_DESCRIPTION, folderDescription);
			resourceProperties.addProperty( ResourceProperties.PROP_CREATOR, userId);

			contentHostingService.commitCollection(collection);
		}
		catch (Exception e) {
			// catches IdUnusedException, TypeException
			// InconsistentException,  IdUsedException
			// IdInvalidException, PermissionException
			// InUseException

			LOG.error(e.getMessage() + " while attempting to create "+folderName+" folder: "
					+ " for site: " + siteId + ". NOT CREATED... " + e.getMessage(), e);
			e.printStackTrace();
			return "error: " + e.getMessage();
		}
		return "success";

	}

	@WebMethod
	@Path("/longsightRemoveAllResources")
	@Produces("text/plain")
	@GET
	public String longsightRemoveAllResources(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
		try {
			Session session = establishSession(sessionid);

			String homeCollection = contentHostingService.getSiteCollection(siteId);
			contentHostingService.removeCollection(homeCollection);
		}
		catch (Exception e) {
			e.printStackTrace();
			return "error: " + e.getMessage();
		}
		return "success";

	}

	@WebMethod
	@Path("/longsightRemoveFolder")
	@Produces("text/plain")
	@GET
	public String longsightRemoveFolder(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "folderName", partName = "folderName") @QueryParam("folderName") String folderName,
			@WebParam(name = "folderPath", partName = "folderPath") @QueryParam("folderPath") String folderPath) {
		try {
			Session session = establishSession(sessionid);

			String homeCollection = contentHostingService.getSiteCollection(siteId);
			contentHostingService.removeCollection(homeCollection+folderPath+folderName);
		}
		catch (Exception e) {
			e.printStackTrace();
			return "error: " + e.getMessage();
		}
		return "sucess";

	}

	@WebMethod
	@Path("/longsightAddResourceToFolder")
	@Produces("text/plain")
	@GET
	public boolean longsightAddResourceToFolder(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid, 
			@WebParam(name = "foldername", partName = "foldername") @QueryParam("foldername") String foldername, 
			@WebParam(name = "basename", partName = "basename") @QueryParam("basename") String basename, 
			@WebParam(name = "extension", partName = "extension") @QueryParam("extension") String extension, 
			@WebParam(name = "encodedstring", partName = "encodedstring") @QueryParam("encodedstring") String encodedstring, 
			@WebParam(name = "contenttype", partName = "contenttype") @QueryParam("contenttype") String contenttype, 
			@WebParam(name = "filesize", partName = "filesize") @QueryParam("filesize") String filesize) {
		Session session = establishSession(sessionid); 

		//LOG.warn("site: " + siteid + "; base: " + basename + "; extension: " + extension + "; contentype: " + contenttype + "; filesize: " + filesize);
		try
		{
			//LOG.warn(encodedstring);
			//String resourcestring = new String(Base64.decodeBase64(encodedstring.getBytes("UTF-8")));
			//InputStream is = new ByteArrayInputStream( resourcestring.getBytes() );

			InputStream is = new ByteArrayInputStream(Base64.decodeBase64(encodedstring.getBytes()));

			//String homeCollection = contentHostingService.getSiteCollection(siteid);
			//LOG.warn(homeCollection);

			final int idVariationLimit = 100; 

			// Method: create a resource, fill in its properties,
			// commit to officially save it
			ContentResourceEdit cr = null;

			// create the initial object
			cr = contentHostingService.addResource(foldername, basename, extension, idVariationLimit);

			// Add the actual contents of the file and content type
			cr.setContent(is);
			cr.setContentType(contenttype);

			// fill up its properties
			//final ResourcePropertiesEdit resourceProperties = cr.getPropertiesEdit();
			ResourcePropertiesEdit resourceProperties = contentHostingService.newResourceProperties();

			resourceProperties.addProperty(ResourceProperties.PROP_IS_COLLECTION, Boolean.FALSE.toString());
			resourceProperties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, basename + "." + extension);
			resourceProperties.addProperty(ResourceProperties.PROP_DESCRIPTION, basename + "." + extension);

			final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmssSSS");
			formatter.setTimeZone(timeService.getLocalTimeZone());

			// resourceProperties.addProperty(ResourceProperties.PROP_CREATION_DATE, formatter .format(displayDate));
			resourceProperties.addProperty(ResourceProperties.PROP_CONTENT_LENGTH, filesize);

			// now to commit the changes
			contentHostingService.commitResource(cr, NotificationService.NOTI_NONE);

			// add entry for event tracking
			//final Event event = EventTrackingService.newEvent(EVENT_ADD_PODCAST, getEventMessage(cr.getReference()), true, NotificationService.NOTI_NONE);
			//EventTrackingService.post(event);
			return true;

		}
		catch (IdUnusedException e) {
			LOG.error("IdUnusedException trying to add a resource to folder in Resources", e);
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			LOG.error("Error when adding resource to folder", e);
		}
		return false;
	}

	@WebMethod
	@Path("/longsightAddUrlToFolder")
	@Produces("text/plain")
	@GET
	public boolean longsightAddUrlToFolder(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid, 
			@WebParam(name = "foldername", partName = "foldername") @QueryParam("foldername") String foldername, 
			@WebParam(name = "basename", partName = "basename") @QueryParam("basename") String basename, 
			@WebParam(name = "encodedstring", partName = "encodedstring") @QueryParam("encodedstring") String encodedstring, 
			@WebParam(name = "filesize", partName = "filesize") @QueryParam("filesize") String filesize, 
			@WebParam(name = "description", partName = "description") @QueryParam("description") String description) {
		Session session = establishSession(sessionid);

		try
		{
			InputStream is = new ByteArrayInputStream(Base64.decodeBase64(encodedstring.getBytes()));

			final int idVariationLimit = 100;

			ContentResourceEdit cr = null;

			cr = contentHostingService.addResource(foldername, basename, "URL", idVariationLimit);

			// Add the actual contents of the file and content type
			cr.setContent(is);
			cr.setContentType("text/url");
			cr.setResourceType("org.sakaiproject.content.types.urlResource");

			// fill up its properties
			ResourcePropertiesEdit resourceProperties = cr.getPropertiesEdit();
			resourceProperties.addProperty(ResourceProperties.PROP_IS_COLLECTION, Boolean.FALSE.toString());
			resourceProperties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, basename);
			resourceProperties.addProperty(ResourceProperties.PROP_DESCRIPTION, description);

			final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmssSSS");
			formatter.setTimeZone(timeService.getLocalTimeZone());

			resourceProperties.addProperty(ResourceProperties.PROP_CONTENT_LENGTH, filesize);
			resourceProperties.addProperty(ResourceProperties.PROP_CONTENT_TYPE, ResourceProperties.TYPE_URL);

			contentHostingService.commitResource(cr, NotificationService.NOTI_NONE);
			return true;
		}
		catch (IdUnusedException e) {
			LOG.error("IdUnusedException trying to add a resource to folder in Resources", e);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			LOG.error("Error when adding resource to folder", e);
		}
		return false;
	}

	@WebMethod
	@Path("/longsightAddNewToolToPageIfNotExists")
	@Produces("text/plain")
	@GET
	public String longsightAddNewToolToPageIfNotExists(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "pagetitle", partName = "pagetitle") @QueryParam("pagetitle") String pagetitle,
			@WebParam(name = "tooltitle", partName = "tooltitle") @QueryParam("tooltitle") String tooltitle,
			@WebParam(name = "toolid", partName = "toolid") @QueryParam("toolid") String toolid,
			@WebParam(name = "oldToolId", partName = "oldToolId") @QueryParam("oldToolId") String oldToolId) 
	{
		Session session = establishSession(sessionid);

		try {
			boolean toolExists = false;
			Site siteEdit = siteService.getSite(siteid);
			List pageEdits = siteEdit.getPages();

			for (Iterator i = pageEdits.iterator(); i.hasNext();)
			{
				SitePage pageEdit = (SitePage) i.next();

				if (pageEdit.getTitle().equals(pagetitle))
				{
					List toolEdits = pageEdit.getTools();

					for (Iterator j = toolEdits.iterator(); j.hasNext();)
					{
						ToolConfiguration tool = (ToolConfiguration) j.next();
						Tool t = tool.getTool();

						if (t.getId().equals(oldToolId))
						{
							toolExists = true;
							LOG.warn(oldToolId + " already exists! Skipping add!");
							return "exists";
						}
					}

					ToolConfiguration toolTwo = pageEdit.addTool();
					Tool tt = toolTwo.getTool();

					toolTwo.setTool(toolid, toolManager.getTool(toolid));
					toolTwo.setTitle(tooltitle);
					toolExists = true;
				}

			}

			siteService.save(siteEdit);

			if (toolExists) {
				return "success";
			}
			else {
				return "nopage";
			}

		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();

		}

	}

	@WebMethod
	@Path("/longsightRemoveToolFromPage")
	@Produces("text/plain")
	@GET
	public String longsightRemoveToolFromPage(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "pagetitle", partName = "pagetitle") @QueryParam("pagetitle") String pagetitle,
			@WebParam(name = "toolid", partName = "toolid") @QueryParam("toolid") String toolid) 
	{
		Session session = establishSession(sessionid);
		boolean removedTool = false;

		try {
			Site siteEdit = siteService.getSite(siteid);
			List pageEdits = siteEdit.getPages();

			for (Iterator i = pageEdits.iterator(); i.hasNext();)
			{
				SitePage pageEdit = (SitePage) i.next();

				if (pageEdit.getTitle().equals(pagetitle))
				{
					List toolEdits = pageEdit.getTools();

					for (Iterator j = toolEdits.iterator(); j.hasNext();)
					{
						ToolConfiguration tool = (ToolConfiguration) j.next();
						Tool t = tool.getTool();

						boolean pleaseRemove = false;

						if (t.getId().equals(toolid))
						{
							pleaseRemove = true;
						}

						if (pleaseRemove) {
							pageEdit.removeTool(tool);
							removedTool = true;
						}
						break;
					}
				}

			}
			siteService.save(siteEdit);

			if (removedTool) {
				return "success";
			}
			else {
				return "fail";
			}
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

	}

	@WebMethod
	@Path("/longsightDoesPageExist")
	@Produces("text/plain")
	@GET
	public String longsightDoesPageExist(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "pagetitle", partName = "pagetitle") @QueryParam("pagetitle") String pagetitle) 
	{
		Session session = establishSession(sessionid);
		boolean pageExist = false;

		try {
			Site siteEdit = siteService.getSite(siteid);
			List pageEdits = siteEdit.getPages();

			for (Iterator i = pageEdits.iterator(); i.hasNext();)
			{
				SitePage pageEdit = (SitePage) i.next();

				if (pageEdit.getTitle().equals(pagetitle))
				{
					pageExist = true;
				}
			}
			return pageExist + "";
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

	}

	@WebMethod
	@Path("/longsightRenamePage")
	@Produces("text/plain")
	@GET
	public String longsightRenamePage(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "pagetitle", partName = "pagetitle") @QueryParam("pagetitle") String pagetitle,
			@WebParam(name = "newtitle", partName = "newtitle") @QueryParam("newtitle") String newtitle) 
	{
		Session session = establishSession(sessionid);

		try {
			Site siteEdit = siteService.getSite(siteid);
			List pageEdits = siteEdit.getPages();

			for (Iterator i = pageEdits.iterator(); i.hasNext();)
			{
				SitePage pageEdit = (SitePage) i.next();

				if (pageEdit.getTitle().equals(pagetitle))
				{
					pageEdit.setTitle(newtitle);
				}
			}

			siteService.save(siteEdit);
			return "sucess";
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

	}

	@WebMethod
	@Path("/longsightRemovePageFromSite")
	@Produces("text/plain")
	@GET
	public String longsightRemovePageFromSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "toolId", partName = "toolId") @QueryParam("toolId") String toolId) 
	{
		Session session = establishSession(sessionid);

		try {
			Site siteEdit = null;
			siteEdit = siteService.getSite(siteid);

			ToolConfiguration tool = siteEdit.getToolForCommonId(toolId);
			SitePage sitePage = tool.getContainingPage();

			sitePage.removeTool(tool);
			siteEdit.removePage(sitePage);

			siteService.save(siteEdit);
		}
		catch (Exception e) 
		{
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightGetUserType")
	@Produces("text/plain")
	@GET
	public String longsightGetUserType(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "userid", partName = "userid") @QueryParam("userid") String userid) 
	{
		Session session = establishSession(sessionid);

		try {
			User user = userDirectoryService.getUserByEid(userid);
			return user.getType();
		}
		catch(Exception e) {
			return "nouser";
		}

	}

	@WebMethod
	@Path("/longsightAddNewUser")
	@Produces("text/plain")
	@GET
	public String longsightAddNewUser(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid,
			@WebParam(name = "firstname", partName = "firstname") @QueryParam("firstname") String firstname,
			@WebParam(name = "lastname", partName = "lastname") @QueryParam("lastname") String lastname,
			@WebParam(name = "email", partName = "email") @QueryParam("email") String email,
			@WebParam(name = "type", partName = "type") @QueryParam("type") String type,
			@WebParam(name = "password", partName = "password") @QueryParam("password") String password) 
	{
		Session session = establishSession(sessionid);

		if (!securityService.isSuperUser())
		{
			LOG.warn("NonSuperUser trying to add accounts: " + session.getUserId());
			return "NonSuperUser trying to add accounts: " + session.getUserId();
		}
		try {

			User addeduser = null;
			addeduser = userDirectoryService.addUser(null, eid, firstname, lastname, email, password, type, null);

		}
		catch (Exception e) {
			LOG.warn("WS addNewUser(): " + e.getClass().getName() + " : " + e.getMessage());
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightUserExists")
	@Produces("text/plain")
	@GET
	public boolean longsightUserExists( String sessionid, String eid) 
	{
		Session session = establishSession(sessionid);

		try {
			UserEdit userEdit = null;
			String userid = userDirectoryService.getUserByEid(eid).getId();

			if (userid != null) {
				return true;
			}
			else {
				return false;
			}
		}
		catch (Exception e) {  
			return false;
		}

	}

	@WebMethod
	@Path("/longsightGetMembersForSite")
	@Produces("text/plain")
	@GET
	public String longsightGetMembersForSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) 
	{
		Session session = establishSession(sessionid);
		String instruct = "";

		try {
			Site site = siteService.getSite(siteid);
			Set users = site.getUsersHasRole("Instructor");

			for (Iterator i = users.iterator(); i.hasNext();)
			{
				String id = (String)i.next();
				String eid= userDirectoryService.getUser(id).getEid();
				instruct += eid + ",";
			}
		}
		catch(Exception ex) {
		}
		return instruct;
	}

	@WebMethod
	@Path("/longsightGetStudentsForSite")
	@Produces("text/plain")
	@GET
	public String longsightGetStudentsForSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) 
	{
		Session session = establishSession(sessionid);
		String instruct = "";

		try {
			Site site = siteService.getSite(siteid);
			Set users = site.getUsersHasRole("Student");

			for (Iterator i = users.iterator(); i.hasNext();)
			{
				try {
					String id = (String)i.next();
					String eid= userDirectoryService.getUser(id).getEid();
					String sortName = userDirectoryService.getUser(id).getSortName();
					instruct += id + ":" + eid + ":" + sortName + "|";
				} catch(UserNotDefinedException ux) {
					// necessary to skip phantom users without bailing
					continue;
				}
			}

		}
		catch(Exception ex) {
		}
		return instruct;
	}

	@WebMethod
	@Path("/longsightIsMember")
	@Produces("text/plain")
	@GET
	public boolean longsightIsMember(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid, 
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			String userid = userDirectoryService.getUserByEid(eid).getId();
			Member member = site.getMember(userid);
			String memberUserId =  member.getUserId();

			if (memberUserId != null && memberUserId.toLowerCase().equals(userid.toLowerCase())) {
				return true;
			}
			else {
				return false;
			}
		}
		catch(Exception ex) {
			return false;
		}

	}

	@WebMethod
	@Path("/longsightIsMemberById")
	@Produces("text/plain")
	@GET
	public boolean longsightIsMemberById(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid, 
			@WebParam(name = "id", partName = "id") @QueryParam("id") String id) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			String userid = userDirectoryService.getUser(id).getId();
			Member member = site.getMember(userid);
			String memberUserId =  member.getUserId();

			if (memberUserId != null && memberUserId.toLowerCase().equals(userid.toLowerCase())) {
				return true;
			}
			else {
				return false;
			}
		}
		catch(Exception ex) {
			return false;
		}
	}

	@WebMethod
	@Path("/longsightIsStudentOfSite")
	@Produces("text/plain")
	@GET
	public boolean longsightIsStudentOfSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid, 
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			String userid = userDirectoryService.getUserByEid(eid).getId();

			return site.hasRole(userid, "Student");
		}
		catch(Exception ex) {
			return false;
		}

	}

	@WebMethod
	@Path("/longsightIsStudentOfSite2")
	@Produces("text/plain")
	@GET
	public boolean longsightIsStudentOfSite2(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid, 
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			String userid = userDirectoryService.getUserByEid(eid).getId();
			Member member = site.getMember(userid);
			String memberUserId =  member.getUserId();
			if (memberUserId != null && memberUserId.toLowerCase().equals(userid.toLowerCase())) {
				return true;
			}
			else {
				return false;
			}
		}
		catch(Exception ex) {
			return false;
		}
	}

	@WebMethod
	@Path("/longsightIsInstructorOfSite")
	@Produces("text/plain")
	@GET
	public boolean longsightIsInstructorOfSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid, 
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			String userid = userDirectoryService.getUserByEid(eid).getId();

			return site.hasRole(userid, "Instructor");
		}
		catch(Exception ex) {
			return false;
		}

	}

	@WebMethod
	@Path("/longsightAllowFunctionForRole")
	@Produces("text/plain")
	@GET
	public String longsightAllowFunctionForRole(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "roleid", partName = "roleid") @QueryParam("roleid") String roleid,
			@WebParam(name = "functionname", partName = "functionname") @QueryParam("functionname") String functionname) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			Role role = site.getRole(roleid);

			role.allowFunction(functionname);
			siteService.save(site);
		}
		catch (Exception e) {  
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";

	}

	@WebMethod
	@Path("/longsightDisallowFunctionForRole")
	@Produces("text/plain")
	@GET
	public String longsightDisallowFunctionForRole(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "roleid", partName = "roleid") @QueryParam("roleid") String roleid,
			@WebParam(name = "functionname", partName = "functionname") @QueryParam("functionname") String functionname) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			Role role = site.getRole(roleid);

			role.disallowFunction(functionname);
			siteService.save(site);
		}
		catch (Exception e) {  
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";

	}

	@WebMethod
	@Path("/longsightRemoveMemberFromSite")
	@Produces("text/plain")
	@GET
	public String longsightRemoveMemberFromSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			String userid = userDirectoryService.getUserByEid(eid).getId();

			site.removeMember(userid);
			siteService.save(site);
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightAddGroupToSite")
	@Produces("text/plain")
	@GET
	public String longsightAddGroupToSite(
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "groupTitle", partName = "groupTitle") @QueryParam("groupTitle") String groupTitle,
			@WebParam(name = "groupDesc", partName = "groupDesc") @QueryParam("groupDesc") String groupDesc,
			@WebParam(name = "groupCategory", partName = "groupCategory") @QueryParam("groupCategory") String groupCategory) 
	{
		Session session = establishSession(sessionId);

		try
		{
			Site site = siteService.getSite(siteId);
			Group group = site.addGroup();

			group.setTitle(groupTitle);
			group.setDescription(groupDesc);

			LOG.warn("New section: " + groupTitle + "; descr: " + groupDesc + "; cate: " + groupCategory);

			ResourceProperties props = group.getProperties();

			props.addProperty("group_prop_wsetup_created", Boolean.TRUE.toString());
			props.addProperty("sections_category", groupCategory);
			props.addProperty(EXTERNALLY_MAINTAINED, Boolean.toString(false));
			props.addProperty(STUDENT_REGISTRATION_ALLOWED, Boolean.toString(false));
			props.addProperty(STUDENT_SWITCHING_ALLOWED, Boolean.toString(false));

			siteService.save(site);

			return group.getId();
		}
		catch (Exception e)
		{
			return "FAILURE: " + (e.toString());
		}

	}


	@WebMethod
	@Path("/longsightAddCalendarEvent")
	@Produces("text/plain")
	@GET
	public String longsightAddCalendarEvent(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "importstring", partName = "importstring") @QueryParam("importstring") String importstring) {

		//setup source and target calendar strings
		String calId = "/calendar/calendar/"+ siteid +"/main"; 
		CalendarEdit calendar = null;

		Session session = establishSession(sessionid); 
		try
		{
			calendar = CalendarService.editCalendar(calId); 
			InputStream is = new ByteArrayInputStream( importstring.getBytes() );

			Map columnMap = CalendarImporterService.getDefaultColumnMap(CalendarImporterService.CSV_IMPORT);
			String[] addFieldsCalendarArray = null;

			List eventsList = CalendarImporterService.doImport(
					CalendarImporterService.CSV_IMPORT,
					is,
					columnMap,
					addFieldsCalendarArray);

			for (Iterator i = eventsList.iterator(); i.hasNext();) { 

				CalendarEvent cEvent = (CalendarEvent) i.next(); 
				LOG.warn("new event: " + cEvent.getDisplayName());
				CalendarEventEdit cedit = calendar.addEvent(); 

				cedit.setRange(cEvent.getRange()); 
				cedit.setDisplayName(cEvent.getDisplayName()); 
				cedit.setDescription(cEvent.getDescription()); 
				cedit.setType(cEvent.getType()); 
				cedit.setLocation(cEvent.getLocation()); 
				cedit.setDescriptionFormatted(cEvent.getDescriptionFormatted()); 
				cedit.setRecurrenceRule(cEvent.getRecurrenceRule()); 

				calendar.commitEvent(cedit); 
			}

			CalendarService.commitCalendar(calendar); 

			return "success";
		}
		catch (IdUnusedException e)
		{
			LOG.warn("Unused calendar");
			return "unused";
		}
		catch (Exception e)
		{
			if (calendar != null) {
				CalendarService.cancelCalendar(calendar); 
			}
			LOG.warn("Calendar import failed", e);
			return "fail";
		}

	}


	@WebMethod
	@Path("/searchCalendarEvent")
	@Produces("text/plain")
	@GET
	public String searchCalendarEvent(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "searchString", partName = "searchString") @QueryParam("searchString") String searchString) {
		Session session = establishSession(sessionid);

		String searchResults = "";

		try {
			String calId = "/calendar/calendar/"+siteId+"/main";
			Calendar calendar = CalendarService.getCalendar(calId); 
			//get all the events on this site's calendar
			List<CalendarEvent> eventList = calendar.getEvents(null, null);

			searchResults += "{\"events\": [ ";

			for (CalendarEvent event : eventList) {
				if ("".equals(searchString) || event.getDisplayName().equals(searchString) ) {
					if (searchResults.equals("{\"events\": [ ")) {
						// this is the first record
					} else {
						// not the first record, so add a comma in fromt
						searchResults += ", ";
					}

					searchResults += "{ \"id\": \""+event.getId()+"\", \"title\": \""+event.getDisplayName()+"\", \"range\": \""+event.getRange().toStringHR()+"\", \"type\": \""+event.getType();
					searchResults += "\", \"location\": \""+event.getLocation()+"\", \"frequency\": \""+event.getRecurrenceRule().getFrequencyDescription()+"\", \"interval\": \"";
					searchResults += event.getRecurrenceRule().getInterval()+"\", \"until\": \""+event.getRecurrenceRule().getUntil().toStringLocalFull()+"\"}";
				}
			}

			searchResults += "]}";

		} catch (Exception e) {
			LOG.error("WS searchCalendarEvent(): error " + e.getClass().getName() + " : " + e.getMessage());
			return e.getClass().getName() + " : " + e.getMessage();	
		}

		return searchResults;
	}

	@WebMethod
	@Path("/getAllCalendarEvents")
	@Produces("text/plain")
	@GET
	public String getAllCalendarEvents(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
		Session session = establishSession(sessionid);

		String searchResults = "";

		try {
			String calId = "/calendar/calendar/"+siteId+"/main";
			Calendar calendar = CalendarService.getCalendar(calId); 
			//get all the events on this site's calendar
			List<CalendarEvent> eventList = calendar.getEvents(null, null);

			searchResults += "{\"events\": [ ";

			for (CalendarEvent event : eventList) {
				if (searchResults.equals("{\"events\": [ ")) {
					// this is the first record
				} else {
					// not the first record, so add a comma in fromt
					searchResults += ", ";
				}

				if (event.getRecurrenceRule() != null) {

					searchResults += "{ \"id\": \""+event.getId()+"\", \"title\": \""+event.getDisplayName()+"\", \"range\": \""+event.getRange().toStringHR()+"\", \"type\": \""+event.getType();
					searchResults += "\", \"location\": \""+event.getLocation()+"\", \"frequency\": \""+event.getRecurrenceRule().getFrequencyDescription()+"\", \"interval\": \"";
					searchResults += event.getRecurrenceRule().getInterval()+"\", \"until\": \""+event.getRecurrenceRule().getUntil().toStringLocalFull()+"\"}";

				} else {
					searchResults += "{ \"title\": \""+event.getDisplayName()+"\", \"range\": \""+event.getRange().toStringHR()+"\", \"typ     e\"     : \""+event.getType()+"\", \"location\": \""+event.getLocation()+"\" }";
				}
			}

			searchResults += "]}";

		} catch (Exception e) {
			LOG.error("WS getAllCalendarEvents(): error " + e.getClass().getName() + " : " + e.getMessage());
			return e.getClass().getName() + " : " + e.getMessage();	
		}

		return searchResults;
	}

	@WebMethod
	@Path("/createCalendarEvent")
	@Produces("text/plain")
	@GET
	public String createCalendarEvent(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eventTitle", partName = "eventTitle") @QueryParam("eventTitle") String eventTitle,
			@WebParam(name = "startdate", partName = "startdate") @QueryParam("startdate") String startdate,
			@WebParam(name = "enddate", partName = "enddate") @QueryParam("enddate") String enddate,
			@WebParam(name = "starttime", partName = "starttime") @QueryParam("starttime") String starttime,
			@WebParam(name = "endtime", partName = "endtime") @QueryParam("endtime") String endtime,
			@WebParam(name = "daysofweek", partName = "daysofweek") @QueryParam("daysofweek") String daysofweek,
			@WebParam(name = "location", partName = "location") @QueryParam("location") String location) { 

		Session session = establishSession(sessionid); 

		//setup source and target calendar strings
		String calId = "/calendar/calendar/"+siteId+"/main";
		LOG.error("calendar: "+calId);

		try { 
			//get calendar
			Calendar calendar = CalendarService.getCalendar(calId); 

			String startdateArr[] = startdate.split("-");
			String enddateArr[] = enddate.split("-");
			String starttimeArr[] = starttime.split(":");
			String endtimeArr[] = endtime.split(":");

			// Create the time range object, needed for adding the event to the calendar
			Time startTimeObj = timeService.newTimeLocal(
					Integer.parseInt(startdateArr[0]),
					Integer.parseInt(startdateArr[1]),
					Integer.parseInt(startdateArr[2]),
					Integer.parseInt(starttimeArr[0]),
					Integer.parseInt(starttimeArr[1]),
					Integer.parseInt(starttimeArr[2]),
					000);
			Time endTimeObj = timeService.newTimeLocal(
					Integer.parseInt(startdateArr[0]),
					Integer.parseInt(startdateArr[1]),
					Integer.parseInt(startdateArr[2]),
					Integer.parseInt(endtimeArr[0]),
					Integer.parseInt(endtimeArr[1]),
					Integer.parseInt(endtimeArr[2]),
					000);

			Time untilTimeObj = timeService.newTimeLocal(
					Integer.parseInt(enddateArr[0]),
					Integer.parseInt(enddateArr[1]),
					Integer.parseInt(enddateArr[2]),
					Integer.parseInt(endtimeArr[0]),
					Integer.parseInt(endtimeArr[1]),
					Integer.parseInt(endtimeArr[2]),
					000);

			HashMap dayMapping = new HashMap();
			dayMapping.put("m", "Mon");
			dayMapping.put("t", "Tue");
			dayMapping.put("w", "Wed");
			dayMapping.put("r", "Thu");
			dayMapping.put("f", "Fri");
			//dayMapping.put("s", "Sat"); // not sure what these would be in Banner
			//dayMapping.put("s", "Sun");

			String daysofweekArr[] = daysofweek.split("");
			for (String d : daysofweekArr) {

				if (!"".equals(d)) {

					//LOG.error("day: "+d);
					String tmp = startTimeObj.toStringRFC822Local();
					String startday = tmp.substring(0, tmp.indexOf(","));

					while (!startday.equals( dayMapping.get(d) )) {
						// we keep adding days to StartTimeObj until we get to the REAL start date.
						long oneDay = 86400000L;
						startTimeObj = timeService.newTime(startTimeObj.getTime()+oneDay);
						tmp = startTimeObj.toStringRFC822Local();
						startday = tmp.substring(0, tmp.indexOf(","));
					}

					//LOG.error(startday);

					TimeBreakdown tbd = startTimeObj.breakdownLocal();

					// set the endtime to the new day too.
					endTimeObj = timeService.newTimeLocal(
							tbd.getYear(),
							tbd.getMonth(),
							tbd.getDay(),
							Integer.parseInt(endtimeArr[0]),
							Integer.parseInt(endtimeArr[1]),
							Integer.parseInt(endtimeArr[2]),
							000);

					TimeRange range = timeService.newTimeRange(startTimeObj, endTimeObj, true, true);

					CalendarEventEdit cedit = calendar.addEvent(); 
					cedit.setRange(range); 
					cedit.setDisplayName(eventTitle); 
					cedit.setDescription(eventTitle);
					cedit.setDescriptionFormatted(eventTitle);
					cedit.setType("Class session");
					cedit.setLocation(location); 
					RecurrenceRule recurr = CalendarService.newRecurrence("week", 1, untilTimeObj);
					cedit.setRecurrenceRule(recurr); 

					calendar.commitEvent(cedit);

				}

			}

		} catch (Exception e) { 
			LOG.error("WS createCalendarEvent(): error " + e.getClass().getName() + " : " + e.getMessage()); 
			return e.getClass().getName() + " : " + e.getMessage(); 
		} 


		return "success"; 
	}

	@WebMethod
	@Path("/createGroupCalendarEvent")
	@Produces("text/plain")
	@GET
	public String createGroupCalendarEvent(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eventTitle", partName = "eventTitle") @QueryParam("eventTitle") String eventTitle,
			@WebParam(name = "startdate", partName = "startdate") @QueryParam("startdate") String startdate,
			@WebParam(name = "enddate", partName = "enddate") @QueryParam("enddate") String enddate,
			@WebParam(name = "starttime", partName = "starttime") @QueryParam("starttime") String starttime,
			@WebParam(name = "endtime", partName = "endtime") @QueryParam("endtime") String endtime,
			@WebParam(name = "groupId", partName = "groupId") @QueryParam("groupId") String groupId,
			@WebParam(name = "type", partName = "type") @QueryParam("type") String type) { 

		Session session = establishSession(sessionid); 

		String calId = "/calendar/calendar/"+siteId+"/main";

		try {

			Calendar calendar = CalendarService.getCalendar(calId); 

			String startdateArr[] = startdate.split("-");
			String enddateArr[] = enddate.split("-");
			String starttimeArr[] = starttime.split(":");
			String endtimeArr[] = endtime.split(":");

			Time startTimeObj = timeService.newTimeLocal(
					Integer.parseInt(startdateArr[0]),
					Integer.parseInt(startdateArr[1]),
					Integer.parseInt(startdateArr[2]),
					Integer.parseInt(starttimeArr[0]),
					Integer.parseInt(starttimeArr[1]),
					Integer.parseInt(starttimeArr[2]),
					000);
			Time endTimeObj = timeService.newTimeLocal(
					Integer.parseInt(startdateArr[0]),
					Integer.parseInt(startdateArr[1]),
					Integer.parseInt(startdateArr[2]),
					Integer.parseInt(endtimeArr[0]),
					Integer.parseInt(endtimeArr[1]),
					Integer.parseInt(endtimeArr[2]),
					000);

			TimeRange range = timeService.newTimeRange(startTimeObj, endTimeObj, true, true);

			Site site = siteService.getSite(siteId);
			Collection groups = new Vector();
			groups.add(site.getGroup(groupId));

			CalendarEventEdit cedit = calendar.addEvent();
			cedit.setRange(range); 
			cedit.setDisplayName(eventTitle); 
			cedit.setDescription(eventTitle);
			cedit.setDescriptionFormatted(eventTitle);
			cedit.setType(type);
			cedit.setGroupAccess(groups, true);

			calendar.commitEvent(cedit);

		} catch (Exception e) { 

			LOG.error("WS createGroupCalendarEvent(): error " + e.getClass().getName() + " : " + e.getMessage()); 
			return e.getClass().getName() + " : " + e.getMessage();

		}

		return "success";
	}

	@WebMethod
	@Path("/removeCalendarEvent")
	@Produces("text/plain")
	@GET
	public String removeCalendarEvent(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "eventid", partName = "eventid") @QueryParam("eventid") String eventid) {
		Session session = establishSession(sessionid);

		try {
			String calId = "/calendar/calendar/"+siteid+"/main";
			Calendar calendar = CalendarService.getCalendar(calId); 

			CalendarEventEdit calEventEdit = calendar.getEditEvent(eventid, EVENT_REMOVE_CALENDAR);

			calendar.removeEvent(calEventEdit);        

		} catch (Exception e) {
			LOG.error("WS updateCalendarEvent(): error " + e.getClass().getName() + " : " + e.getMessage()); 
			return e.getClass().getName() + " : " + e.getMessage(); 
		}

		return "success";
	}

	@WebMethod
	@Path("/getMemberStatus")
	@Produces("text/plain")
	@GET
	public String getMemberStatus(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) { 

		Session s = establishSession(sessionid); 

		String status = "";

		try {
			Site site = siteService.getSite(siteid);

			String userid = userDirectoryService.getUserByEid(eid).getId();
			Member membership = site.getMember(userid);

			if (membership.isActive()) {
				status = "active";
			} else {
				status = "inactive";
			}

		} catch (Exception e) {
			LOG.error("WS getMemberStatus(): "+ e.getClass().getName() + " : "+ e.getMessage());
			return "";
		}

		return status;
	}

	@WebMethod
	@Path("/setMemberStatus")
	@Produces("text/plain")
	@GET
	public String setMemberStatus(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid,
			@WebParam(name = "active", partName = "active") @QueryParam("active") boolean active) { 

		Session s = establishSession(sessionid); 

		try {
			AuthzGroup site = authzGroupService.getAuthzGroup("/site/"+siteid);

			String userid = userDirectoryService.getUserByEid(eid).getId();
			Member membership = site.getMember(userid);

			if (membership.isActive() && !active) {
				membership.setActive(false);
			} else if (!membership.isActive() && active) {
				membership.setActive(true);
			}
			authzGroupService.save(site);
			//siteService.save(site);

		} catch (Exception e) {
			LOG.error("WS setMemberStatus(): "+ e.getClass().getName() + " : "+ e.getMessage());
			return "";
		}

		return "success";
	}

	@WebMethod
	@Path("/copySiteWithProviderId")
	@Produces("text/plain")
	@GET
	public String copySiteWithProviderId(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteidtocopy", partName = "siteidtocopy") @QueryParam("siteidtocopy") String siteidtocopy,
			@WebParam(name = "newsiteid", partName = "newsiteid") @QueryParam("newsiteid") String newsiteid,
			@WebParam(name = "title", partName = "title") @QueryParam("title") String title,
			@WebParam(name = "description", partName = "description") @QueryParam("description") String description,
			@WebParam(name = "shortdesc", partName = "shortdesc") @QueryParam("shortdesc") String shortdesc,
			@WebParam(name = "iconurl", partName = "iconurl") @QueryParam("iconurl") String iconurl,
			@WebParam(name = "infourl", partName = "infourl") @QueryParam("infourl") String infourl,
			@WebParam(name = "joinable", partName = "joinable") @QueryParam("joinable") boolean joinable,
			@WebParam(name = "joinerrole", partName = "joinerrole") @QueryParam("joinerrole") String joinerrole,
			@WebParam(name = "published", partName = "published") @QueryParam("published") boolean published,
			@WebParam(name = "publicview", partName = "publicview") @QueryParam("publicview") boolean publicview,
			@WebParam(name = "skin", partName = "skin") @QueryParam("skin") String skin,
			@WebParam(name = "type", partName = "type") @QueryParam("type") String type,
			@WebParam(name = "providerid", partName = "providerid") @QueryParam("providerid") String providerid) 
	{
		Session session = establishSession(sessionid);
	
		try {
			Site site = siteService.getSite(siteidtocopy);
			Site siteEdit = siteService.addSite(newsiteid, site);
			siteEdit.setTitle(title);
			siteEdit.setDescription(description);
			siteEdit.setShortDescription(shortdesc);
			siteEdit.setIconUrl(iconurl);
			siteEdit.setInfoUrl(infourl);
			siteEdit.setJoinable(joinable);
			siteEdit.setJoinerRole(joinerrole);
			siteEdit.setPublished(published);
			siteEdit.setPubView(publicview);
			siteEdit.setSkin(skin);
			siteEdit.setType(type);
			siteEdit.setProviderGroupId(providerid);
			siteService.save(siteEdit);
		}
		catch (Exception e) {  
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightAddNewSiteWithProviderId")
	@Produces("text/plain")
	@GET
	public String longsightAddNewSiteWithProviderId(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "title", partName = "title") @QueryParam("title") String title,
			@WebParam(name = "description", partName = "description") @QueryParam("description") String description,
			@WebParam(name = "shortdesc", partName = "shortdesc") @QueryParam("shortdesc") String shortdesc,
			@WebParam(name = "iconurl", partName = "iconurl") @QueryParam("iconurl") String iconurl,
			@WebParam(name = "infourl", partName = "infourl") @QueryParam("infourl") String infourl,
			@WebParam(name = "joinable", partName = "joinable") @QueryParam("joinable") boolean joinable,
			@WebParam(name = "joinerrole", partName = "joinerrole") @QueryParam("joinerrole") String joinerrole,
			@WebParam(name = "published", partName = "published") @QueryParam("published") boolean published,
			@WebParam(name = "publicview", partName = "publicview") @QueryParam("publicview") boolean publicview,
			@WebParam(name = "skin", partName = "skin") @QueryParam("skin") String skin,
			@WebParam(name = "type", partName = "type") @QueryParam("type") String type,
			@WebParam(name = "providerid", partName = "providerid") @QueryParam("providerid") String providerid) 
	{
		Session session = establishSession(sessionid);

		try {

			Site siteEdit = null;
			siteEdit = siteService.addSite(siteid, type);
			siteEdit.setTitle(title);
			siteEdit.setDescription(description);
			siteEdit.setShortDescription(shortdesc);
			siteEdit.setIconUrl(iconurl);
			siteEdit.setInfoUrl(infourl);
			siteEdit.setJoinable(joinable);
			siteEdit.setJoinerRole(joinerrole);
			siteEdit.setPublished(published);
			siteEdit.setPubView(publicview);
			siteEdit.setSkin(skin);
			siteEdit.setType(type);
			siteEdit.setProviderGroupId(providerid);
			siteService.save(siteEdit);

		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightGetUserEmail")
	@Produces("text/plain")
	@GET
	public String longsightGetUserEmail(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "userid", partName = "userid") @QueryParam("userid") String userid) 
	{
		Session session = establishSession(sessionid);
		try {
			User user = userDirectoryService.getUserByEid(userid);
			return user.getEmail();
		} catch (Exception e) {
			LOG.warn("WS getUserEmail() failed for user: " + userid);
			return "";
		}

	}

	@WebMethod
	@Path("/longsightGetUserDisplayName")
	@Produces("text/plain")
	@GET
	public String longsightGetUserDisplayName(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "userid", partName = "userid") @QueryParam("userid") String userid) 
	{
		Session session = establishSession(sessionid);
		try {
			User user = userDirectoryService.getUserByEid(userid);
			return user.getDisplayName();
		} catch (Exception e) {
			LOG.warn("WS getUserDisplayName() failed for user: " + userid);
			return "";
		}

	}

	@WebMethod
	@Path("/archiveSite")
	@Produces("text/plain")
	@GET
	public String archiveSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) 
	{
		Session session = establishSession(sessionid);

		try {
			String msg = archiveService.archive(siteid);
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/addConfigPropertyToPage")
	@Produces("text/plain")
	@GET
	public String addConfigPropertyToPage(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "pagetitle", partName = "pagetitle") @QueryParam("pagetitle") String pagetitle,
			@WebParam(name = "propname", partName = "propname") @QueryParam("propname") String propname,
			@WebParam(name = "propvalue", partName = "propvalue") @QueryParam("propvalue") String propvalue) 
	{
		Session session = establishSession(sessionid);

		try {

			Site siteEdit = siteService.getSite(siteid);
			List pageEdits = siteEdit.getPages();
			for (Iterator i = pageEdits.iterator(); i.hasNext();)
			{
				SitePage pageEdit = (SitePage) i.next();
				if (pageEdit.getTitle().equals(pagetitle))
				{
					ResourcePropertiesEdit propsedit = pageEdit.getPropertiesEdit();
					propsedit.addProperty(propname, propvalue); // is_home_page = true
				}
			}
			siteService.save(siteEdit);

		}
		catch (Exception e) {
			LOG.error("WS addConfigPropertyToPage(): " + e.getClass().getName() + " : " + e.getMessage());
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/addInactiveMemberToSiteWithRole")
	@Produces("text/plain")
	@GET
	public String addInactiveMemberToSiteWithRole(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid,
			@WebParam(name = "roleid", partName = "roleid") @QueryParam("roleid") String roleid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			String userid = userDirectoryService.getUserByEid(eid).getId();
			site.addMember(userid,roleid,false,false);
			siteService.save(site);
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightChangeSiteDescriptions")
	@Produces("text/plain")
	@GET
	public String longsightChangeSiteDescriptions(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "description", partName = "description") @QueryParam("description") String description,
			@WebParam(name = "shortDescription", partName = "shortDescription") @QueryParam("shortDescription") String shortDescription) 
	{
		Session session = establishSession(sessionid);

		try {

			Site siteEdit = null;
			siteEdit = siteService.getSite(siteid);
			siteEdit.setDescription(description);
			siteEdit.setShortDescription(shortDescription);
			siteService.save(siteEdit);

		}
		catch (Exception e) {
			LOG.warn("WS changeSiteDescription(): " + e.getClass().getName() + " : " + e.getMessage());
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/changeUserFirstAndLastName")
	@Produces("text/plain")
	@GET
	public String changeUserFirstAndLastName(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid,
			@WebParam(name = "firstName", partName = "firstName") @QueryParam("firstName") String firstName,
			@WebParam(name = "lastName", partName = "lastName") @QueryParam("lastName") String lastName) 
	{
		Session session = establishSession(sessionid);

		try {

			UserEdit userEdit = null;
			String userid = userDirectoryService.getUserByEid(eid).getId();
			userEdit = userDirectoryService.editUser(userid);
			userEdit.setFirstName(firstName);
			userEdit.setLastName(lastName);
			userDirectoryService.commitEdit(userEdit);

		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/getUserFirstName")
	@Produces("text/plain")
	@GET
	public String getUserFirstName(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionid);
		try {
			User user = userDirectoryService.getUserByEid(eid);
			return user.getFirstName();
		} catch (Exception e) {
			LOG.error("WS getUserFirstName() failed for user: " + eid + " : " + e.getClass().getName() + " : " + e.getMessage());
			return "";
		}
	}

	@WebMethod
	@Path("/getUserLastName")
	@Produces("text/plain")
	@GET
	public String getUserLastName(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionid);
		try {
			User user = userDirectoryService.getUserByEid(eid);
			return user.getLastName();
		} catch (Exception e) {
			LOG.error("WS getUserLastName() failed for user: " + eid + " : " + e.getClass().getName() + " : " + e.getMessage());
			return "";
		}
	}

	@WebMethod
	@Path("/getSitesUserCanAccess")
	@Produces("text/plain")
	@GET
	public String getSitesUserCanAccess(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid)

	{
		Session s = establishSession(sessionid);

		try 
		{
			List allSites = siteService.getSites(SelectionType.ACCESS, null, null,
					null, SortType.TITLE_ASC, null);
			List moreSites = siteService.getSites(SelectionType.UPDATE, null, null,
					null, SortType.TITLE_ASC, null);

			if ( (allSites == null || moreSites == null) ||
					(allSites.size() == 0 && moreSites.size() == 0) )
				return "<list/>";

			// Remove duplicates and combine two lists
			allSites.removeAll( moreSites );
			allSites.addAll( moreSites );

			Document dom = Xml.createDocument();
			Node list = dom.createElement("list");
			dom.appendChild(list);

			for (Iterator i = allSites.iterator(); i.hasNext();)
			{
				Site site = (Site)i.next();
				Node item = dom.createElement("item");
				Node siteId = dom.createElement("siteId");
				siteId.appendChild( dom.createTextNode(site.getId()) );
				Node siteTitle = dom.createElement("siteTitle");
				siteTitle.appendChild( dom.createTextNode(site.getTitle()) );

				item.appendChild(siteId);
				item.appendChild(siteTitle);
				list.appendChild(item);
			}

			return Xml.writeDocumentToString(dom);
		}
		catch (Exception e) 
		{
			return "<exception/>";
		}
	}

	@WebMethod
	@Path("/getSitesUserCanAccessFilteredByTerm")
	@Produces("text/plain")
	@GET
	public String getSitesUserCanAccessFilteredByTerm(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "userid", partName = "userid") @QueryParam("userid") String userid,
			@WebParam(name = "termid", partName = "termid") @QueryParam("termid") String termid) {
		try {
			String newsessionid = longsightGetSessionForUser (sessionid, userid, true);
			newsessionid = StringUtils.substringBefore(newsessionid, ".");
			Session session = establishSession(newsessionid);
		}
		catch (Exception ee) {
			return "user not found";
		}


		try {
			Map propertyCriteria = new HashMap();
			propertyCriteria.put("term_eid", termid);

			List allSites = siteService.getSites(SelectionType.ACCESS, null, null, propertyCriteria, SortType.TITLE_ASC, null);
			List moreSites = siteService.getSites(SelectionType.UPDATE, null, null, propertyCriteria, SortType.TITLE_ASC, null);

			if ( (allSites == null || moreSites == null) || (allSites.size() == 0 && moreSites.size() == 0) ) {
				return "<list/>";
			}

			// Remove duplicates and combine two lists
			allSites.removeAll( moreSites );
			allSites.addAll( moreSites );

			Document dom = Xml.createDocument();
			Node list = dom.createElement("list");
			dom.appendChild(list);

			for (Iterator i = allSites.iterator(); i.hasNext();) {
				Site site = (Site)i.next();
				Node item = dom.createElement("item");
				Node siteId = dom.createElement("siteId");
				siteId.appendChild( dom.createTextNode(site.getId()) );
				Node siteTitle = dom.createElement("siteTitle");
				siteTitle.appendChild( dom.createTextNode(site.getTitle()) );

				item.appendChild(siteId);
				item.appendChild(siteTitle);                                                                                                                                                                                                          list.appendChild(item);                                                                                                                                                                                                       }

			return Xml.writeDocumentToString(dom);
		}
		catch (Exception e)
		{
			return "<exception/>";
		}
	}


	@WebMethod
	@Path("/getSitesUserCanAccessFilteredFromPreferences")
	@Produces("text/plain")
	@GET
	public String getSitesUserCanAccessFilteredFromPreferences(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid)

	{
		Session s = establishSession(sessionid);
		int prefTabs = 4;
		List prefExclude = new Vector();
		List prefOrder = new Vector();

		try 
		{
			try 
			{
				Preferences prefs = preferencesService.getPreferences(s.getUserId());
				ResourceProperties props = prefs.getProperties("sakai:portal:sitenav");
				prefTabs = (int) props.getLongProperty("tabs");

				List l = props.getPropertyList("exclude");
				if (l != null)
				{
					prefExclude = l;
				}

				l = props.getPropertyList("order");
				if (l != null)
				{
					prefOrder = l;
				}

				// the number of tabs to display
				int tabsToDisplay = prefTabs;
			}
			catch (Exception e2) {
				LOG.warn("Could not find preferences for this user");
			}

			List allSites = siteService.getSites(SelectionType.ACCESS, null, null,
					null, SortType.TITLE_ASC, null);
			List moreSites = siteService.getSites(SelectionType.UPDATE, null, null,
					null, SortType.TITLE_ASC, null);

			if ( (allSites == null || moreSites == null) ||
					(allSites.size() == 0 && moreSites.size() == 0) )
				return "<list/>";

			// Remove duplicates and combine two lists
			allSites.removeAll( moreSites );

			// remove all in exclude from mySites
			allSites.removeAll(prefExclude);

			allSites.addAll( moreSites );

			Document dom = Xml.createDocument();
			Node list = dom.createElement("list");
			dom.appendChild(list);

			for (Iterator i = allSites.iterator(); i.hasNext();)
			{
				Site site = (Site)i.next();
				Node item = dom.createElement("item");
				Node siteId = dom.createElement("siteId");
				siteId.appendChild( dom.createTextNode(site.getId()) );
				Node siteTitle = dom.createElement("siteTitle");
				siteTitle.appendChild( dom.createTextNode(site.getTitle()) );

				item.appendChild(siteId);
				item.appendChild(siteTitle);
				list.appendChild(item);
			}


			return Xml.writeDocumentToString(dom);
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			return "<exception/>";
		}
	}

	@WebMethod
	@Path("/longsightRemoveUserIdFromSite")
	@Produces("text/plain")
	@GET
	public String longsightRemoveUserIdFromSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "userid", partName = "userid") @QueryParam("userid") String userid) 
	{
		Session session = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);
			site.removeMember(userid);
			siteService.save(site);
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/longsightUserIdExists")
	@Produces("text/plain")
	@GET
	public boolean longsightUserIdExists(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid, 
			@WebParam(name = "id", partName = "id") @QueryParam("id") String id) 
	{
		Session session = establishSession(sessionid);

		try {

			UserEdit userEdit = null;
			String userid = userDirectoryService.getUser(id).getId();

			if (userid != null) {
				return true;
			}
			else {
				return false;
			}

		}
		catch (Exception e) {
			return false;
		}
	}

	@WebMethod
	@Path("/doesGroupExistInSite")
	@Produces("text/plain")
	@GET
	public boolean doesGroupExistInSite(String sessionid, String siteid, String grouptitle) 
	{
		Session s = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);

			for (Iterator iter = site.getGroups().iterator(); iter.hasNext();) {
				Group group = (Group) iter.next();
				if (group.getTitle().equals(grouptitle)) {
					return true;
				}
			}
		}
		catch (Exception e) {
			LOG.error("WS doesGroupExistInSite(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return false;
	}

	@WebMethod
	@Path("/getGroupIdInSite")
	@Produces("text/plain")
	@GET
	public String getGroupIdInSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "grouptitle", partName = "grouptitle") @QueryParam("grouptitle") String grouptitle) 
	{
		Session s = establishSession(sessionid);

		try {
			Site site = siteService.getSite(siteid);

			for (Iterator iter = site.getGroups().iterator(); iter.hasNext();) {
				Group group = (Group) iter.next();
				if (group.getTitle().equals(grouptitle)) {
					return group.getId();
				}
			}
		}
		catch (Exception e) {
			LOG.error("WS doesGroupExistInSite(): " + e.getClass().getName() + " : " + e.getMessage());
		}
		return "false";
	}

	@WebMethod
	@Path("/setContentResourceType")
	@Produces("text/plain")
	@GET
	public String setContentResourceType (
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "resourceid", partName = "resourceid") @QueryParam("resourceid") String resourceid,
			@WebParam(name = "resourcetype", partName = "resourcetype") @QueryParam("resourcetype") String resourcetype) 
	{
		Session session = establishSession(sessionid);

		try {
			LOG.warn("editing resource: " + resourceid);
			ContentResourceEdit cre = contentHostingService.editResource(resourceid);
			if (resourcetype.equals("url")) {
				cre.setResourceType(ResourceType.TYPE_URL);
			}
			else {
				cre.setResourceType(ResourceType.TYPE_UPLOAD);
			}
			contentHostingService.commitResource(cre);
		}
		catch (Exception e) {
			LOG.error("WS setContentResourceType(): " + e.getClass().getName() + " : " + e.getMessage());
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/changeUserEidById")
	@Produces("text/plain")
	@GET
	public String changeUserEidById(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "id", partName = "id") @QueryParam("id") String id,
			@WebParam(name = "neweid", partName = "neweid") @QueryParam("neweid") String neweid) 
	{
		Session session = establishSession(sessionid);

		try {

			UserEdit userEdit = null;
			String userid = userDirectoryService.getUser(id).getId();
			userEdit = userDirectoryService.editUser(userid);
			userEdit.setEid(neweid);
			userDirectoryService.commitEdit(userEdit);

		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
		return "success";
	}

	@WebMethod
	@Path("/getSynMsgcntr")
	@Produces("text/plain")
	@GET
	public String getSynMsgcntr(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		establishSession(sessionid);
		Map<String, Integer> count = new HashMap<String, Integer>();

		Document dom = Xml.createDocument();
		Node msgcntr = dom.createElement("msgcntr");
		dom.appendChild(msgcntr);

		String retVal = "";
		String userId = "";

		try{ 
			userId = userDirectoryService.getUserByEid(eid).getId();
		} catch (Exception e) {
			return "Error: User not defined.";
		}

		List<SynopticMsgcntrItem> synItems;
		SynopticMsgcntrManager synopticMsgcntrManager = SynopticMsgcntrManagerCover.getInstance();
		synItems = synopticMsgcntrManager.getWorkspaceSynopticMsgcntrItems(userId);

		Preferences prefs = preferencesService.getPreferences(userId);
		ResourceProperties props = prefs.getProperties("sakai:portal:sitenav");
		List<String> orderedSites = props.getPropertyList("order");

		List<String> excludedSites = props.getPropertyList("exclude");

		if(excludedSites != null){
			//user has set preferences so filter out any missing sites:
			for (Iterator iterator = synItems.iterator(); iterator.hasNext();) {
				SynopticMsgcntrItem synItem = (SynopticMsgcntrItem) iterator.next();
				if(excludedSites.contains(synItem.getSiteId())){
					iterator.remove();
				}
			}
		}

		for (SynopticMsgcntrItem synopticMsgcntrItem : synItems) {
			String synopticSiteId = synopticMsgcntrItem.getSiteId();
			// synopticMsgcntrItem.isHideItem() lets us know if the site is hidden in the synoptic tool
			if(synopticSiteId != null && !"".equals(synopticSiteId) && !synopticMsgcntrItem.isHideItem()){

				String mUrl = "";
				String fUrl = "";

				try {
					Site site = siteService.getSite(synopticSiteId);

					// serverConfigurationService.getPortalUrl() was returning http://localhost/Apache-Axis, so I have removed it for now
					// instead you should put in http://sakai/portal in it's place.

					ToolConfiguration mTool = site.getToolForCommonId("sakai.messages");
					if (mTool == null) {
						mTool = site.getToolForCommonId("sakai.messagecenter");
						if (mTool != null) {
							mUrl = "/directtool/" + mTool.getId() + "/sakai.messageforums.helper.helper/main";
						}
					} else {
						mUrl = "/directtool/" + mTool.getId() + "/sakai.messageforums.helper.helper/privateMsg/pvtMsgHpView";
					}

					ToolConfiguration fTool = site.getToolForCommonId("sakai.forums");
					if (fTool != null) {
						fUrl = "/directtool/" + fTool.getId() + "/sakai.messageforums.helper.helper/discussionForum/forumsOnly/dfForums";
					}

				} catch (Exception e) {
					return "Error: Cannot get Site for siteid, url cannot be generated."+e.toString();
				}


				Node messageNode = dom.createElement("msgcntrList");
				msgcntr.appendChild(messageNode);

				Node idNode = dom.createElement("siteid");
				messageNode.appendChild(idNode);
				idNode.appendChild(dom.createTextNode(synopticSiteId));

				Node titleNode = dom.createElement("sitetitle");
				messageNode.appendChild(titleNode);
				titleNode.appendChild(dom.createTextNode(synopticMsgcntrItem.getSiteTitle()));

				Node fCountNode = dom.createElement("forumCount");
				messageNode.appendChild(fCountNode);
				fCountNode.appendChild(dom.createTextNode(String.valueOf(synopticMsgcntrItem.getNewForumCount() )));

				Node mCountNode = dom.createElement("messageCount");
				messageNode.appendChild(mCountNode);
				mCountNode.appendChild(dom.createTextNode(String.valueOf(synopticMsgcntrItem.getNewMessagesCount())));

				Node fLink = dom.createElement("forumUrl");
				messageNode.appendChild(fLink);
				fLink.appendChild(dom.createTextNode(fUrl));

				Node mLink = dom.createElement("messageUrl");
				messageNode.appendChild(mLink);
				mLink.appendChild(dom.createTextNode(mUrl));

			}

		}

		retVal = Xml.writeDocumentToString(dom);

		return retVal;
	}

	@WebMethod
	@Path("/longsightAddMemberToGroup")
	@Produces("text/plain")
	@GET
	public Boolean longsightAddMemberToGroup( String sessionId, String siteId, String groupId, String eid ) 
	{
		Session session = establishSession(sessionId);
		try
		{
			Site site = siteService.getSite(siteId);
			Group group = site.getGroup(groupId);
			if ( group == null )
				return false;

			String userId = userDirectoryService.getUserByEid(eid).getId();

			Role r = site.getUserRole(userId);
			Member m = site.getMember(userId);
			group.addMember(userId, r != null ? r.getId()   : "",
					m != null ? m.isActive() : true,   false);
			siteService.save(site);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	@WebMethod
	@Path("/longsightGetFoldersInSite")
	@Produces("text/plain")
	@GET
	public String longsightGetFoldersInSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
		try {
			Session session = establishSession(sessionid);
			String homeCollectionString = contentHostingService.getSiteCollection(siteId);
			ContentCollection homeCollection = contentHostingService.getCollection(homeCollectionString);
			List memberIds = homeCollection.getMembers();
			Iterator it = memberIds.iterator();

			String concat = "";
			while(it.hasNext()) {
				String memberId = (String) it.next();
				concat += memberId;

				if (it.hasNext()) {
					concat += "$";
				}
			}
			return concat;

		}
		catch (Exception e) {
			LOG.error(e.getMessage() + " could not retrieve folders for siteID: " + siteId);
			e.printStackTrace();
			return "false";
			//return "error: " + e.getMessage();
		}

	}

	@WebMethod
	@Path("/longsightHideFolder")
	@Produces("text/plain")
	@GET
	public String longsightHideFolder(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "folderString", partName = "folderString") @QueryParam("folderString") String folderString) {
		try {
			Session session = establishSession(sessionid);
			ContentCollectionEdit collection = contentHostingService.editCollection(folderString);
			collection.setHidden();
			contentHostingService.commitCollection(collection);
			return "success";
		}
		catch (Exception e) {
			e.printStackTrace();
			return "false";
		}

	}


	@WebMethod
	@Path("/longsightImportFromFile")
	@Produces("text/plain")
	@GET
	public String longsightImportFromFile(
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "filename", partName = "filename") @QueryParam("filename") String filename) 
	{
		Session session = establishSession(sessionId);

		if (!securityService.isSuperUser()) {
			LOG.warn("WS getCourseGrades(): Permission denied. Restricted to super users.");
			return "FAILURE: getCourseGrades(): Permission denied. Restricted to super users.";
		}

		try {
			FileInputStream file = new FileInputStream(filename);
			byte[] bytes = new byte[file.available()];
			file.read(bytes);
			file.close();

			ResetOnCloseInputStream inputStream = new ResetOnCloseInputStream(new BufferedInputStream(file));

			if (importService.isValidArchive(inputStream)) {
				ImportDataSource importDataSource = importService.parseFromFile(inputStream);
				LOG.info("Getting import items from manifest.");
				List lst = importDataSource.getItemCategories();
				if (lst != null && lst.size() > 0) {
					importService.doImportItems(importDataSource.getItemsForCategories(lst), siteId);
					return "success";
				}
			}
			return "failure";
		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

	}

	@WebMethod
	@Path("/longsightGetRoleForSite")
	@Produces("text/plain")
	@GET
	public String longsightGetRoleForSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "rolename", partName = "rolename") @QueryParam("rolename") String rolename) 
	{
		Session session = establishSession(sessionid);
		String instruct = "";

		try {
			Site site = siteService.getSite(siteid);
			Set users = site.getUsersHasRole(rolename);

			for (Iterator i = users.iterator(); i.hasNext();)
			{
				String id = (String)i.next();
				String eid = "";
				try {
					eid= userDirectoryService.getUser(id).getEid();
				}
				catch (UserNotDefinedException ue) {
					LOG.warn("Could not find user " + id);
					continue;
				}
				String sortName = userDirectoryService.getUser(id).getSortName();
				instruct += id + ":" + eid + ":" + sortName + "|";
			}

		}
		catch(Exception ex) {
		}
		return instruct;
	}

	@WebMethod
	@Path("/longsightGetSessionForUser")
	@Produces("text/plain")
	@GET
	public String longsightGetSessionForUser(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid,
			@WebParam(name = "wsonly", partName = "wsonly") @QueryParam("wsonly") boolean wsonly) {

		Session session = establishSession(sessionid);

		// find what server we are on
		String serverId = serverConfigurationService.getString("serverId");

		//check that ONLY super user's are accessing this
		if(!securityService.isSuperUser(session.getUserId())) {
			LOG.warn("WS getSessionForUser(): Permission denied. Restricted to super users.");
			return "FAILURE: getSessionForUser(): Permission denied. Restricted to super users.";
		}

		try {

			//check for empty userid
			if (StringUtils.isBlank(eid)) {
				LOG.warn("WS getSessionForUser() failed. Param eid empty.");
				return "FAILURE: failed. Param eid empty.";
			}

			//if dealing with web service sessions, re-use is ok
			if(wsonly) {
				//do we already have a web service session for the given user? If so, reuse it.
				List<Session> existingSessions = sessionManager.getSessions();
				for(Session existingSession: existingSessions){
					if(StringUtils.equals(existingSession.getUserEid(), eid)) {

						//check if the origin attribute, if set, is set for web services
						String origin = (String)existingSession.getAttribute(SESSION_ATTR_NAME_ORIGIN);
						if(StringUtils.equals(origin, SESSION_ATTR_VALUE_ORIGIN_WS)) {
							LOG.warn("WS getSessionForUser() reusing existing session for: " + eid + ", session=" + existingSession.getId());
							return existingSession.getId() + "." + serverId;
						}
					}
				}
			}

			//get ip address for establishing session
			String ipAddress = getUserIp();

			//start a new session
			Session newsession = sessionManager.startSession();
			sessionManager.setCurrentSession(newsession);

			//inject this session with new user values
			User user = userDirectoryService.getUserByEid(eid);
			newsession.setUserEid(eid);
			newsession.setUserId(user.getId());

			//if wsonly, inject the origin attribute
			if(wsonly) {
				newsession.setAttribute(SESSION_ATTR_NAME_ORIGIN, SESSION_ATTR_VALUE_ORIGIN_WS);
				LOG.warn("WS getSessionForUser() set origin attribute on session: " + newsession.getId());
			}

			//register the session with presence
			UsageSession usagesession = usageSessionService.startSession(user.getId(),ipAddress,"SakaiScript.jws getSessionForUser()");

			// update the user's externally provided realm definitions
			authzGroupService.refreshUser(user.getId());

			// post the login event
			eventTrackingService.post(eventTrackingService.newEvent("user.login", null, true));

			if (newsession == null){
				LOG.warn("WS getSessionForUser() failed. Unable to establish session for userid=" + eid + ", ipAddress=" + ipAddress);
				return "FAILURE: failed. Unable to establish session";
			} else {
				LOG.warn("WS getSessionForUser() OK. Established session for userid=" + eid + ", session=" + newsession.getId() + ", ipAddress=" + ipAddress);
				return newsession.getId() + "." + serverId;
			}
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
	}

	@WebMethod
	@Path("/longsightAddSyllabusRedirect")
	@Produces("text/plain")
	@GET
	public String longsightAddSyllabusRedirect(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "userid", partName = "userid") @QueryParam("userid") String userid,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
			@WebParam(name = "url", partName = "url") @QueryParam("url") String url) {

		Session session = establishSession(sessionid); 

		SyllabusItem syllabusItem;

		try {
			syllabusItem = syllabusManager.getSyllabusItemByContextId(siteid);

			if (syllabusItem == null) {
				syllabusItem = syllabusManager.createSyllabusItem(userid, siteid, url);
			}
			else {
				syllabusItem.setRedirectURL(url);
			}

			syllabusManager.saveSyllabusItem(syllabusItem);
			return "success";
		}
		catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}
	}

	@WebMethod
	@Path("/getGroupsForMember")
	@Produces("text/plain")
	@GET
	public String getGroupsForMember(
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId,
			@WebParam(name = "eid", partName = "eid") @QueryParam("eid") String eid) 
	{
		Session session = establishSession(sessionId);

		String returnGroups = "";

		try
		{
			Site site = siteService.getSite(siteId);
			ArrayList<Group> groups = new ArrayList<Group>(site.getGroups());

			Iterator i1 = groups.iterator();
			while (i1.hasNext()) {
				Group group = (Group) i1.next();
				Set<Member> members = group.getMembers();
				Iterator i2 = members.iterator();
				while (i2.hasNext()) {
					Member member = (Member) i2.next();
					if (member.getUserEid().equals(eid)) {
						returnGroups = returnGroups+group.getTitle()+":";
					}
				}
			}

			return returnGroups;
		}
		catch (Exception e)
		{
			return "FAILURE: " + e.toString();
		}
	}

	@WebMethod
	@Path("/removeMemberFromGroup")
	@Produces("text/plain")
	@GET
	public Boolean removeMemberFromGroup( String sessionId, String siteId, String groupId, String eid ) 
	{
		Session session = establishSession(sessionId);
		try
		{
			Site site = siteService.getSite(siteId);
			Group group = site.getGroup(groupId);
			if ( group == null )
				return false;

			String userId = userDirectoryService.getUserByEid(eid).getId();

			Role r = site.getUserRole(userId);
			Member m = site.getMember(userId);
			group.removeMember(userId);
			siteService.save(site);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	@WebMethod
	@Path("/deleteAllMyWorkspaceSites")
	@Produces("text/plain")
	@GET
	public String deleteAllMyWorkspaceSites(
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId) {
		try{
			Session session = establishSession(sessionId);
			if (securityService.isSuperUser()) {
				//get special users
				String config = serverConfigurationService.getString("webservice.specialUsers", "admin,postmaster");
				String[] items = StringUtils.split(config, ',');
				List<String> specialUsers = Arrays.asList(items);

				//now get all users
				List<String> allUsers = new ArrayList<String>();
				List<Map<String, String>> returnList = dbRead("SELECT user_id FROM SAKAI_USER_ID_MAP", new String[]{}, new String[]{"user_id"});
				for(Map<String, String> map : returnList){
					allUsers.add(map.get("user_id"));
				}

				//remove special users
				allUsers.removeAll(specialUsers);

				//now add a page to each site, and the tool to that page
				for (Iterator j = allUsers.iterator(); j.hasNext();) { 
					String userid = StringUtils.trim((String)j.next());

					LOG.info("deleteAllMyWorkspaceSites: processing user:" + userid);

					String myWorkspaceId = siteService.getUserSiteId(userid);

					Site siteEdit = null;

					try {
						siteEdit = siteService.getSite(myWorkspaceId);
						siteService.removeSite(siteEdit);
					} catch (IdUnusedException e) {
						LOG.info("No workspace for user: " + myWorkspaceId + ", skipping...");
						continue;
					}
				}
				return "success";
			}else{
				return "FAILURE: to deleteAllMyWorkspaceSites is restricted to super admins";
			}
		}catch(Exception e){
			return "FAILURE: " + e.toString();
		}
	}

	@WebMethod
	@Path("/findDeletedTest")
	@Produces("text/plain")
	@GET
	public Object findDeletedTest(String sessionId, String siteId) {
		try{

			Session session = establishSession(sessionId);
			if (securityService.isSuperUser()) {

				String DELETED_TEST_SQL = "Select spt.ID, spt.TITLE, spt.DESCRIPTION, spt.CREATEDBY, spt.CREATEDDATE, spt.LASTMODIFIEDBY, spt.LASTMODIFIEDDATE " +
						"from SAM_PUBLISHEDASSESSMENT_T spt left join SAM_AUTHZDATA_T sat on sat.QUALIFIERID = spt.ID " + 
						"where (sat.AGENTID = ? or sat.AGENTID = (Select TITLE from SAKAI_SITE where SITE_ID = ?) " +
						"or sat.AGENTID in (select GROUP_ID from SAKAI_SITE_GROUP where SITE_ID = ?) ) and spt.STATUS = 2 group by spt.ID" ;
				String SUBMISSIONS_SQL = "select count(*) as SUB_COUNT " +
						"from (select ASSESSMENTGRADINGID from SAM_ASSESSMENTGRADING_T " +
						"where PUBLISHEDASSESSMENTID = ? " +
						"and SUBMITTEDDATE is not null group by AGENTID) counttable";

				List<Map<String, String>> returnList = dbRead(DELETED_TEST_SQL, new String[]{siteId, siteId, siteId}, new String[]{"ID", "TITLE", "DESCRIPTION", "CREATEDBY", "CREATEDDATE", "LASTMODIFIEDBY", "LASTMODIFIEDDATE"});

				for(Map<String, String> map : returnList){
					List<Map<String, String>> returnList2 = dbRead(SUBMISSIONS_SQL, new String[]{map.get("ID")}, new String[]{"SUB_COUNT"});
					if(returnList2 != null && returnList2.size() == 1){
						map.put("SUBMISSIONS", returnList2.get(0).get("SUB_COUNT"));
					}
				}
				return returnList;
			}else{
				return "FAILURE: to findDeletedTest is restricted to super admins";
			}
		}catch(Exception e){
			return "FAILURE: " + e.toString();
		}
	}

	@WebMethod
	@Path("/recoverDeletedTest")
	@Produces("text/plain")
	@GET
	public void recoverDeletedTest(String sessionId, String publishedAssessmentId) {
		try{
			Session session = establishSession(sessionId);
			if (securityService.isSuperUser()) {
				String SQL = "Update SAM_PUBLISHEDASSESSMENT_T Set STATUS = 1 where ID = ? limit 1";
				dbUpdate(SQL, new String[]{publishedAssessmentId});
			}else{
				LOG.warn("Access to recoverDeletedTest is restricted to super admins");
			}
		}catch(Exception e){
			LOG.warn("FAILURE: " + e.toString());
		}
	}

	@WebMethod
	@Path("/changeUserEidForce")
	@Produces("text/plain")
	@GET    	
	public String changeUserEidForce(
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "currentEid", partName = "currentEid") @QueryParam("currentEid") String currentEid,
			@WebParam(name = "newEid", partName = "newEid") @QueryParam("newEid") String newEid,
			@WebParam(name = "force", partName = "force") @QueryParam("force") boolean force) {
		try{
			Session session = establishSession(sessionId);
			if (securityService.isSuperUser()) {
				// make sure it is lower case when going into db
				newEid = newEid.toLowerCase();

				String COUNT_SQL = "select count(*) c from SAKAI_USER_ID_MAP where EID = ?";
				List<Map<String, String>> existingEidList = dbRead(COUNT_SQL, new String[]{newEid}, new String[]{"c"});
				boolean existingNewEid = existingEidList != null && existingEidList.size() > 0 && !"0".equals(existingEidList.get(0).get("c"));
				if(existingNewEid && !force){
					return "A user with eid : " + newEid + " already exists.";
				}else{
					if(existingNewEid && force){
						//drop the old new eid (probably user already signed in and created a blank user)
						String DELETE_SQL = "Delete from SAKAI_USER_ID_MAP where EID = ?";
						int success = dbUpdate(DELETE_SQL, new String[]{newEid});
						if(success <= 0){
							return "Failed to delete existing new eid: " + newEid;
						}
					}
					String UPDATE_SQL = "update SAKAI_USER_ID_MAP set EID = ? where EID = ?";
					int success = dbUpdate(UPDATE_SQL, new String[]{newEid, currentEid});
					if(success > 0){
						// Need to clear the id - eid cache
						boolean clearedCache = clearCache(ID_EID_CACHE);
						LOG.info("Cache cleared because of eid update: " + ID_EID_CACHE + ":" + clearedCache);
						return "Successfully updated eid: " + currentEid + " to eid: " + newEid + ";cacheCleared=" + clearedCache;
					}else{
						return "Update failed for changing from eid: " + currentEid + " to eid: " + newEid;
					}
				}

			}else{
				return "FAILURE: Access to recoverDeletedTest is restricted to super admins";
			}
		}catch(Exception e){
			return "FAILURE: " + e.toString();
		}
	}

	private Map transferCopyEntities(String toolId, String fromContext, String toContext) {

		Map transversalMap = new HashMap();

		// offer to all EntityProducers
		for (Iterator i = EntityManager.getEntityProducers().iterator(); i.hasNext();) {
			EntityProducer ep = (EntityProducer) i.next();
			if (ep instanceof EntityTransferrer) {
				try {
					EntityTransferrer et = (EntityTransferrer) ep;

					// if this producer claims this tool id
					if (ArrayUtil.contains(et.myToolIds(), toolId)) {
						if(ep instanceof EntityTransferrerRefMigrator){
							EntityTransferrerRefMigrator etMp = (EntityTransferrerRefMigrator) ep;
							Map<String,String> entityMap = etMp.transferCopyEntitiesRefMigrator(fromContext, toContext,
									new Vector());
							if(entityMap != null){                                                   
								transversalMap.putAll(entityMap);
							}
						}else{
							et.transferCopyEntities(fromContext, toContext, new Vector());
						}
					}
				} catch (Throwable t) {
					LOG.warn(this + ".transferCopyEntities: Error encountered while asking EntityTransfer to transferCopyEntities from: "
							+ fromContext + " to: " + toContext, t);
				}
			}
		}

		return transversalMap;
	}

	private void updateEntityReferences(String toolId, String toContext, Map transversalMap, Site newSite) {
		for (Iterator i = EntityManager.getEntityProducers().iterator(); i.hasNext();) {
			EntityProducer ep = (EntityProducer) i.next();
			if (ep instanceof EntityTransferrerRefMigrator && ep instanceof EntityTransferrer) {
				try {
					EntityTransferrer et = (EntityTransferrer) ep;
					EntityTransferrerRefMigrator etRM = (EntityTransferrerRefMigrator) ep;

					// if this producer claims this tool id
					if (ArrayUtil.contains(et.myToolIds(), toolId)) {
						etRM.updateEntityReferences(toContext, transversalMap);
					}
				} catch (Throwable t) {
					LOG.warn(
							"Error encountered while asking EntityTransfer to updateEntityRefere     nces at site: "
									+ toContext, t);
				}
			}
		}
	}

	@WebMethod
	@Path("/duplicateSite")
	@Produces("text/plain")
	@GET
	public String duplicateSite(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteidtocopy", partName = "siteidtocopy") @QueryParam("siteidtocopy") String siteidtocopy,
			@WebParam(name = "newsiteid", partName = "newsiteid") @QueryParam("newsiteid") String newsiteid,
			@WebParam(name = "title", partName = "title") @QueryParam("title") String title,
			@WebParam(name = "description", partName = "description") @QueryParam("description") String description,
			@WebParam(name = "shortdesc", partName = "shortdesc") @QueryParam("shortdesc") String shortdesc,
			@WebParam(name = "iconurl", partName = "iconurl") @QueryParam("iconurl") String iconurl,
			@WebParam(name = "infourl", partName = "infourl") @QueryParam("infourl") String infourl,
			@WebParam(name = "joinable", partName = "joinable") @QueryParam("joinable") boolean joinable,
			@WebParam(name = "joinerrole", partName = "joinerrole") @QueryParam("joinerrole") String joinerrole,
			@WebParam(name = "published", partName = "published") @QueryParam("published") boolean published,
			@WebParam(name = "publicview", partName = "publicview") @QueryParam("publicview") boolean publicview,
			@WebParam(name = "skin", partName = "skin") @QueryParam("skin") String skin,
			@WebParam(name = "type", partName = "type") @QueryParam("type") String type) 
	{
		Session session = establishSession(sessionid);

		try {

			Site site = siteService.getSite(siteidtocopy);

			Site siteEdit = siteService.addSite(newsiteid, site);
			siteEdit.setTitle(title);
			siteEdit.setJoinable(joinable);
			siteEdit.setJoinerRole(joinerrole);
			siteEdit.setPublished(published);
			siteEdit.setPubView(publicview);
			siteService.save(siteEdit);


			String nSiteId = siteEdit.getId();

			List pageList = site.getPages();
			Set<String> toolsCopied = new HashSet<String>();

			Map transversalMap = new HashMap();

			if (!((pageList == null) || (pageList.size() == 0))) {
				for (ListIterator i = pageList.listIterator(); i.hasNext();) {
					SitePage page = (SitePage) i.next();

					List pageToolList = page.getTools();
					if (!(pageToolList == null || pageToolList.size() == 0))
					{

						Tool tool = ((ToolConfiguration) pageToolList.get(0)).getTool();
						String toolId = tool != null?tool.getId():"";
						LOG.warn("ZZ: " + toolId);
						if (toolId.equalsIgnoreCase("sakai.resources")) {
							// handle
							// resource
							// tool
							// specially
							Map<String,String> entityMap = transferCopyEntities(
									toolId,
									contentHostingService.getSiteCollection(siteidtocopy),
									contentHostingService.getSiteCollection(nSiteId));
							if(entityMap != null){                                                   
								transversalMap.putAll(entityMap);
							}
						}
						else {
							// other
							// tools
							// SAK-19686 - added if statement and toolsCopied.add
							if (!toolsCopied.contains(toolId)) {
								Map<String,String> entityMap = transferCopyEntities(toolId, siteidtocopy, nSiteId);
								if(entityMap != null){                                                   
									transversalMap.putAll(entityMap);
								}
								toolsCopied.add(toolId);
							}
						}
					}
				}

				//update entity references
				toolsCopied = new HashSet<String>();
				for (ListIterator i = pageList
						.listIterator(); i.hasNext();) {
					SitePage page = (SitePage) i.next();

					List pageToolList = page.getTools();
					if (!(pageToolList == null || pageToolList.size() == 0))
					{                                       
						Tool tool = ((ToolConfiguration) pageToolList.get(0)).getTool();
						String toolId = tool != null?tool.getId():"";

						updateEntityReferences(toolId, nSiteId, transversalMap, site);
					}
				}
			}
		}
		catch (Exception e) {  
			LOG.error("WS copySite(): " + e.getClass().getName() + " : " + e.getMessage());
			return e.getClass().getName() + " : " + e.getMessage();
		}

		return "success";
	}


	@WebMethod
	@Path("/getAssignmentPointsPossible")
	@Produces("text/plain")
	@GET
	public String getAssignmentPointsPossible(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "gradebookUid", partName = "gradebookUid") @QueryParam("gradebookUid") String gradebookUid,
			@WebParam(name = "assignmentName", partName = "assignmentName") @QueryParam("assignmentName") String assignmentName) 
	{
		Session s = establishSession(sessionid);
		String retval = "";

		try {
			if (gradebookService == null) {
				return "Cannot get Gradebook service!";
			}

			Assignment a1 = gradebookService.getAssignment(gradebookUid, assignmentName);
			if (a1 == null) {
				LOG.warn("getAssignmentPointsPossible() aGradebookService.getAssignment() is null!");
			}
			retval = retval+a1.getPoints();
		} catch (Exception e) {
			return e.getClass().getName() + " : " + e.getMessage();
		}

		return retval;
	}

	@WebMethod
	@Path("/longsightTestWebservices")
	@Produces("text/plain")
	@GET
	public String longsightTestWebservices (
			@WebParam(name = "a", partName = "a") @QueryParam("a") String a,
			@WebParam(name = "b", partName = "b") @QueryParam("b") String b) {
		if (a != null && b != null && a.equals(b)) {
			return "success";                                                                                                                                                                                                             }
		else {
			return "failure";
		}
	}

	@WebMethod
	@Path("/longsightCleanupOrphanedFiles")
	@Produces("text/plain")
	@GET
	public String longsightCleanupOrphanedFiles(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "siteId", partName = "siteId") @QueryParam("siteId") String siteId) {
		try {
			Session session = establishSession(sessionid);

			// main resources
			String homeCollection = contentHostingService.getSiteCollection(siteId);
			try {
				contentHostingService.removeCollection(homeCollection);
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}

			// first the dropbox
			String groupUserCollection = contentHostingService.getDropboxCollection(siteId);
			try {
				contentHostingService.removeCollection(groupUserCollection);
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}

			// second try melete private area
			String meleteDocs = StringUtils.replace (groupUserCollection, "/group-user/", "/private/meleteDocs/");
			try {
				contentHostingService.removeCollection(meleteDocs);
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}

			// third attachments in various tools
			String attachments = StringUtils.replace (groupUserCollection, "/group-user/", "/attachment/");

			try {
				contentHostingService.removeCollection(attachments + "Forums/");
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}

			try {
				contentHostingService.removeCollection(attachments + "Assignments/");
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}

			try {
				contentHostingService.removeCollection(attachments + "Messages/");
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}

			try {
				contentHostingService.removeCollection(attachments + "Syllabus/");
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}

			try {
				contentHostingService.removeCollection(attachments + "Announcements/");
			} catch (IdUnusedException e) {
				LOG.warn("Empty collection " + e.getMessage());
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			return "error: " + e.getMessage();
		}
		return "success";
	}


	@WebMethod
	@Path("/getUserEid")
	@Produces("text/plain")
	@GET
	public String getUserEid(
			@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
			@WebParam(name = "userId", partName = "userId") @QueryParam("userId") String userId) 
	{
		Session session = establishSession(sessionid);
		String eid = "";
		try {
			eid = userDirectoryService.getUserEid(userId);
		} catch (Exception e) {
			LOG.warn("Could not find user "+userId);
		}
		return eid;
	}

	@WebMethod
	@Path("/addNewUser")
	@Produces("text/plain")
	@GET
	public String updateMembershipWithProvider (
			@WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
			@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) 
	{
		try {

			Session s = establishSession(sessionId);

			AuthzGroup realmEdit = authzGroupService.getAuthzGroup("/site/"+siteid);
			authzGroupService.save(realmEdit);

			AuthzGroup realm = authzGroupService.getAuthzGroup("/site/"+siteid);

			return "success";
		} catch (Exception e) {
			return "error";
		} 

	} 


	@WebMethod(exclude = true)
	private boolean clearCache(String cacheName) {
		Cache cache = memoryService.getCache(cacheName);
		cache.removeAll();
		return true;
	}

	@WebMethod(exclude = true)
	private int dbUpdate(String SQL, String[] params){
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try{
			connection = sqlService.borrowConnection();
			ps = connection.prepareStatement(SQL);
			int i = 1;
			for(String param : params){
				ps.setString(i, param);
				i++;
			}
			return ps.executeUpdate();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(ps != null){
				try{
					ps.close();
				}catch(Exception e){}
			}
			if(rs != null){
				try{
					rs.close();
				}catch(Exception e){}
			}
			sqlService.returnConnection(connection);
		}

		return 0;
	}

	@WebMethod(exclude = true)
	private List<Map<String, String>> dbRead(String SQL, String[] params, String[] cols){
		List<Map<String, String>> returnList = new ArrayList<Map<String, String>>();	
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		try{
			connection = sqlService.borrowConnection();
			ps = connection.prepareStatement(SQL);
			int i = 1;
			for(String param : params){
				ps.setString(i, param);
				i++;
			}
			rs = ps.executeQuery();

			if(rs != null){
				while(rs.next()){
					Map<String, String> resultMap = new HashMap<String, String>();
					for(String col : cols){
						resultMap.put(col, rs.getString(col));
					}
					returnList.add(resultMap);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(ps != null){
				try{
					ps.close();
				}catch(Exception e){}
			}
			if(rs != null){
				try{
					rs.close();
				}catch(Exception e){}
			}
			sqlService.returnConnection(connection);
		}

		return returnList;
	}


}

