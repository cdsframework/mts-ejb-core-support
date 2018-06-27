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
package org.cdsframework.ejb.local;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.sql.DataSource;
import org.cdsframework.annotation.Entity;
import org.cdsframework.annotation.ParentChildRelationship;
import org.cdsframework.annotation.Table;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.enumeration.ApplicationServer;
import org.cdsframework.enumeration.DatabaseType;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.mybatis.MyBatisInAppMigrator;
import org.cdsframework.util.Constants;
import org.cdsframework.util.DTOTable;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.DatabaseResource;
import org.cdsframework.util.EJBUtils;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.StringUtils;
import org.cdsframework.util.XmlTableResourceUtils;
import org.cdsframework.util.table.QueryOperation;
import org.cdsframework.util.table.SelectElement;
import org.cdsframework.util.table.XmlTableResource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 *
 * @author HLN Consulting, LLC
 */
@Startup
@Singleton
@LocalBean
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)

public class DbMGRLocal {

    private final static LogUtils logger = LogUtils.getLogger(DbMGRLocal.class);
    private final Map<String, JdbcTemplate> dbResourceMap = new HashMap<String, JdbcTemplate>();
    private final Map<Class<? extends BaseDTO>, XmlTableResource> xmlTableResourceMap = new HashMap<Class<? extends BaseDTO>, XmlTableResource>();
    private final Map<Class<? extends BaseDTO>, Map<String, String>> tableSqlMap = new HashMap<Class<? extends BaseDTO>, Map<String, String>>();
    private final List<Class<? extends BaseDTO>> processQueueList = new ArrayList<Class<? extends BaseDTO>>();
    private final Map<DatabaseType, String> dbDetectSchemaMap = new EnumMap<DatabaseType, String>(DatabaseType.class);
    @EJB
    private MyBatisInAppMigrator myBatisInAppMigrator;

    @PostConstruct
    public void postConstruct() {
        dbDetectSchemaMap.put(DatabaseType.DERBY, "values session_user");
        dbDetectSchemaMap.put(DatabaseType.MYSQL, "select current_user()");
        dbDetectSchemaMap.put(DatabaseType.ORACLE, "SELECT SYS_CONTEXT ('USERENV', 'SESSION_USER') FROM DUAL");
        dbDetectSchemaMap.put(DatabaseType.SQLSERVER, "SELECT schema_name()");
        dbDetectSchemaMap.put(DatabaseType.POSTGRESQL, "select session_user");
    }

