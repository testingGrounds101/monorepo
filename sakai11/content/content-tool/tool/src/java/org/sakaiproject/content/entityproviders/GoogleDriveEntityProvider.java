package org.sakaiproject.content.entityproviders;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.content.googledrive.GoogleClient;
import org.sakaiproject.content.tool.RequestParams;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.EntityProvider;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.*;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.entityprovider.extension.RequestGetter;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.UserDirectoryService;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class GoogleDriveEntityProvider extends AbstractEntityProvider implements EntityProvider, AutoRegisterEntityProvider, ActionsExecutable, Outputable, Describeable, RequestAware {

	public final static String ENTITY_PREFIX = "google-drive";

	public static final String DRIVE_MODE_RECENT = "recent";
	public static final String DRIVE_MODE_MY_DRIVE = "home";
	public static final String DRIVE_MODE_STARRED = "starred";

	public static final String AUTH_MODE_SEND_TO_GOOGLE = "send_to_google";
	public static final String AUTH_MODE_HANDLE = "handle";
	public static final String AUTH_MODE_RESET = "reset";

	@Override
	public String getEntityPrefix() {
		return ENTITY_PREFIX;
	}

	@Override
	public String[] getHandledOutputFormats() {
		return new String[]{Formats.JSON};
	}

	@EntityCustomAction(action = "reset-oauth", viewKey = EntityView.VIEW_LIST)
	public void resetOauthCredential(EntityView view, Map<String, Object> params) {
		try {
			GoogleClient google = new GoogleClient();
			google.deleteCredential();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@EntityCustomAction(action = "handle-google-auth", viewKey = EntityView.VIEW_LIST)
	public void handleGoogleLogin(EntityView view, Map<String, Object> params) {
		HttpServletRequest request = requestGetter.getRequest();
		HttpServletResponse response = requestGetter.getResponse();

		try {
			GoogleClient google = new GoogleClient();
			GoogleAuthorizationCodeFlow flow = google.getAuthFlow();

			GoogleTokenResponse googleResponse = flow.newTokenRequest((String) params.get("code"))
				.setRedirectUri(GoogleClient.getRedirectURL())
				.execute();

			flow.createAndStoreCredential(googleResponse, GoogleClient.getCurrentGoogleUser());

			String state = (String) params.get("state");

			response.sendRedirect(state);
		} catch  (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private RequestGetter requestGetter;

	public void setRequestGetter(RequestGetter requestGetter) {
		this.requestGetter = requestGetter;
	}

	@Setter
	private SiteService siteService;

	@Setter
	private ToolManager toolManager;

	@Setter
	private SecurityService securityService;

	@Setter
	private UserDirectoryService userDirectoryService;
}
