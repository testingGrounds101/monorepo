/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2007 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.wicket.markup.html.link;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Page;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

public class NavIntraLink extends BookmarkablePageLabeledLink {

	private static final long serialVersionUID = 1L;

	public NavIntraLink(String id, IModel model, Class pageClass) {
		super(id, model, pageClass);

		setAutoEnable(true);
		setBeforeDisabledLink("");
		setAfterDisabledLink("");
		add(new AttributeModifier("style", true, new Model("margin-right: 1em")));
	}

	public boolean linksTo(final Page page)
	{
		return page.getClass() == getPageClass();
	}
	
}