    /**
     * Interrogate a DTO class for its underlying table configuration and perform any initialization functions as appropriate.
     *
     * @param dtoClass the DTO class to process
     * @throws MtsException
     */
    @Lock(LockType.READ)
    public void initializePersistenceMechanism(Class<? extends BaseDTO> dtoClass) throws MtsException {
        final String METHODNAME = "initializePersistenceMechanism ";

        synchronized (processQueueList) {
            if (processQueueList.contains(dtoClass)) {
                logger.debug(METHODNAME, "dtoClass already in queue: ", dtoClass);
                return;
            } else {
                processQueueList.add(dtoClass);
            }
        }

        try {
            if (dtoClass == null) {
                logger.error(METHODNAME, "- cannot invoke with null DTO class");
                return;
            }

            if (Modifier.isAbstract(dtoClass.getModifiers())) {
                logger.debug(METHODNAME, "- skipping abstract class: ", dtoClass);
                return;
            }

            // if this method has already been called for this class then just return
            if (xmlTableResourceMap.containsKey(dtoClass) && tableSqlMap.containsKey(dtoClass)) {
                logger.debug(METHODNAME, dtoClass.getSimpleName(), " already initialized.");
                return;
            }

            // pre-process any foreign key related objects
            preProcessDtoClass(dtoClass);
            if (logger.isDebugEnabled()) {
                logger.debug(METHODNAME, "processing: ", dtoClass);
            }

            // no Table annotation - no go
            Table dtoTable = getDtoTable(dtoClass);
            if (dtoTable == null) {
                logger.error(METHODNAME, "- cannot invoke with null DTO dtoTable");
                return;
            }

            // no table name set - no go
            String dtoTableName = getDtoTableName(dtoClass);
            if (dtoTableName == null) {
                logger.error(dtoClass.getSimpleName(), " not annotated with a table name - fix");
                return;
            }

            // no database id - no go
            String databaseId = getDtoTableDatabaseId(dtoClass);
            if (databaseId == null) {
                logger.error(dtoClass.getSimpleName(), " not annotated with a database id - fix");
                return;
            }
            // make sure the dto's resource is in dbResourceMap
            JdbcTemplate dataSource = getDataSource(dtoClass);
            if (dataSource == null) {
                logger.error(dtoClass.getSimpleName(), " dataSource was null - please investigate");
                return;
            }

            // don't proceed if there is no xmlTableResource
            XmlTableResource xmlTableResource = getTableResource(dtoClass);
            if (xmlTableResource == null) {
                logger.debug(dtoClass.getSimpleName(), " xmlTableResource was null");
                return;
            }

            // make sure the dto's SQL is in tableSqlMap
            Map<String, String> dtoSqlMap = getDtoSqlMap(dtoClass);
            if (dtoSqlMap == null || dtoSqlMap.isEmpty()) {
                logger.error(dtoClass.getSimpleName(), " dtoSqlMap was null - please investigate");
                return;
            }

            // post-rocess any child objects
            postProcessDtoClass(dtoClass);
        } finally {
            synchronized (processQueueList) {
                processQueueList.remove(dtoClass);
            }
            logger.debug(METHODNAME, "processQueueList=", processQueueList);
        }

    }

    /**
     * Find all fields with GenerationSource.FOREIGN_CONSTRAINT and pre-initialize those DTOs as there may be foreign key
     * relationships in the currently processing DTO table to this DTO list. Do the same for any ReferenceDTOs.
     *
     * @param dtoClass the class to interrogate for foreign keys and ReferenceDTO fields.
     * @throws MtsException
     */
    private void preProcessDtoClass(Class<? extends BaseDTO> dtoClass) throws MtsException {
        final String METHODNAME = "preProcessDtoClass ";
        List<Class<? extends BaseDTO>> foreignKeySourceClasses = DTOUtils.getForeignKeySourceClasses(dtoClass);
        for (Class<? extends BaseDTO> item : foreignKeySourceClasses) {
            logger.debug(METHODNAME, "pre-processing DTO related to ", dtoClass.getSimpleName(), ": ", item.getSimpleName());
            initializePersistenceMechanism(item);
        }
        List<Field> referenceDTOs = DTOUtils.getReferenceDTOs(dtoClass);
        for (Field item : referenceDTOs) {
            if (item != null) {
                Class<?> type = item.getType();
                if (type.getSuperclass() == BaseDTO.class) {
                    initializePersistenceMechanism((Class<? extends BaseDTO>) type);
                }
            }
        }
    }

    /**
     * Find all ParentChildRelationship child DTOs and process them after the parent is finished.
     *
     * @param dtoClass the class to interrogate for foreign keys and ReferenceDTO fields.
     * @throws MtsException
     */
    private void postProcessDtoClass(Class<? extends BaseDTO> dtoClass) throws MtsException {
        final String METHODNAME = "postProcessDtoClass ";
        Map<Class<? extends BaseDTO>, ParentChildRelationship> parentChildRelationshipMapByDTO = DTOUtils.getParentChildRelationshipMapByDTO(dtoClass);
        for (Class<? extends BaseDTO> item : parentChildRelationshipMapByDTO.keySet()) {
            logger.debug(METHODNAME, "post-processing DTO related to ", dtoClass.getSimpleName(), ": ", item.getSimpleName());
            initializePersistenceMechanism(item);
        }
    }

