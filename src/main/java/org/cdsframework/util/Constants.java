/**
 * The MTS core support EJB project is the base framework for the CDS Framework Middle Tier Service.
 *
 * Copyright (C) 2016 New York City Department of Health and Mental Hygiene, Bureau of Immunization
 * Contributions by HLN Consulting, LLC
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version. You should have received a copy of the GNU Lesser
 * General Public License along with this program. If not, see <http://www.gnu.org/licenses/> for more
 * details.
 *
 * The above-named contributors (HLN Consulting, LLC) are also licensed by the New York City
 * Department of Health and Mental Hygiene, Bureau of Immunization to have (without restriction,
 * limitation, and warranty) complete irrevocable access and rights to this project.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; THE
 * SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING,
 * BUT NOT LIMITED TO, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE COPYRIGHT HOLDERS, IF ANY, OR DEVELOPERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES, OR OTHER LIABILITY OF ANY KIND, ARISING FROM, OUT OF, OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information about this software, see https://www.hln.com/services/open-source/ or send
 * correspondence to ice@hln.com.
 */
package org.cdsframework.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cdsframework.dto.UserDTO;
import org.cdsframework.enumeration.DatabaseType;
import org.cdsframework.exceptions.MtsException;

/**
 * A class to store public static final constant values. Do not use for anything not related to the core plugin.
 *
 * @author HLN Consulting, LLC
 */
public class Constants {

    private static LogUtils logger = LogUtils.getLogger(Constants.class);
    /**
     * The database resource map used by the application.
     */
    public static Map<String, DatabaseResource> DB_RESOURCES = new HashMap<String, DatabaseResource>();

    public static Map<String, String> ALERT_JOB_CLASSES = new HashMap<String, String>();

    /**
     * The list of installed plugins
     */
    public static List<String> INSTALLED_PLUGINS = new ArrayList<String>();
    public static final Map<Integer, String> PLSQL_ERROR_CODES = new HashMap<Integer, String>();
    public static final List<Class> NATIVE_PROXY_USERS = new ArrayList<Class>();
    public static final String PERMISSION_CACHE_NAME = "securitySchemePermissionCache";
    public static final String MTS_INTERNAL_DB_RESOURCE_ID = "MTSINT";
    public static final String MTS_EXTERNAL_DB_RESOURCE_ID = "MTS";

    public static String APP_SERVER = null;

    static {
        PLSQL_ERROR_CODES.put(-20010, "Fatal database error.");
        PLSQL_ERROR_CODES.put(-20020, "Internal processing error.");
        PLSQL_ERROR_CODES.put(-20030, "Internal validation error.");
        PLSQL_ERROR_CODES.put(-20050, "No data found using key.");
        PLSQL_ERROR_CODES.put(-20099, "Logging error.");
        PLSQL_ERROR_CODES.put(-20200, "Transitory database error.");
        PLSQL_ERROR_CODES.put(-20210, "No data found on select.");
        PLSQL_ERROR_CODES.put(-20211, "Too many rows.");
        PLSQL_ERROR_CODES.put(-20220, "Not found on update.");
        PLSQL_ERROR_CODES.put(-20221, "Duplicate value on insert or update.");
        PLSQL_ERROR_CODES.put(-20230, "Not found on delete.");
        PLSQL_ERROR_CODES.put(-20300, "User validation error.");
        PLSQL_ERROR_CODES.put(-20500, "Message for user.");
        NATIVE_PROXY_USERS.add(UserDTO.FindCatProxyUser.class);
    }

    public static void loadDbResources(String databaseResources) throws MtsException {
        if (databaseResources != null) {
            for (String resource : databaseResources.split(",")) {
                if (resource.contains("|")) {
                    String[] resourceItems = resource.split("\\|");
                    logger.info("resourceItems.length=", resourceItems.length);
                    if (resourceItems.length == 4) {
                        String resourceId = resourceItems[0].trim().toUpperCase();
                        //String jndiName = resourceItems[1].trim().toLowerCase();
                        String jndiName = resourceItems[1].trim();

                        DatabaseType resourceType = DatabaseType.valueOf(resourceItems[2].trim().toUpperCase());
                        String schemaOwner = resourceItems[3].trim().toUpperCase();
                        DatabaseResource databaseResource = DB_RESOURCES.get(resourceId);
                        if (databaseResource == null) {
                            databaseResource = new DatabaseResource(resourceId, jndiName, resourceType, schemaOwner);
                            DB_RESOURCES.put(resourceId, databaseResource);
                            logger.info("initialized db resource: ", resourceId, " - ", databaseResource);
                        } else {
                            logger.warn("db resource already initialized: " + resource);
                        }
                    } else {
                        throw new MtsException("Malformed db resource: " + resource);
                    }
                } else {
                    throw new MtsException("Malformed db resource: " + resource);
                }
            }
        }
    }
}
