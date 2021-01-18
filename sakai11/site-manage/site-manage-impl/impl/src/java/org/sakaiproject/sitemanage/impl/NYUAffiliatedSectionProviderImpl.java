/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2007, 2008 The Sakai Foundation
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
package org.sakaiproject.sitemanage.impl;

import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.sitemanage.api.AffiliatedSectionProvider;
import org.sakaiproject.db.api.SqlService;


public class NYUAffiliatedSectionProviderImpl implements AffiliatedSectionProvider {

	private static final Log log = LogFactory.getLog(AffiliatedSectionProviderImpl.class);
	private SqlService sqlService = (SqlService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.db.api.SqlService");


	public List getAffiliatedSectionEids(String userEid, String academicSessionEid)	{

		log.info("NYUAffiliatedSectionProviderImpl looking up user '" +
				userEid + "' for academic session '" + academicSessionEid + "'");

		ArrayList<String> result = new ArrayList<String>();

		Connection db = null;

		try {
			db = sqlService.borrowConnection();

			PreparedStatement ps = db.prepareStatement("select stem_name " +
					"from NYU_V_COURSE_ADMINS " +
					"where lower(acad_session_eid) = ? AND lower(netid) = ?");

			ps.setString(1, academicSessionEid.toLowerCase());
			ps.setString(2, userEid.toLowerCase());

			ResultSet rs = ps.executeQuery();
			try {
				while (rs.next()) {
					String sectionEid = rs.getString(1).replace(":", "_");
					result.add(sectionEid);
				}
			} finally {
				rs.close();
			}

		} catch (SQLException e) {
			log.warn(this + ".getAffiliatedSectionEids: " + e);
		} finally {
			if (db != null) {
				sqlService.returnConnection(db);
			}
		}
		return result;
	}


	public void init() {
		log.info("Loading NYUAffiliatedSectionProviderImpl");
	}

	public void destroy() {
		log.info("Destroying NYUAffiliatedSectionProviderImpl");
	}
}
