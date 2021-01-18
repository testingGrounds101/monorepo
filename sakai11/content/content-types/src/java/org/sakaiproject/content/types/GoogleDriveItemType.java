/**********************************************************************************
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2006, 2007, 2008 Sakai Foundation
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

package org.sakaiproject.content.types;

import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.content.api.ResourceToolAction;
import org.sakaiproject.content.api.ResourceToolAction.ActionType;
import org.sakaiproject.content.api.ResourceType;
import org.sakaiproject.content.util.BaseInteractionAction;
import org.sakaiproject.content.util.BaseResourceAction.Localizer;
import org.sakaiproject.content.util.BaseResourceType;
import org.sakaiproject.content.util.BaseServiceLevelAction;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.user.api.User;
import org.sakaiproject.util.Resource;
import org.sakaiproject.util.ResourceLoader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.sakaiproject.content.api.ResourceToolAction.*;

import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.exception.IdUnusedException;

public class GoogleDriveItemType extends BaseResourceType 
{
	protected String typeId = ResourceType.TYPE_GOOGLE_DRIVE_ITEM;
	protected String helperId = "sakai.resource.type.helper";
	
	private static final String DEFAULT_RESOURCECLASS = "org.sakaiproject.localization.util.TypeProperties";
	private static final String DEFAULT_RESOURCEBUNDLE = "org.sakaiproject.localization.bundle.type.types";
	private static final String RESOURCECLASS = "resource.class.type";
	private static final String RESOURCEBUNDLE = "resource.bundle.type";
	private ServerConfigurationService serverConfigurationService =  (ServerConfigurationService) ComponentManager.get("org.sakaiproject.component.api.ServerConfigurationService");;
	private String resourceClass = serverConfigurationService.getString(RESOURCECLASS, DEFAULT_RESOURCECLASS);
	private String resourceBundle = serverConfigurationService.getString(RESOURCEBUNDLE, DEFAULT_RESOURCEBUNDLE);
	private ResourceLoader rb = new Resource().getLoader(resourceClass, resourceBundle);

	protected EnumMap<ActionType, List<ResourceToolAction>> actionMap = new EnumMap<ActionType, List<ResourceToolAction>>(ActionType.class);

	protected Map<String, ResourceToolAction> actions = new HashMap<String, ResourceToolAction>();	


	private Localizer localizer(final String string) {
		return new Localizer() {

			public String getLabel() {
				return rb.getString(string);
			}
			
		};
	}

	public GoogleDriveItemType() {
		actions.put(CREATE, new BaseInteractionAction(CREATE, ActionType.NEW_GOOGLE_DRIVE_ITEMS, typeId, helperId, localizer("create.googledriveitems")));
		actions.put(DELETE, new MoveToTrashAction(DELETE, ActionType.DELETE, typeId, true, localizer("action.delete")));

		for(ActionType type : ActionType.values())
		{
			actionMap.put(type, new ArrayList<ResourceToolAction>());
		}

		// for each action in actions, add a link in actionMap
		Iterator<String> it = actions.keySet().iterator();
		while(it.hasNext())
		{
			String id = it.next();
			ResourceToolAction action = actions.get(id);
			List<ResourceToolAction> list = actionMap.get(action.getActionType());
			if(list == null)
			{
				list = new ArrayList<ResourceToolAction>();
				actionMap.put(action.getActionType(), list);
			}
			list.add(action);
		}
		
	}

	public ResourceToolAction getAction(String actionId) 
	{
		return actions.get(actionId);
	}

	public List<ResourceToolAction> getActions(Reference entityRef, Set permissions) 
	{
		// TODO: use entityRef to filter actions
		List<ResourceToolAction> rv = new ArrayList<ResourceToolAction>();
		rv.addAll(actions.values());
		return rv;
	}

	public List<ResourceToolAction> getActions(Reference entityRef, User user, Set permissions) 
	{
		// TODO: use entityRef and user to filter actions
		List<ResourceToolAction> rv = new ArrayList<ResourceToolAction>();
		rv.addAll(actions.values());
		return rv;
	}

	public String getIconClass(ContentEntity entity)
	{
		ResourceProperties properties = entity.getProperties();
		if (properties.get("google-mime-type") != null) {
			return getIconClassForGoogleMimeType((String) properties.get("google-mime-type"));
		}

		return null;
	}

	public String getIconLocation(ContentEntity entity) 
	{
		return null;
	}

	public String getId() 
	{
		return typeId;
	}

	public String getLabel() 
	{
		return rb.getString("type.googledriveitem");
	}

	public String getLocalizedHoverText(ContentEntity member)
	{
		return rb.getString("type.googledriveitem");
	}

	public List<ResourceToolAction> getActions(ActionType type)
	{
		if (ActionType.NEW_GOOGLE_DRIVE_ITEMS.equals(type)) {
			boolean showGoogleDrive = false;

			if (ToolManager.getCurrentPlacement() == null) {
				return new ArrayList<>();
			}

			try {
				Site site = SiteService.getSite(ToolManager.getCurrentPlacement().getContext());
				showGoogleDrive = (site != null && "true".equals(site.getProperties().get("google-drive-enabled")));
			} catch (org.sakaiproject.exception.IdUnusedException missing) {}

			if (!showGoogleDrive) {
				return new ArrayList<>();
			}
		}

		List<ResourceToolAction> list = actionMap.get(type);
		if(list == null)
		{
			list = new ArrayList<ResourceToolAction>();
			actionMap.put(type, list);
		}
		return new ArrayList<ResourceToolAction>(list);
	}

	public List<ResourceToolAction> getActions(List<ActionType> types)
	{
		List<ResourceToolAction> list = new ArrayList<ResourceToolAction>();
		if(types != null)
		{
			Iterator<ActionType> it = types.iterator();
			while(it.hasNext())
			{
				ActionType type = it.next();
				List<ResourceToolAction> sublist = actionMap.get(type);
				if(sublist == null)
				{
					sublist = new ArrayList<ResourceToolAction>();
					actionMap.put(type, sublist);
				}
				list.addAll(sublist);
			}
		}
		return list;
	}

	@Override
	public boolean hasRightsDialog()
    {
		return false;
    }

	private String getIconClassForGoogleMimeType(String googleMimeType) {
		String googleMimeTypeCSSClass = googleMimeType.replaceAll("[^A-Za-z0-9]", "-");
		return "sakai-google-file-icon " + googleMimeTypeCSSClass;
	}

	class MoveToTrashAction extends BaseServiceLevelAction {
		private ContentHostingService chs = null;

		public MoveToTrashAction(String id, ActionType actionType, String typeId, boolean multipleItemAction, Localizer localizer) {
			super(id, actionType, typeId, multipleItemAction, localizer);

			chs = (ContentHostingService) ComponentManager.get("org.sakaiproject.content.api.ContentHostingService");
		}

		public void finalizeAction(Reference reference)
		{
			super.finalizeAction(reference);

			// FIXME Drop all sakai group and google permissions
			ContentResourceEdit resourceEdit = null;
			try {
				resourceEdit = chs.editResource(reference.getId());
				resourceEdit.clearRoleAccess();
				resourceEdit.setHidden();
				chs.commitResource(resourceEdit);
			} catch (Exception e) {
				if (resourceEdit != null) {
					chs.cancelResource(resourceEdit);
				}
				throw new RuntimeException(e);
			}
		}
	}
}