    /**
     * Check if the table exists.
     *
     * @param tableName table name
     * @param dataSource
     * @return
     */
    public boolean tableExists(String tableName, JdbcTemplate dataSource) {
        boolean result = false;
        try {
            logger.debug("Checking to see if ", tableName, " exists in the datasource.");
            dataSource.execute("select count(*) from " + tableName);
            logger.debug(tableName, " exists.");
            result = true;
        } catch (BadSqlGrammarException e) {
            if (e.getCause() instanceof SQLException) {
                logger.debug("Cause: ", e.getCause().getMessage());
            }
        }
        return result;
    }

    /**
     * Get all the DTO's DDL and DDL from its annotations.
     *
     * @param dtoClass DTO class reference containing table info
     * @return
     */
    @Lock(LockType.READ)
    public Map<String, String> getDtoSqlMap(Class<? extends BaseDTO> dtoClass) {
        final String METHODNAME = "getDtoSqlMap ";
        Map<String, String> sqlMap = tableSqlMap.get(dtoClass);
        if (sqlMap != null) {
            return sqlMap;
        } else {
            sqlMap = new HashMap<String, String>();
        }
        String dtoTableName = getDtoTableName(dtoClass);
        XmlTableResource xmlTableResource = getTableResource(dtoClass);
        try {
            if (dtoTableName != null) {
                if (xmlTableResource != null) {
                    sqlMap.put("select", String.format(" %s ", xmlTableResource.getSelect().getValue().trim()));
                    sqlMap.put("tableAlias", xmlTableResource.getSelect().getTableAlias());
                    String selectByPrimaryKey = xmlTableResource.getSelectByPrimaryKey();
                    if (!StringUtils.isEmpty(selectByPrimaryKey)) {
                        selectByPrimaryKey = selectByPrimaryKey.trim();
                    }
                    sqlMap.put("selectByPrimaryKey", String.format(" %s ", selectByPrimaryKey));
                    sqlMap.put("insert", String.format(" %s ", xmlTableResource.getInsert().trim()));
                    String update = xmlTableResource.getUpdate();
                    if (!StringUtils.isEmpty(update)) {
                        update = update.trim();
                    }
                    sqlMap.put("update", String.format(" %s ", update));
                    String delete = xmlTableResource.getDelete();
                    if (!StringUtils.isEmpty(delete)) {
                        delete = delete.trim();
                    }

                    sqlMap.put("delete", String.format(" %s ", delete));
                    sqlMap.put("orderBy", String.format(" %s ", xmlTableResource.getOrderBy().trim()));
                    tableSqlMap.put(dtoClass, sqlMap);
                } else {
                    logger.debug(METHODNAME, "xmlTableResource not found.");
                }
            } else {
                logger.error(METHODNAME, "dtoTableName is not set - annotate the DTO with @Table.");
            }
        } catch (Exception e) {
            logger.error(METHODNAME + "Exception on XML table initialization.");
            logger.error(e);
        }
        return sqlMap;
    }

    /**
     * Get a DTO's table name if it is present in the its annotations.
     *
     * @param dtoClass DTO class reference containing table info
     * @return
     */
    public static String getDtoTableName(Class<? extends BaseDTO> dtoClass) {
        final String METHODNAME = "getDtoTableName ";
        Table dtoTable = getDtoTable(dtoClass);
        String result = null;
        if (dtoTable == null) {
            logger.error(METHODNAME, "dtoTable is null: ", dtoClass);
            return result;
        }
        if (dtoTable.name() == null) {
            logger.error(METHODNAME, "dtoTable.name() is null: ", dtoClass);
            return result;
        }
        return dtoTable.name().toUpperCase();
    }

