package org.sakaiproject.site.tool;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.component.cover.ServerConfigurationService;

/**
 * NYUPilotToolsDbHelper abstracts the DB calls to the additional NYU_ specific DB tables to get the pilot tools.
 * 
 * @author Miguel Pellicer (mp5437@nyu.edu)
 *
 */

@Slf4j
public class NYUPilotToolsDbHelper {

    //Table fields
    private String TOOL_TITLE_COLUMN = "tool_name";
    private String TOOL_DESCRIPTION_COLUMN = "tool_description";
    private String TOOL_LEARN_MORE_COLUMN = "tool_learn_more_url";
    private String TOOL_SCHOOL_COLUMN = "school";
    private String TOOL_DEPARTMENT_COLUMN = "department";
    private String TOOL_SUBJECT_CODE_COLUMN = "subject_code";

    //Queries
    private String PILOT_TOOLS_QUERY = "select * from nyu_t_pilot_tools";
    private String PILOT_TOOLS_CREATE_TABLE = "CREATE TABLE \"NYU_T_PILOT_TOOLS\" (\"TOOL_NAME\" VARCHAR2(30 BYTE), \"TOOL_DESCRIPTION\" VARCHAR2(1000 BYTE), \"TOOL_LEARN_MORE_URL\" VARCHAR2(1000 BYTE), \"SCHOOL\" VARCHAR2(30 BYTE), \"DEPARTMENT\" VARCHAR2(30 BYTE), \"SUBJECT_CODE\" VARCHAR2(30 BYTE))";

    private SqlService sqlService;

    public NYUPilotToolsDbHelper() {
        if(sqlService == null) {
            sqlService = (SqlService) org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.db.api.SqlService");
        }
        //setup table if we have auto.ddl enabled.
        boolean autoddl = ServerConfigurationService.getBoolean("auto.ddl", true);
        if(autoddl) {
            initTables();
        }
    }

    /**
     * Sets up our table
     */
    private void initTables() {
        try {
            // Uncomment to handle the table creation here, it may throw errors in the log when the table exists
            //sqlService.dbWrite(PILOT_TOOLS_CREATE_TABLE);
            log.debug("The pilot tools table has been created successfully.");
        } catch (Exception ex) {
            log.error("Error creating the pilot tools table, see the stacktrace above.");
        }
    }

    public List<NYUPilotTool> getPilotToolsFromDb() {
        List<NYUPilotTool> nyuPilotTools = new ArrayList<NYUPilotTool>();
        try{
            nyuPilotTools = sqlService.dbRead(PILOT_TOOLS_QUERY, null,  new SqlReader<NYUPilotTool>() {
                    public NYUPilotTool readSqlResultRecord(ResultSet result) {
                        NYUPilotTool nyuPilotTool = new NYUPilotTool();
                        try{
                            nyuPilotTool.setToolName(result.getString(TOOL_TITLE_COLUMN));
                            nyuPilotTool.setToolDescription(result.getString(TOOL_DESCRIPTION_COLUMN));
                            nyuPilotTool.setToolLearnMoreUrl(result.getString(TOOL_LEARN_MORE_COLUMN));
                            nyuPilotTool.setSchool(result.getString(TOOL_SCHOOL_COLUMN));
                            nyuPilotTool.setDepartment(result.getString(TOOL_DEPARTMENT_COLUMN));
                            nyuPilotTool.setSubjectCode(result.getString(TOOL_SUBJECT_CODE_COLUMN));
                        } catch (SQLException sqlEx) {
                            log.error("Error mapping the pilot tool from the table {}.", sqlEx);
                        }
                        return nyuPilotTool;
                    }
                });
            return nyuPilotTools;
        } catch (Exception ex) {
            log.error("getPilotToolsFromDb: Fatal error getting the pilot tools from the table {}.", ex);
        }
        return nyuPilotTools;
    }
}
