/**********************************************************************************
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package edu.nyu.classes.providers;

import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPAttribute;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPEntry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.sakaiproject.unboundid.EntryAttributeToUserTypeMapper;
import org.sakaiproject.unboundid.LdapAttributeMapper;

/**
 * Extends EntryAttributeToUserTypeMapper to look to the database for overrides
 * to individual user types.  Allows users to be given certain roles even if
 * they're not in LDAP.
 *
 * Expects a table (or view) equivalent to:
 *
 *   create table nyu_v_user_type_overrides (netid varchar(50) primary key, type varchar(50));
 *
 * @author Mark Triggs <mark.triggs@nyu.edu>
 */
public class NYUUserTypeMapper extends EntryAttributeToUserTypeMapper {

	protected static Log M_log = LogFactory.getLog(NYUUserTypeMapper.class);


	private class NYUUserTypeOverrideCache implements Runnable {
		private AtomicReference<Map<String, String>> overrideMap;
		private Thread updateThread;
		private List<String> userTypesInAscendingOrder;


		public NYUUserTypeOverrideCache() {
			overrideMap = new AtomicReference<Map<String, String>>(null);
			updateThread = new Thread(this);

			String[] userTypeOrdering = ServerConfigurationService.getStrings("nyu.user-type-cache.user-types-in-ascending-importance");
			if (userTypeOrdering == null) {
				throw new RuntimeException("nyu.user-type-cache.user-types-in-ascending-importance not set");
			}

			userTypesInAscendingOrder = new ArrayList<String>();

			for (String type : userTypeOrdering) {
				userTypesInAscendingOrder.add(type.toLowerCase());
			}

			doUpdate();
			updateThread.start();
		}


		public void run() {
			int delay = ServerConfigurationService.getInt("nyu.user-type-cache.refresh-ms", 60000);

			while (true) {
				doUpdate();
				try { Thread.sleep(delay); } catch (Exception e) {}
			}
		}


		public String getTypeForUser(String netid, String defaultValue) {
			String result = overrideMap.get().get(netid.toLowerCase());

			return (result == null) ? defaultValue : result;
		}


		private void doUpdate() {
			try {
				long startTime = System.currentTimeMillis();
				Map<String, String> updated = loadOverridesFromDB();
				overrideMap.set(updated);

				M_log.info(String.format("Updated override map in %dms (entry count: %d)",
							 (System.currentTimeMillis() - startTime),
							 updated.size()));

			} catch (Throwable e) {
				M_log.error("Problem updating user type overrides: " + e);
				e.printStackTrace();
			}
		}


		private void recordUserType(Map<String, String> result, String netid, String type) {
			String currentType = result.get(netid);

			if (currentType == null ||
			    userTypesInAscendingOrder.indexOf(currentType) < userTypesInAscendingOrder.indexOf(type.toLowerCase())) {
				result.put(netid, type);
			}
		}


		private Map<String, String> loadOverridesFromDB() throws Exception {

			Map<String, String> result = new HashMap<String, String>();

			Connection connection = null;
			PreparedStatement ps = null;
			ResultSet rs = null;

			try {
				connection = sqlService.borrowConnection();
				ps = connection.prepareStatement("SELECT lower(netid), type FROM nyu_v_user_type_overrides");
				rs = ps.executeQuery();

				while (rs.next()) {
					String netid = rs.getString(1);
					String type = rs.getString(2);

					recordUserType(result, netid, type);
				}
			} finally {
				if (ps != null) {
					try { ps.close (); } catch (Exception e) {}
				}
				if (rs != null) {
					try { rs.close (); } catch (Exception e) {}
				}

				sqlService.returnConnection (connection);
			}

			return result;
		}
	}


	private SqlService sqlService = (SqlService) ComponentManager.get(SqlService.class.getName());

	private NYUUserTypeOverrideCache userTypeOverrideCache = new NYUUserTypeOverrideCache();


	// Turn "uid=mt1970,ou=People,o=nyu.edu,o=nyu" into "mt1970"
	private String dnToNetId(String dn)
	{
		Matcher m = Pattern.compile("(?i)uid=(.*?),").matcher(dn);

		if (m.find() && m.groupCount() > 0) {
			return m.group(1);
		}

		return null;
	}


	/**
	 * {@inheritDoc}
	 *
	 * @see edu.amc.sakai.user.EntryAttributeToUserTypeMapper#mapLdapEntryToSakaiUserType(LDAPEntry, LdapAttributeMapper)
	 */
	public String mapLdapEntryToSakaiUserType(LDAPEntry ldapEntry, LdapAttributeMapper mapper) {
		
		String userType = super.mapLdapEntryToSakaiUserType(ldapEntry, mapper);
		String netId = dnToNetId(ldapEntry.getDN());

		if (netId == null) {
			return userType;
		}

		return userTypeOverrideCache.getTypeForUser(netId, userType);
	}


    public String getTypeForNetId(String netId) {
	return userTypeOverrideCache.getTypeForUser(netId, null);
    }

}