    /**
     * Get the databaseId of a DTO if it exists in its annotations.
     *
     * @param dtoClass DTO class reference containing table info
     * @return
     */
    public static String getDtoTableDatabaseId(Class<? extends BaseDTO> dtoClass) {
        final String METHODNAME = "getDtoTableDatabaseId ";
        Table dtoTable = getDtoTable(dtoClass);
        String result = null;
        if (dtoTable == null) {
            logger.error(METHODNAME, "dtoTable is null!");
            return result;
        }
        if (dtoTable.databaseId() == null) {
            logger.error(METHODNAME, "dtoTable.databaseId() is null!");
            return result;
        }
        return dtoTable.databaseId().toUpperCase();
    }

    /**
     * Get the database type of a DTO if it exists in its annotations.
     *
     * @param dtoClass DTO class reference containing table info
     * @return
     */
    public static DatabaseType getDtoTableDatabaseType(Class<? extends BaseDTO> dtoClass) {
        final String METHODNAME = "getDtoTableDatabaseType ";
        String databaseId = getDtoTableDatabaseId(dtoClass);
        DatabaseType result = null;
        if (databaseId == null) {
            logger.error(METHODNAME, "databaseId is null!");
            return result;
        }
        DatabaseResource databaseResource = Constants.DB_RESOURCES.get(databaseId);
        if (databaseResource == null) {
            logger.error(METHODNAME, "databaseResource is null!");
            return result;
        }
        result = databaseResource.getResourceType();
        if (result == null) {
            logger.error(METHODNAME, "databaseType is null!");
        }
        return result;
    }

    /**
     * Get a Table instance object for a DTO.
     *
     * @param dtoClass DTO class reference containing table info
     * @return
     */
    public static Table getDtoTable(Class<? extends BaseDTO> dtoClass) {
        final String METHODNAME = "getDtoTable ";
        Table dtoTable = null;
        if (dtoClass == null) {
            logger.error(METHODNAME, "dtoClass is null!");
            return dtoTable;
        }
        dtoTable = DTOUtils.getDtoTable(dtoClass);
        if (dtoTable == null) {
            logger.error(METHODNAME, "dtoTable is null!");
            return dtoTable;
        }
        return dtoTable;
    }

