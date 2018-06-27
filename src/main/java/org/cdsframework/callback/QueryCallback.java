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
package org.cdsframework.callback;

import org.cdsframework.base.BaseDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.enumeration.QueryType;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.base.BaseRowMapper;
import org.cdsframework.exceptions.EmptyResultsException;
import org.cdsframework.exceptions.IncorrectResultSizeException;
import org.cdsframework.exceptions.IntegrityViolationException;
import org.cdsframework.exceptions.JdbcConnectionException;
import org.cdsframework.exceptions.UncaughtSQLException;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.StringUtils;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cdsframework.annotation.Table;
import org.cdsframework.enumeration.DatabaseType;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.ObjectUtils;
import org.cdsframework.util.support.CoreConstants;
import org.cdsframework.util.table.QueryOperation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public abstract class QueryCallback<T extends BaseDTO> {

    private static final LogUtils logger = LogUtils.getLogger(QueryCallback.class);
    protected Class queryClass;
    private String tableName;
    private String tableAlias = "";
    private boolean rollbackOnNotFound = false;
    private int rowLimit = 0;
    private boolean debugDML = false;
    private DatabaseType databaseType;
    //necessary b/c this sometimes receives a QueryOperation on the callback
    QueryOperation queryOperation = null;
    private Map<String, String> sortFieldOrderByMap = new HashMap<String, String>();
    private boolean dontIncludeRowIdInOrderBy = false;
    private final static Pattern orderByPattern = Pattern.compile("(?:)?\\S*order by\\S*(?:\\s\\S+)?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private final static Pattern rowIdPattern = Pattern.compile("\\browid\\b", Pattern.CASE_INSENSITIVE);

    boolean callbackNamedParametersOverridden = true;

    public QueryCallback(String tableName) throws MtsException {
        if (StringUtils.isEmpty(tableName)) {
            throw new MtsException("tableName must not be null!");
        }
        this.tableName = tableName.toUpperCase();
    }

    public QueryCallback(String tableName, int rowLimit) throws MtsException {
        this(tableName);
        this.rowLimit = rowLimit;
    }

    public QueryCallback(String tableName, String tableAlias) throws MtsException {
        this(tableName);
        this.tableAlias = tableAlias;
    }

    public QueryCallback(String tableName, Map<String, String> sortFieldOrderByMap, boolean rollbackOnNotFound, String tableAlias)
            throws MtsException {
        if (StringUtils.isEmpty(tableName)) {
            throw new MtsException("tableName must not be null!");
        }
        this.tableName = tableName.toUpperCase();
        this.sortFieldOrderByMap = sortFieldOrderByMap;
        this.rollbackOnNotFound = rollbackOnNotFound;
        this.tableAlias = tableAlias;
    }

    public void setQueryClass(Class queryClass) {
        this.queryClass = queryClass;
    }

    public Class getQueryClass() {
        return queryClass;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isRollbackOnNotFound() {
        return rollbackOnNotFound;
    }

    public void setRollbackOnNotFound(boolean rollbackOnNotFound) {
        this.rollbackOnNotFound = rollbackOnNotFound;
    }

    public void setDebugDML(boolean debugDML) {
        this.debugDML = debugDML;
    }

    private String getQueryDMLMain(QueryType queryType, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        final String METHODNAME = "getQueryDMLMain ";
        logger.logBegin(METHODNAME);
        String query;
        try {
            query = getQueryDML(baseDTO, sessionDTO, propertyBagDTO);
            boolean lazy = DTOUtils.isQueryLazy(baseDTO);
            Boolean skipLimit = propertyBagDTO.get("skipLimit", false);
            Integer adHocQueryLimit = propertyBagDTO.get("adHocQueryLimit", 0);
            if (adHocQueryLimit > 0) {
                logger.debug(METHODNAME, "found adHocQueryLimit: ", adHocQueryLimit);
            }

            if (lazy) {
                skipLimit = true;
                Boolean rowCount = ObjectUtils.objectToBoolean(baseDTO.getQueryMap().get(CoreConstants.LAZY_ROWCOUNT));
                if (rowCount != null && rowCount) {
                    query = getLazyLoadSQLCount(query);
                } else {
                    // Default page size is 10 or the rowLimit, whichever is greater
                    query = getSQL(query, baseDTO.getQueryMap(), Math.max(rowLimit, 10));
                }
            } else {
                // Only process if multi result set query
                switch (queryType) {
                    case OBJECT_LIST:
                    case CUSTOM_QUERY_LIST:
                    case QUERY_LIST:
                    case QUERY_LIST_WITH_EXCEPTION:
                        query = getSQL(query, baseDTO.getQueryMap(), 0);
                        //                    logger.debug(METHODNAME, "query: ", query);
                        break;
                }
            }
            if (!skipLimit && (rowLimit > 0 || adHocQueryLimit > 0)) {
                query = wrapLimitOnDML(query, adHocQueryLimit > 0 ? adHocQueryLimit : rowLimit);
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
        logger.debug(METHODNAME, "query=", query);
        return query;
    }

    protected abstract String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO);

    protected String getSQLOrderBy(String sortField, Boolean sortOrder) {
        final String METHODNAME = "getSQLOrderBy ";
        if (sortField == null) {
            sortField = "default";
        }
        String orderBy = sortFieldOrderByMap.get(sortField);
        if (orderBy == null && !"default".equals(orderBy)) {
            logger.error(METHODNAME, " unhandled sortField: ", sortField, "; you may need to add a Column annotation on the DTO to make this work");
        }

        if (orderBy != null) {
            if (sortOrder != null && !sortOrder) {
                orderBy += " DESC ";
            }
        }
        return orderBy;
    }

    protected String getSQL(String query, Map<String, Object> queryMap, int pageSize) {
        final String METHODNAME = "getSQL ";
        String orderBy = "";
        String sortField = null;
        Boolean sortOrder = null;

        if (!StringUtils.isEmpty(query)) {

            // Is there an order by defined in the query?
            if (!orderByPattern.matcher(query).find()) {
                if (queryMap.get(CoreConstants.SORT_FIELD) != null) {
                    sortField = (String) queryMap.get(CoreConstants.SORT_FIELD);
                    logger.debug(METHODNAME, "sortField: ", sortField);
                }

                Object objSortOrder = queryMap.get(CoreConstants.SORT_ORDER);
                logger.debug("objSortOrder: ", objSortOrder);
                if (objSortOrder != null) {
                    sortOrder = ObjectUtils.objectToBoolean(objSortOrder);
                    //sortOrder = (Boolean) objSortOrder;
                    logger.debug(METHODNAME, "sortOrder: ", sortOrder);
                }

                String sqlOrderBy = getSQLOrderBy(sortField, sortOrder);
                if (!StringUtils.isEmpty(sqlOrderBy)) {
                    orderBy = sqlOrderBy;
                }
            } else {
                logger.debug(METHODNAME, "found order by in query: ", query);
            }

            if (pageSize > 0) {
                if (queryMap.get(CoreConstants.LAZY_PAGE_SIZE) != null) {
                    pageSize = ObjectUtils.objectToInteger(queryMap.get(CoreConstants.LAZY_PAGE_SIZE));
                    logger.debug(METHODNAME, "queryDTO.getQueryMap().get('" + CoreConstants.LAZY_PAGE_SIZE + "'): ", pageSize);
                }
                int rowOffset = 0;
                if (queryMap.get(CoreConstants.LAZY_ROW_OFFSET) != null) {
                    rowOffset = ObjectUtils.objectToInteger(queryMap.get(CoreConstants.LAZY_ROW_OFFSET));
                    logger.debug(METHODNAME, "rowOffset: ", rowOffset);
                }
                query = wrapPageOnDML(query, orderBy, rowOffset, pageSize);
            } else {
                query += orderBy;
            }

            if (logger.isDebugEnabled()) {
                logger.debug(METHODNAME, "query=", query);
                logger.debug(METHODNAME, "queryClass=", queryClass);
            }

        }
//        else {
//            throw new MtsException(METHODNAME + "sql can not be null.");
//        }
        return query;
    }

    /*
     * Used for Lazy Loader
     */
    protected String getLazyLoadSQLCount(String query) {
        final String METHODNAME = "getLazyLoadSQLCount ";
        // must contain From at position 0
        if (!StringUtils.isEmpty(query)) {
            query = "SELECT count(*) from ( " + query + " ) foo";
        } else {
            throw new IllegalArgumentException(METHODNAME + "query can not be null.");
        }
        return query;
    }

    /**
     * Get the query named parameters. They can either be locally defined via an
     * override of getCallbackNamedParameters or an override of the row mapper
     * getNamedParameters method. Further overrides can occur by overriding the
     * BaseDAO preProcessNamedParameters and postProcessNamedParameters methods.
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param rowMapper
     * @param propertyBagDTO
     * @return
     * @throws MtsException
     */
    private MapSqlParameterSource getNamedParametersMain(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, BaseRowMapper<T> rowMapper, PropertyBagDTO propertyBagDTO)
            throws MtsException {
        final String METHODNAME = "getNamedParametersMain ";
//        logger.logBegin(METHODNAME);
//        logger.debug(METHODNAME, "baseDTO: ", rowMapper.getBaseDTO());
        MapSqlParameterSource result = new MapSqlParameterSource();
        try {
            getCallbackNamedParameters(result, baseDTO, sessionDTO, propertyBagDTO);
            // if getCallbackNamedParameters is not overridden then look for named parameters on the row mapper
            if (!callbackNamedParametersOverridden) {
                if (rowMapper == null) {
                    result = new MapSqlParameterSource();
                    logger.warn("BaseRowMapper is null - returning empty instance of MapSqlParameterSource");
                } else {
                    result = rowMapper.getNamedParametersMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
                    if (result == null) {
                        result = new MapSqlParameterSource();
                        logger.warn("BaseRowMapper getNamedParameters returned null - returning empty instance of MapSqlParameterSource");
                    }
                }
            }
        } finally {
//            logEnd(METHODNAME);
        }
        if (logger.isDebugEnabled() || debugDML) {
            if (result.getValues() != null) {
                logger.debug(METHODNAME, "map size ", result.getValues().size());
                for (String key : result.getValues().keySet()) {
                    logger.debug("key: ", key, " - object: ", result.getValues().get(key));
                }
            }
        }
        return result;
    }

    /**
     * Client override for setting query class specific named parameters.
     *
     * @param namedParameters
     * @param baseDTO
     * @param sessionDTO
     * @param propertyBagDTO
     * @throws MtsException
     */
    protected void getCallbackNamedParameters(
            MapSqlParameterSource namedParameters,
            BaseDTO baseDTO,
            SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO)
            throws MtsException {
        // client override
        callbackNamedParametersOverridden = false;
    }

    final public Object execute(
            NamedParameterJdbcTemplate jdbcTemplate,
            QueryType queryType,
            BaseDTO baseDTO,
            Class queryClass,
            SessionDTO sessionDTO,
            Class requiredType,
            BaseRowMapper<T> rowMapper,
            PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ConstraintViolationException {
        final String METHODNAME = "execute ";
//        logger.logBegin(METHODNAME);
//        logger.debug(METHODNAME, "queryType: " + queryType);
//        logger.debug(METHODNAME, "baseDTO: " + baseDTO);
//        logger.debug(METHODNAME, "queryClass: " + queryClass);
//        logger.debug(METHODNAME, "sessionDTO: " + sessionDTO);
//        logger.debug(METHODNAME, "propertyBag: " + propertyBag);
        Object result = null;
        String dml = null;
        MapSqlParameterSource namedParameters = null;
        try {
            dml = getQueryDMLMain(queryType, baseDTO, sessionDTO, propertyBagDTO);
            namedParameters = getNamedParametersMain(baseDTO, queryClass, sessionDTO, rowMapper, propertyBagDTO);
            if (logger.isDebugEnabled() || debugDML) {
                logQuery(LogLevel.INFO, queryType, requiredType, rowMapper, baseDTO, queryClass, dml, namedParameters);
            }
            // primary key or generic query - single result returned
            if (queryType == QueryType.PRIMARY_KEY || queryType == QueryType.QUERY) {
                result = jdbcTemplate.queryForObject(dml, namedParameters, rowMapper);
                // generic query - array returned
            } else if (queryType == QueryType.QUERY_LIST) {
                result = jdbcTemplate.query(dml, namedParameters, rowMapper);
                logger.debug("isRollbackOnNotFound state: ", isRollbackOnNotFound());
                if (isRollbackOnNotFound() && ((List) result).isEmpty()) {
                    throw new EmptyResultDataAccessException("result should be greater than zero", 0);
                }
                // generic query - hash returned with primary key as key
            } else if (queryType == QueryType.HASH) {
                //logger.debug(METHODNAME, "processing HASHMAP");
                Map<Object, BaseDTO> hash = new HashMap<Object, BaseDTO>();
                for (BaseDTO item : jdbcTemplate.query(dml, namedParameters, rowMapper)) {
                    Object primaryKey = item.getPrimaryKey();
                    //logger.debug(METHODNAME, "HASH processing ", primaryKey, item);
                    if (hash.containsKey(primaryKey)) {
                        logger.error("Duplicate user key found for : ", primaryKey);
                    }
                    hash.put(primaryKey, item);
                }
                result = hash;
//                logger.debug("Hash size: ", hash.size() + "");
                // generic query - single result string returned
            } else if (queryType == QueryType.OBJECT) {
                result = jdbcTemplate.queryForObject(dml, namedParameters, requiredType);
            } else if (queryType == QueryType.OBJECT_LIST) {
                result = jdbcTemplate.queryForList(dml, namedParameters, requiredType);
            } else if (queryType == QueryType.ADD || queryType == QueryType.UPDATE || queryType == QueryType.DELETE) {
                result = jdbcTemplate.update(dml, namedParameters);
            } else {
                throw new MtsException(logger.error("Query type not implemented: ", queryType));
            }
        } catch (DataIntegrityViolationException e) {
            Table dtoTable = DTOUtils.getDtoTable(baseDTO.getClass());
            IntegrityViolationException integrityViolationException = new IntegrityViolationException(e.getMessage());
            logger.error(METHODNAME, e.getClass());
            logQuery(LogLevel.ERROR, queryType, requiredType, rowMapper, baseDTO, queryClass, dml, namedParameters);
            logger.error(METHODNAME, e);
            throw new ConstraintViolationException(dtoTable.databaseId(),
                    getTableName(),
                    logger.error("DataIntegrityViolationException caught(usually on an insert): ",
                            integrityViolationException.getClass().getSimpleName(),
                            " - ",
                            integrityViolationException.getMessage()),
                    integrityViolationException);
        } catch (UncategorizedSQLException e) {
            logger.error(METHODNAME, e.getClass());
            logQuery(LogLevel.ERROR, queryType, requiredType, rowMapper, baseDTO, queryClass, dml, namedParameters);
            UncaughtSQLException uncaughtSQLException = new UncaughtSQLException(e.getMessage(), "", e.getSql(), e.getSQLException());
            logger.error(e);
            throw new MtsException(
                    logger.error("UncaughtSQLException caught(usually a mapRow field missing issue if message is 'Invalid column name'): ",
                            uncaughtSQLException.getClass().getSimpleName(),
                            " - ",
                            uncaughtSQLException.getMessage()),
                    uncaughtSQLException);
        } catch (EmptyResultDataAccessException e) {
            if (logger.isDebugEnabled() || debugDML) {
                logger.warn(METHODNAME, e.getClass());
                logQuery(LogLevel.WARN, queryType, requiredType, rowMapper, baseDTO, queryClass, dml, namedParameters);
            }
            EmptyResultsException emptyResultsException = new EmptyResultsException(e.getMessage(), e.getExpectedSize(), e.getActualSize());
            String msg = logger.debug("EmptyResultsException - actual size: ",
                    emptyResultsException.getActualSize(),
                    "; expected size: ",
                    emptyResultsException.getExpectedSize(),
                    "; message: ",
                    emptyResultsException.getMessage());
            throw new NotFoundException(
                    getTableName(),
                    msg,
                    emptyResultsException);
        } catch (IncorrectResultSizeDataAccessException e) {
            if (logger.isDebugEnabled() || debugDML) {
                logger.error(METHODNAME, e.getClass());
                logQuery(LogLevel.ERROR, queryType, requiredType, rowMapper, baseDTO, queryClass, dml, namedParameters);
            }
            IncorrectResultSizeException incorrectResultSizeException = new IncorrectResultSizeException(e.getMessage(), e.getExpectedSize(), e.getActualSize());
            throw new NotFoundException(
                    getTableName(),
                    logger.error("IncorrectResultSizeException: actual size - ",
                            incorrectResultSizeException.getActualSize(),
                            "; expected size - ",
                            incorrectResultSizeException.getExpectedSize(),
                            "; message - ",
                            incorrectResultSizeException.getMessage()),
                    incorrectResultSizeException);
        } catch (CannotGetJdbcConnectionException e) {
            SQLException sqlException = null;
            if (e.getCause() instanceof SQLException) {
                sqlException = (SQLException) e.getCause();
            }
            JdbcConnectionException jdbcConnectionException = new JdbcConnectionException(e.getMessage(), sqlException);
            throw new MtsException(
                    logger.error("JdbcConnectionException - ",
                            jdbcConnectionException.getMessage()),
                    jdbcConnectionException);
        } catch (Exception e) {
            logger.error(METHODNAME, e.getClass());
            logQuery(LogLevel.ERROR, queryType, requiredType, rowMapper, baseDTO, queryClass, dml, namedParameters);
            logger.error(e);
            throw new MtsException(
                    logger.error("Unexpected Exception caught: ",
                            e.getClass().getSimpleName(),
                            " - ",
                            e.getMessage()));
        } finally {
//            logger.logEnd(METHODNAME);
        }
        return result;
    }

    /**
     * Get the value of rowLimit
     *
     * @return the value of rowLimit
     */
    public int getRowLimit() {
        return rowLimit;
    }

    /**
     * Set the value of rowLimit
     *
     * @param rowLimit new value of rowLimit
     */
    public void setRowLimit(int rowLimit) {
        this.rowLimit = rowLimit;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    public Map<String, String> getSortFieldOrderByMap() {
        return sortFieldOrderByMap;
    }

    public void setSortFieldOrderByMap(Map<String, String> sortFieldOrderByMap) {
        this.sortFieldOrderByMap = sortFieldOrderByMap;
    }

    protected String wrapLimitOnDML(String sql, int limit) {
        String result = sql;
        if (sql != null && sql.trim().toLowerCase().startsWith("select")) {
            if (databaseType == DatabaseType.ORACLE) {
                result = "select * from (" + sql + ") where rownum <= " + limit;
            } else if (databaseType == DatabaseType.DERBY
                    || databaseType == DatabaseType.SQLSERVER
                    || databaseType == DatabaseType.POSTGRESQL) {
                result = sql + " OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY";
            } else if (databaseType == DatabaseType.MYSQL) {
                // there is an optimised way to do this in mysql - look into it another time
                // http://explainextended.com/2011/02/11/late-row-lookups-innodb/
                result = sql + " LIMIT " + limit + " OFFSET 0 ";
            } else {
                throw new IllegalStateException("unsupported database type: " + databaseType);
            }
        }
        return result;
    }

    protected String wrapPageOnDML(String sql, String orderBy, int rowOffset, int pageSize) {
        final String METHODNAME = "wrapPageOnDML ";
        String result = sql + orderBy;

        if (pageSize > 0) {

            if (sql != null && sql.trim().toLowerCase().startsWith("select")) {
                if (null != databaseType) {
                    switch (databaseType) {
                        case ORACLE:
                            //
                            // If a rowid is found in the select statement it means that the developer has taken steps
                            // for the query to return a deterministic resultset
                            //
                            boolean rowIdInOrderBy = false;
                            boolean rowIdInSelect = rowIdPattern.matcher(sql).find();
                            logger.debug(METHODNAME, "rowIdInSelect=", rowIdInSelect);
                            // Do we have a row Id in the sql ?
                            if (rowIdInSelect) {
                                // Get the order by's and check if the row Id is in the order by
                                Matcher matcher = orderByPattern.matcher(sql);
                                while (matcher.find()) {
                                    String matchText = sql.substring(matcher.start(), matcher.end());
                                    logger.debug(METHODNAME, "matchText=", matchText);
                                    if (matchText.toLowerCase().contains("rowid")) {
                                        rowIdInOrderBy = true;
                                        break;
                                    }
                                }
                                logger.debug(METHODNAME, "rowIdInOrderBy=", rowIdInOrderBy);
                            }   //
                            // Tack on rowId to order by so that the query is deterministic, sorting by duplicate values yields an
                            // undeterministict result. There is no guaranteed that a row from page X will not show up on page Y.
                            // Ordering by rowid forces a deterministinct resultset
                            //
                            // Row Id present in the above sql's order by ?
                            logger.debug(METHODNAME, "orderBy=", orderBy, " rowIdInOrderBy=", rowIdInOrderBy,
                                    " dontIncludeRowIdInOrderBy=", dontIncludeRowIdInOrderBy,
                                    " getTableAlias=", getTableAlias());

                            if (!rowIdInOrderBy && !this.dontIncludeRowIdInOrderBy && getTableAlias() != null) {
                                // sometimes in complex joins you need to specify the rowid alias which can be supplied through
                                // the constructor or via a rowIdAlias attribute on the table xml
                                if (StringUtils.isEmpty(orderBy)) {
                                    orderBy = String.format(" order by %srowid", getTableAlias());
                                } else {
                                    orderBy += String.format(", %srowid", getTableAlias());
                                }
                            }   // Wrap query with paging constraints
                            result = "select * from (select p.*, rownum rnum from (" + sql + orderBy + ") p ) where rnum > " + rowOffset + " and rnum <= " + (rowOffset + pageSize);
                            logger.debug(METHODNAME, "result=", result);

                            break;
                        case DERBY:
                        case SQLSERVER:
                        case POSTGRESQL:
                            result = sql + orderBy + " OFFSET " + rowOffset + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
                            logger.debug(METHODNAME, "orderBy=", orderBy);
                            logger.debug(METHODNAME, "sql=", sql);
                            logger.debug(METHODNAME, "result=", result);
                            break;
                        case MYSQL:
                            // there is an optimized way to do this in mysql - look into it another time
                            // http://explainextended.com/2011/02/11/late-row-lookups-innodb/
                            result = sql + orderBy + " LIMIT " + pageSize + " OFFSET " + rowOffset;
                            break;
                        default:
                            throw new IllegalStateException("unsupported database type: " + databaseType);
                    }
                } else {
                    logger.error(METHODNAME, "databaseType was null!");
                }
            }
        } else {
            logger.error(METHODNAME, "pageSize was not > 0 - check the paginator settings on the client application! pageSize=", pageSize);
        }
        return result;
    }

    public boolean isDontIncludeRowIdInOrderBy() {
        return dontIncludeRowIdInOrderBy;
    }

    public void setDontIncludeRowIdInOrderBy(boolean dontIncludeRowIdInOrderBy) {
        this.dontIncludeRowIdInOrderBy = dontIncludeRowIdInOrderBy;
    }

    public String getTableAlias() {
//        String result = "";
//        if (tableAlias != null && !tableAlias.trim().isEmpty()) {
//            result = tableAlias.trim() + ".";
//        }
        return tableAlias;
    }

    public void setTableAlias(String tableAlias) {
        // Append the period
        if (!StringUtils.isEmpty(tableAlias)) {
            tableAlias = tableAlias.trim() + ".";
        }

        this.tableAlias = tableAlias;
    }

    private static void logQuery(LogLevel loglevel, QueryType queryType, Class requiredType, BaseRowMapper rowMapper, BaseDTO baseDTO, Class queryClass, String dml, MapSqlParameterSource namedParameters) {
        final String METHODNAME = "logQuery ";
        logger.log(loglevel, METHODNAME, "queryType: ", queryType);
        logger.log(loglevel, METHODNAME, "requiredType: ", requiredType);
        logger.log(loglevel, METHODNAME, "rowMapper: ", rowMapper);
        logger.log(loglevel, METHODNAME, "baseDTO: ", baseDTO);
        logger.log(loglevel, METHODNAME, "dml: ", dml);
        logger.log(loglevel, METHODNAME, "queryClass: ", queryClass != null ? queryClass.getCanonicalName() : null);
        if (namedParameters != null) {
            Map<String, Object> values = namedParameters.getValues();
            for (String key : values.keySet()) {
                logger.log(loglevel, METHODNAME, "    namedParameter value: ", key, " - ", values.get(key));
            }
        } else {
            logger.log(loglevel, METHODNAME, "namedParameters was null");
        }
    }

}
