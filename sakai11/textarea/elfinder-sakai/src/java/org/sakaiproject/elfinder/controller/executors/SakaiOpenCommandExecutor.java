package org.sakaiproject.elfinder.controller.executors;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;

import org.sakaiproject.elfinder.sakai.SakaiFsService;
import org.sakaiproject.elfinder.sakai.content.ContentSiteVolumeFactory;

import cn.bluejoe.elfinder.controller.executors.OpenCommandExecutor;
import cn.bluejoe.elfinder.service.FsService;
import cn.bluejoe.elfinder.service.FsItem;
import cn.bluejoe.elfinder.service.FsVolume;

import java.lang.reflect.Method;

public class SakaiOpenCommandExecutor extends OpenCommandExecutor
{
	@Override
	public void execute(FsService fsService, HttpServletRequest request, ServletContext servletContext, JSONObject json) throws Exception {
		if (fsService instanceof SakaiFsService) {
			SakaiFsService sakaiService = (SakaiFsService)fsService;

			// Always null out the thread local!
			sakaiService.setCurrentSite(null);

			FsItem item = sakaiService.fromHash(request.getParameter("target"));

			FsVolume volume = item.getVolume();

			try {
				Method getSiteIdMethod = volume.getClass().getMethod("getSiteId");

				if (getSiteIdMethod != null) {
					sakaiService.setCurrentSite((String)getSiteIdMethod.invoke(volume));
				}
			} catch (Exception e) {
				// If we can get a site off this volume, do so.  Otherwise, skip.
				System.err.println(String.format("Failed to set current site.  Volume was: %s", volume));
			}
		}


		super.execute(fsService, request, servletContext, json);
	}
}