    /**
     * Initializes the data source in the data source map. Assumes dtoTable has been pre-validated.
     *
     * @param dtoTable
     * @throws MtsException
     */
    private JdbcTemplate getDataSource(Class<? extends BaseDTO> dtoClass) throws MtsException {
        final String METHODNAME = "initializeDataSource: ";
        String databaseId = getDtoTableDatabaseId(dtoClass);
        JdbcTemplate jdbcTemplate = dbResourceMap.get(databaseId);
        if (jdbcTemplate != null) {
            logger.debug("jdbcTemplate already mapped for: ", databaseId);
            return jdbcTemplate;
        }
        String dbResource = Constants.DB_RESOURCES.get(databaseId).getJndiName();
        if (dbResource == null) {
            throw new MtsException("DB Resource " + databaseId + " not found in Constants.DB_RESOURCES");
        }
        DataSource dataSource = (DataSource) EJBUtils.getBaseLookupObject(dbResource);
        if (dataSource == null) {
            throw new MtsException("Data Source " + dbResource + " not found via getBaseLookupObject()");
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setResultsMapCaseInsensitive(true);
        dbResourceMap.put(databaseId, jdbcTemplate);
        return jdbcTemplate;
    }

    /**
     * Retrieve a DTO's XMLTableResource if if exists. If it does exist - perform some cross-checking with the DTO's annotations.
     *
     * @param dtoClass DTO class reference containing table info
     * @return
     */
    @Lock(LockType.READ)
    public XmlTableResource getTableResource(Class<? extends BaseDTO> dtoClass) {
        final String METHODNAME = "getTableResource ";
        InputStream resourceAsStream = null;
        XmlTableResource tableResource = xmlTableResourceMap.get(dtoClass);
        if (tableResource != null) {
            return tableResource;
        }
        String dtoTableName = getDtoTableName(dtoClass);
        String dtoTableDatabaseId = getDtoTableDatabaseId(dtoClass);
        try {
            if (dtoTableName != null) {

                String document = String.format("/tables/%s.xml", dtoTableName.toLowerCase());
                logger.debug(METHODNAME, "Constants.APP_SERVER=", Constants.APP_SERVER, " document=", document);
                if (Constants.APP_SERVER.equalsIgnoreCase(ApplicationServer.OPENEJB.name())) {
                    URL url = getClass().getResource(document);
                    if (url != null) {
                        try {
                            resourceAsStream = url.openStream();
                        } catch (IOException ex) {
                            logger.error(METHODNAME, "An IOExpection, Message: ", ex.getMessage());
                        }
                    }
                } else {
                    resourceAsStream = getClass().getClassLoader().getResourceAsStream(document);
                }

                if (resourceAsStream != null) {
                    tableResource = XmlTableResourceUtils.objectFromStream(resourceAsStream, org.cdsframework.util.table.XmlTableResource.class);
                    if (tableResource != null) {
                        String tableName = tableResource.getTableName();
                        if (tableName != null) {
                            tableName = tableName.trim().toUpperCase();
                        }
                        if (!dtoTableName.equalsIgnoreCase(tableName)) {
                            throw new MtsException("XML table name does not match DTO table name: " + tableName + " - " + dtoTableName);
                        }
                        String databaseId = tableResource.getDatabaseId();
                        if (databaseId != null) {
                            databaseId = databaseId.trim().toUpperCase();
                        }
                        if (!dtoTableDatabaseId.equalsIgnoreCase(databaseId)) {
                            throw new MtsException("XML database ID does not match DTO table database ID: "
                                    + databaseId + " - " + dtoTableDatabaseId + " for table: " + dtoTableName);
                        }
                        xmlTableResourceMap.put(dtoClass, tableResource);
                    } else {
                        throw new MtsException("xmlTableResource was null");
                    }
                } else {
                    logger.debug(METHODNAME, "No XML resource exists for: " + dtoTableName);
                }

                // If Entity Annotation exists, provides generated insert/update/delete/select/selectByPrimaryKey
                tableResource = getEntityTableResource(tableResource, dtoClass);
            } else {
                logger.error(METHODNAME, "dtoTableName is not set - annotate the DTO with @Table.");
            }
        } catch (Exception e) {
            logger.error(METHODNAME + "Exception on XML table initialization.");
            logger.error(e);
        } finally {
            try {
                if (resourceAsStream != null) {
                    resourceAsStream.close();
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return tableResource;
    }

    private XmlTableResource getEntityTableResource(XmlTableResource tableResource, Class<? extends BaseDTO> dtoClass) {
        final String METHODNAME = "getEntityTableResource ";

        // Is there an Entity annotation?
        Entity entity = DTOUtils.getEntity(dtoClass);
        if (entity == null) {
            return tableResource;
        }

        // XmlTableResource
        if (tableResource == null) {
            tableResource = new XmlTableResource();
        }

        // Get the DTOTable
        DTOTable dtoTable = DTOUtils.getDTOTable(dtoClass);

        // Insert
        if (StringUtils.isEmpty(tableResource.getInsert())) {
            tableResource.setInsert(dtoTable.getInsertDML());
        }
        // Update
        if (StringUtils.isEmpty(tableResource.getUpdate())) {
            tableResource.setUpdate(dtoTable.getUpdateDML());
        }
        // Delete
        if (StringUtils.isEmpty(tableResource.getDelete())) {
            tableResource.setDelete(dtoTable.getDeleteDML());
        }
        // Select
        if (tableResource.getSelect() == null) {
            SelectElement selectElement = new SelectElement();
            selectElement.setTableAlias(dtoTable.getTableAlias());
            selectElement.setValue(dtoTable.getSelectDML());
            tableResource.setSelect(selectElement);
        }
        // SelectByPrimaryKeyDML
        if (StringUtils.isEmpty(tableResource.getSelectByPrimaryKey())) {
            tableResource.setSelectByPrimaryKey(dtoTable.getSelectByPrimaryKeyDML());
        }
        if (StringUtils.isEmpty(tableResource.getOrderBy())) {
            tableResource.setOrderBy(dtoTable.getOrderBy());
        }
        tableResource.setRegisterStandardDMLInterfaces(Boolean.TRUE);
        tableResource.setTableName(dtoTable.getTable().name());

        Map<Class, String> parentForeignKeyDmlMap = dtoTable.getParentForeignKeyDmlMap();
        for (Class item : parentForeignKeyDmlMap.keySet()) {
            QueryOperation queryOperation = new QueryOperation();
            queryOperation.setQueryClass(item.getName());
            SelectElement selectElement = new SelectElement();
            selectElement.setValue(parentForeignKeyDmlMap.get(item));
            queryOperation.setQueryDml(selectElement);
            tableResource.getQueryOperations().add(queryOperation);
        }

        return tableResource;

    }

    /**
     * Determines dynamically what the database and schema is from the DataSource of a JNDI string. Runs the MyBatis Migrations
     * scripts against the DataSource.
     *
     * @param resourceId
     * @param jndiName
     * @return
     */
    public String initializeDatabaseResource(String resourceId, String jndiName) {
        final String METHODNAME = "initializeDatabaseResource ";
        String result = null;
        if (jndiName == null) {
            logger.error(METHODNAME, "jndiName is null!");
        } else {
            logger.info(METHODNAME, "jndiName: ", jndiName);
            try {
                DataSource dataSource = (DataSource) EJBUtils.getBaseLookupObject(jndiName);
                if (dataSource == null) {
                    throw new MtsException("Data Source " + jndiName + " not found via getBaseLookupObject()");
                }
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                jdbcTemplate.setResultsMapCaseInsensitive(true);
                String schema = null;
                DatabaseType databaseType = null;
                for (Map.Entry<DatabaseType, String> entry : dbDetectSchemaMap.entrySet()) {
                    try {
                        schema = jdbcTemplate.queryForObject(entry.getValue(), String.class);
                        databaseType = entry.getKey();
                        logger.info(METHODNAME, "database type|schema: ", databaseType, "/", schema);
                    } catch (Exception e) {
                        logger.debug(METHODNAME, e);
                    }
                    if (databaseType != null && schema != null) {
                        break;
                    }
                }
                if (databaseType != null && schema != null) {

                    // apply any mybatis migrations scripts here...
                    String migrationResult;
                    String scriptPath = String.format("%s_db/%s/", resourceId.toLowerCase(), databaseType.toString().toLowerCase());
                    logger.info(METHODNAME, "Plugin mybatis migrations script path: ", scriptPath);
                    Properties myBatisProperties = myBatisInAppMigrator.getMyBatisProperties(databaseType);
                    myBatisProperties.setProperty("changelog", String.format("%s_changelog", resourceId.toLowerCase()));
                    logger.debug(METHODNAME, "Set mybatis migration changelog table name to: ", myBatisProperties.getProperty("changelog"));
                    if (tableExists(myBatisProperties.getProperty("changelog"), jdbcTemplate)) {
                        migrationResult = myBatisInAppMigrator.migrateDb(dataSource, scriptPath, myBatisProperties);
                    } else {
                        logger.info(METHODNAME, "bootstrapiing database!");
                        migrationResult = myBatisInAppMigrator.bootstrapDb(dataSource, scriptPath, myBatisProperties);
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug(METHODNAME, "migration result: ", migrationResult);
                    } else if (migrationResult != null && !migrationResult.isEmpty()) {
                        logger.info(METHODNAME, "migration  successful!");
                    }

                    // return the formatted string...
                    result = String.format("%s|%s", databaseType.name(), schema);
                }
            } catch (MtsException e) {
                logger.error(METHODNAME, e);
            }
        }
        return result;
    }
}
