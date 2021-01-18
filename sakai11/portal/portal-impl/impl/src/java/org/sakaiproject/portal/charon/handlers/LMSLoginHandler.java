/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008 The Sakai Foundation
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

package org.sakaiproject.portal.charon.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.tool.api.Session;

import org.sakaiproject.tool.cover.ActiveToolManager;
import org.sakaiproject.tool.api.ActiveTool;

import org.sakaiproject.component.cover.HotReloadConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class LMSLoginHandler extends BasePortalHandler
{
	private static final Logger log = LoggerFactory.getLogger(LMSLoginHandler.class);
	private static final String defaultUrlFragment = String.format("<LMSLoginHandlerFragmentUnset:%s>", UUID.randomUUID());

	public static String lmsUrlFragment() {
		String fragment = HotReloadConfigurationService.getString("lms-login.fragment", "");

		if (fragment == null || "".equals(fragment)) {
			return defaultUrlFragment;
		}

		return fragment;
	}

	@Override
	public String getUrlFragment()
	{
		return LMSLoginHandler.lmsUrlFragment();
	}

	@Override
	public void setUrlFragment(String urlFragment)
	{
		log.warn("Refusing to override URL fragment for LMS login");
	}

	@Override
	public int doPost(String[] parts, HttpServletRequest req, HttpServletResponse res,
		Session session) throws PortalHandlerException
	{
		if ((parts.length == 2) && (parts[1].equals(getUrlFragment())) && isIPAcceptable(req))
		{
			String eid = req.getParameter("eid");

			if (isAcceptable(req, eid)) {
				log.info("Accepting direct login from user: " + eid);
			} else {
				log.info("Rejecting attempted login from user: " + eid);
				throw new PortalHandlerException("Access denied");
			}

			try {
				portal.doLogin(req, res, session, "", false);
				return END;
			} catch (Exception ex) {
				throw new PortalHandlerException(ex);
			}
		} else {
			return NEXT;
		}
	}


	@Override
	public int doGet(String[] parts, HttpServletRequest req, HttpServletResponse res,
		Session session) throws PortalHandlerException
	{
		if ((parts.length == 2) && (parts[1].equals(getUrlFragment())) && isIPAcceptable(req))
		{
			try
			{
				ActiveTool tool = ActiveToolManager.getActiveTool("sakai.login");
				tool.help(req,
					  res,
					  req.getContextPath() + req.getServletPath() + "/" + getUrlFragment(),
					  "/" + getUrlFragment());


				return END;
			}
			catch (Exception ex)
			{
				throw new PortalHandlerException(ex);
			}
		}
		else
		{
			return NEXT;
		}
	}

	private boolean isIPAcceptable(HttpServletRequest req) {
		String allowedIPs = HotReloadConfigurationService.getString("lms-login.ip-whitelist-regex", "");
		String ip = req.getRemoteAddr();

		boolean result = (!allowedIPs.isEmpty() && ip.matches(allowedIPs));

		if (result) {
			log.info("LMSLogin from IP {} accepted", ip);
		} else {
			log.info("LMSLogin from IP {} not accepted", ip);
		}

		return result;
	}

	private boolean isAcceptable(HttpServletRequest req, String eid) {
		if (eid == null || "".equals(eid)) {
			return false;
		}

		String allowedString = HotReloadConfigurationService.getString("lms-login.allowed-direct-logins", "");
		String allowedIPs = HotReloadConfigurationService.getString("lms-login.ip-whitelist-regex", "");

		String ip = req.getRemoteAddr();

		if (!isIPAcceptable(req)) {
			log.info("Blocking LMSLogin attempt from IP {} for user: {} (IP mismatch)", ip, eid);
			return false;
		}

		for (String allowed : allowedString.split(" *, *")) {
			if (eid.equals(allowed)) {
				log.info("Accepting LMSLogin attempt from IP {} for user: {}", ip, eid);
				return true;
			}
		}

		log.info("Blocking LMSLogin attempt from IP {} for user: {} (user mismatch)", ip, eid);
		return false;
	}
}
