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
package org.cdsframework.base;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.sql.DataSource;
import org.cdsframework.annotation.Audit;
import org.cdsframework.callback.ParentSetterOperation;
import org.cdsframework.callback.QueryCallback;
import org.cdsframework.dto.AuditTransactionDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.ejb.dao.AuditTransactionDAO;
import org.cdsframework.ejb.local.DbMGRLocal;
import org.cdsframework.enumeration.DatabaseType;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.enumeration.Operation;
import org.cdsframework.enumeration.Operator;
import org.cdsframework.enumeration.QueryType;
import org.cdsframework.enumeration.StringCase;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.exceptions.RuntimeJdbcException;
import org.cdsframework.group.Add;
import org.cdsframework.group.CacheAll;
import org.cdsframework.group.Delete;
import org.cdsframework.group.FindAll;
import org.cdsframework.group.PrimaryKey;
import org.cdsframework.group.Update;
import org.cdsframework.util.ClassUtils;
import org.cdsframework.util.Constants;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.DatabaseResource;
import org.cdsframework.util.DateUtils;
import org.cdsframework.util.EJBUtils;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.StringUtils;
import org.cdsframework.util.table.QueryOperation;
import org.cdsframework.util.table.XmlTableResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public abstract class BaseDAO<T extends BaseDTO> implements BaseDAOInterface<T> {

    @EJB
    private DbMGRLocal dbMGRLocal;
    protected LogUtils logger;
    private String dtoTableName;
    private String databaseId;
    private DatabaseType databaseType;
    private DatabaseResource databaseResource;
    protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    protected JdbcTemplate jdbcTemplate;
    protected Map<Class<? extends BaseDTO>, ParentSetterOperation> psoMap = new HashMap<Class<? extends BaseDTO>, ParentSetterOperation>();
    protected Map<Class<? extends BaseDTO>, Class> parentChildMap = new HashMap<Class<? extends BaseDTO>, Class>();
    protected Map<Class, QueryCallback> dmlMap = new HashMap<Class, QueryCallback>();
    private String selectDML;
    private String tableAlias;
    private String selectByPrimaryKeyDML;
    private String orderByDML = "";
    private String insertDML;
    private String updateDML;
    private String deleteDML;
    protected List<String> dmlConstraints = new ArrayList<String>();
    protected List<String> queryMapPredicates = new ArrayList<String>();
    private boolean cached = false;
    private Map<String, BaseRowMapper<? extends BaseDTO>> tableMapperMap = new HashMap<String, BaseRowMapper<? extends BaseDTO>>();
    private Map<String, String> sortFieldOrderByMap = new HashMap<String, String>();
    // Class representing the generics type argument
    private Class<T> dtoClass;
    private boolean debugDAO = false;
    private XmlTableResource xmlTableResource;
    private int globalRowLimit = 0;

    // For Auditing see Audit Annotation
    private AuditTransactionDAO auditTransactionDao;
    private boolean auditAdd;
    private boolean auditUpdate;
    private boolean auditDelete;

    public enum WildCardOption {

        Force, Detect, Trailing, None
    };

    @PostConstruct
    public void postConstructor() {
        final String METHODNAME = "postConstructor ";
        logger = LogUtils.getLogger(this.getClass());
        dtoClass = ClassUtils.getTypeArgument(BaseDAO.class, getClass());
        if (dtoClass == null) {
            logger.error(METHODNAME, "dtoClass was null for ", getClass());
        }

        dtoTableName = DbMGRLocal.getDtoTableName(dtoClass);
        if (dtoTableName == null) {
            logger.error(dtoClass.getSimpleName(), " not annotated with Table - please fix");
        }
        databaseId = DbMGRLocal.getDtoTableDatabaseId(dtoClass);
        databaseResource = Constants.DB_RESOURCES.get(databaseId);
        if (databaseResource == null) {
            logger.error(METHODNAME, "databaseResource was null for ", databaseId, "; ", dtoClass, "; ", dtoTableName);
        }
        databaseType = databaseResource.getResourceType();
        cached = DTOUtils.isCached(dtoClass);
        logger.debug("dtoClass for ", getClass().getSimpleName(), ": ", dtoClass.getSimpleName());
        try {
            dbMGRLocal.initializePersistenceMechanism(dtoClass);
        } catch (Exception e) {
            logger.error(e);
        }
        xmlTableResource = dbMGRLocal.getTableResource(dtoClass);
        sortFieldOrderByMap = DTOUtils.getSortFieldOrderByMap(dtoClass, databaseType);
        initializeDML();
        initializeSqlFromDbMGR();
        if (DTOUtils.isEntity(dtoClass)) {
            registerDefaultEntityTableMapper();
        }
        try {
            Audit audit = DTOUtils.getAudit(dtoClass);
            if (audit != null) {
                auditTransactionDao = (AuditTransactionDAO) EJBUtils.getDtoDao(AuditTransactionDTO.class);
                auditAdd = audit.add();
                auditUpdate = audit.update();
                auditDelete = audit.delete();
            }

            registerDML();
            initializeDataSource();
            initialize();
        } catch (MtsException e) {
            logger.error(e);
        }
    }

    /**
     * initialize the JDBC Template objects by looking at the DTO's Table
     * annotation and pulling out the databaseId.
     *
     * @throws MtsException
     */
    private void initializeDataSource() throws MtsException {
        if (databaseId == null) {
            throw new MtsException("A dataSourceName must be set via an initializeDbResource override method");
        }
        String dbResource = Constants.DB_RESOURCES.get(databaseId).getJndiName();
        if (dbResource == null) {
            throw new MtsException("DB Resource " + databaseId + " not found in Constants.DB_RESOURCES");
        }
        DataSource dataSource = (DataSource) EJBUtils.getBaseLookupObject(dbResource);
        namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setResultsMapCaseInsensitive(true);
    }

    /**
     * set the initial values of the DML string properties.
     *
     */
    private void initializeDML() {
        String className = this.getClass().getCanonicalName();
        orderByDML = "";
        selectDML = "The SELECT DML needs to be overridden in the " + className;
        selectByPrimaryKeyDML = "The SELECT_BY_PRIMARY_KEY DML needs to be overridden in the " + className;
        insertDML = "The INSERT DML needs to be overridden in the " + className;
        updateDML = "The UPDATE DML needs to be overridden in the " + className;
        deleteDML = "The DELETE DML needs to be overridden in the " + className;
    }

    protected void initialize() throws MtsException {
    }

    private void registerDefaultEntityTableMapper() {
        final String METHODNAME = "registerDefaultEntityTableMapper ";

        registerTableMapper(getDtoTableName(), new BaseRowMapper<T>(dtoClass) {
            @Override
            public MapSqlParameterSource getNamedParameters(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                MapSqlParameterSource namedParameters = new MapSqlParameterSource();
                if (baseDTO == null) {
                    logger.warn(METHODNAME, "Null baseDTO submitted. Returning empty MapSqlParameterSource.");
                    return namedParameters;
                }
                preProcessNamedParameters(getDatabaseType(), namedParameters, baseDTO, queryClass, sessionDTO, propertyBagDTO);
                addStdParameters(queryClass, getDatabaseType(), namedParameters, baseDTO);
                postProcessNamedParameters(getDatabaseType(), namedParameters, baseDTO, queryClass, sessionDTO, propertyBagDTO);
                return namedParameters;
            }

            @Override
            public T mapRow(ResultSet rs, int rowNum) throws SQLException {
                try {
                    T row = (T) dtoClass.newInstance();
                    preProcessMapRow(row, rs, rowNum, databaseType);
                    mapStdProperties(getDatabaseType(), rs, row);
                    postProcessMapRow(row, rs, rowNum, databaseType);
                    return row;
                } catch (InstantiationException e) {
                    throw new IllegalStateException(e);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    protected void preProcessMapRow(
            T row,
            ResultSet rs,
            int rowNum,
            DatabaseType databaseType) throws SQLException {
        // client override
    }

    protected void postProcessMapRow(
            T row,
            ResultSet rs,
            int rowNum,
            DatabaseType databaseType) throws SQLException {
        // client override
    }

    protected void preProcessNamedParameters(
            DatabaseType databaseType,
            MapSqlParameterSource namedParameters,
            BaseDTO baseDTO,
            Class queryClass,
            SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO) {
        // client override
    }

    protected void postProcessNamedParameters(
            DatabaseType databaseType,
            MapSqlParameterSource namedParameters,
            BaseDTO baseDTO,
            Class queryClass,
            SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO) {
        // client override
    }

    public Class<T> getDtoClass() {
        return dtoClass;
    }

    /**
     * Get the value of globalRowLimit
     *
     * @return the value of globalRowLimit
     */
    public int getGlobalRowLimit() {
        return globalRowLimit;
    }

    /**
     * Set the value of globalRowLimit
     *
     * @param globalRowLimit new value of globalRowLimit
     */
    public void setGlobalRowLimit(int globalRowLimit) {
        if (globalRowLimit > 0) {
            this.globalRowLimit = globalRowLimit;
            if (dtoClass != null) {
                if (databaseId != null) {
                    if (databaseType != null) {
                        for (QueryCallback dmlOperation : dmlMap.values()) {
                            dmlOperation.setRowLimit(globalRowLimit);
                            dmlOperation.setDatabaseType(databaseType);
                        }
                    }
                }
            }
        }
    }

    public void setDebugDAO(boolean debugDML) {
        this.debugDAO = debugDML;
    }

    @Override
    public int add(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, MtsException, NotFoundException {
        long start = System.nanoTime();
        final String METHODNAME = "add ";
        logger.logBegin(METHODNAME);
        int rowsReturned = 0;
        try {
            if (logger.isDebugEnabled() || debugDAO) {
                logger.debug(METHODNAME, "queryClass: ", queryClass == null ? queryClass : queryClass.getCanonicalName());
            }

            if (queryClass == Update.class) {
                if (logger.isDebugEnabled() || debugDAO) {
                    logger.debug("Got an Update query class on an add - switching to Add");
                }
                queryClass = Add.class;
            }
            // Audit the data
            if (auditTransactionDao != null && auditAdd) {
                auditTransactionDao.audit(baseDTO, Operation.ADD, queryClass, sessionDTO, propertyBagDTO);
            }
            rowsReturned = performDML(QueryType.ADD, false, baseDTO, queryClass, sessionDTO, Integer.class, propertyBagDTO);

        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);                                                
            logger.logEnd(METHODNAME);
        }
        return rowsReturned;
    }

    @Override
    public int update(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException {
        final String METHODNAME = "update ";
        logger.logBegin(METHODNAME);
        int rowsReturned = 0;
        try {
            if (logger.isDebugEnabled() || debugDAO) {
                logger.debug(METHODNAME, "queryClass: ", queryClass == null ? queryClass : queryClass.getCanonicalName());
            }
            if (auditTransactionDao != null && auditUpdate) {
                auditTransactionDao.audit(baseDTO, Operation.UPDATE, queryClass, sessionDTO, propertyBagDTO);
            }
            rowsReturned = performDML(QueryType.UPDATE, false, baseDTO, queryClass, sessionDTO, Integer.class, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
        return rowsReturned;
    }

    @Override
    public int delete(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws NotFoundException,
            ConstraintViolationException, MtsException {
        final String METHODNAME = "delete ";
        logger.logBegin(METHODNAME);
        int rowsReturned = 0;
        try {
            if (logger.isDebugEnabled() || debugDAO) {
                logger.debug(METHODNAME, "queryClass: ", queryClass == null ? queryClass : queryClass.getCanonicalName());
            }
            if (queryClass == Update.class) {
                queryClass = Delete.class;
            }
            if (auditTransactionDao != null && auditDelete) {
                auditTransactionDao.audit(baseDTO, Operation.DELETE, queryClass, sessionDTO, propertyBagDTO);
            }
            rowsReturned = performDML(QueryType.DELETE, false, baseDTO, queryClass, sessionDTO, Integer.class, propertyBagDTO);

        } finally {
            logger.logEnd(METHODNAME);
        }

        return rowsReturned;
    }

    @Override
    public int setParentsChildren(
            BaseDTO baseDTO,
            Class queryClass,
            Class childBOQueryClass,
            boolean rollbackOnNotFound,
            SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException {
        final String METHODNAME = "setParentsChildren ";
        logger.logBegin(METHODNAME);
        int childCount = 0;
        try {
            if (logger.isDebugEnabled() || debugDAO) {
                logger.debug(METHODNAME, "method called for class: ",
                        childBOQueryClass == null ? childBOQueryClass : childBOQueryClass.getCanonicalName());
                logger.debug(METHODNAME, "method called for baseDTO: ",
                        baseDTO == null ? baseDTO : baseDTO.getClass().getCanonicalName());
            }
            if (baseDTO != null) {
                if (dtoClass == baseDTO.getClass()) {
                    logger.debug("Parent class and DAO argument type are the same: ", dtoClass.getCanonicalName());
                }
                Class parentDTOClass = baseDTO.getClass();
                ParentSetterOperation pso = psoMap.get(parentDTOClass);
                if (pso == null) {
                    Class childQueryClass = DTOUtils.getQueryClassFromDtoQueryMap(parentDTOClass, dtoClass);
                    if (logger.isDebugEnabled() || debugDAO) {
                        logger.debug(METHODNAME,
                                "looking up: ",
                                dtoClass == null ? dtoClass : dtoClass.getCanonicalName(),
                                " in ",
                                parentDTOClass == null ? parentDTOClass : parentDTOClass.getCanonicalName(),
                                ": ",
                                childQueryClass == null ? childQueryClass : childQueryClass.getCanonicalName(),
                                " - incoming QC: ",
                                childBOQueryClass == null ? childBOQueryClass : childBOQueryClass.getCanonicalName());
                    }
                    if (childBOQueryClass == childQueryClass) {
                        if (logger.isDebugEnabled() || debugDAO) {
                            logger.debug(METHODNAME,
                                    "Registering parent child operation: ",
                                    parentDTOClass == null ? parentDTOClass : parentDTOClass.getCanonicalName(),
                                    " - ",
                                    childBOQueryClass == null ? childBOQueryClass : childBOQueryClass.getCanonicalName());
                        }
                        registerParentSetter(parentDTOClass, childBOQueryClass);
                        pso = psoMap.get(parentDTOClass);
                    }
                }
                if (pso != null) {
                    childCount = pso.setChildrenOnParent(baseDTO, queryClass, childBOQueryClass, rollbackOnNotFound, sessionDTO, propertyBagDTO);
                } else {
                    throw new MtsException(logger.error(
                            "DTO parent class not registered: ",
                            baseDTO.getClass().getCanonicalName(),
                            " - query class: ",
                            queryClass == null ? queryClass : queryClass.getCanonicalName(),
                            " - childBOQueryClass: ",
                            childBOQueryClass == null ? childBOQueryClass : childBOQueryClass.getCanonicalName(),
                            " - dtoClass: ",
                            dtoClass == null ? dtoClass : dtoClass.getCanonicalName()));
                }
            } else {
                logger.error(METHODNAME, "baseDTO was null...");
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
        return childCount;
    }

    protected void preFindBy(QueryType queryType, BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        // extender overriddable
    }

    protected void postFindBy(QueryType queryType, Object result, BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        // extender overriddable
    }

    @Override
    public T findByPrimaryKey(T baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException {
        final String METHODNAME = "findByPrimaryKey ";
        logger.logBegin(METHODNAME);
        T result = null;
        try {
            preFindBy(QueryType.PRIMARY_KEY, baseDTO, PrimaryKey.class, sessionDTO, propertyBagDTO);
            if (logger.isDebugEnabled()) {
                logger.debug(METHODNAME + "calling performDML(QueryType.PRIMARY_KEY, ", baseDTO.getPrimaryKey());
            }
            result = performDML(QueryType.PRIMARY_KEY, false, baseDTO, PrimaryKey.class, sessionDTO, getDtoClass(), propertyBagDTO);
            logger.debug(METHODNAME + "made it through performDML...", result);
            // pass in the result as the first arg...
            postFindBy(QueryType.PRIMARY_KEY, result, baseDTO, PrimaryKey.class, sessionDTO, propertyBagDTO);
        } catch (ConstraintViolationException de) {
            throw new MtsException("ConstraintViolationException: this should not happen on a find.", de);
        } finally {
            logger.logEnd(METHODNAME);
        }
        return result;
    }

    @Override
    public List<T> findByQueryList(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException {
        return findByQueryList(baseDTO, false, queryClass, sessionDTO, propertyBagDTO);
    }

    private List<T> findByQueryList(BaseDTO baseDTO, boolean rollbackOnNotFound, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException {
        final String METHODNAME = "findByQueryList ";
        logger.debug(METHODNAME, " method called for class: ", queryClass.getSimpleName());
        List<T> result = null;
        preFindBy(QueryType.QUERY_LIST, baseDTO, queryClass, sessionDTO, propertyBagDTO);
        try {
            result = performDML(QueryType.QUERY_LIST, rollbackOnNotFound, baseDTO, queryClass, sessionDTO, List.class, propertyBagDTO);
        } catch (ConstraintViolationException de) {
            throw new MtsException("ConstraintViolationException: this should not happen on a find.", de);
        }
        postFindBy(QueryType.QUERY_LIST, result, baseDTO, queryClass, sessionDTO, propertyBagDTO);
        return result;
    }

    @Override
    public T findByQuery(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException {
        logger.debug("BaseDAO.findByQuery method called for class: ", queryClass.getSimpleName());
        T result = null;
        preFindBy(QueryType.QUERY, baseDTO, queryClass, sessionDTO, propertyBagDTO);
        try {
            result = performDML(QueryType.QUERY, false, baseDTO, queryClass, sessionDTO, getDtoClass(), propertyBagDTO);
        } catch (ConstraintViolationException de) {
            throw new MtsException("ConstraintViolationException: this should not happen on a find.", de);
        }
        postFindBy(QueryType.QUERY, result, baseDTO, queryClass, sessionDTO, propertyBagDTO);
        return result;
    }

    @Override
    public <S> S findObjectByQuery(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws MtsException, NotFoundException {
        logger.debug("Generic BaseDAO.findObjectByQuery method called for class: ", queryClass.getSimpleName());
        S result = null;
        try {
            // requiredType drops into the first position of findMain propertyBag and the incoming propertyBag becomes the second argument
            preFindBy(QueryType.OBJECT, baseDTO, queryClass, sessionDTO, propertyBagDTO);
            propertyBagDTO.put("skipLimit", true);
            result = performDML(QueryType.OBJECT, false, baseDTO, queryClass, sessionDTO, requiredType, propertyBagDTO);
            // requiredType drops into the first position of findMain propertyBag and the incoming propertyBag becomes the second argument
            postFindBy(QueryType.OBJECT, result, baseDTO, queryClass, sessionDTO, propertyBagDTO);
        } catch (ConstraintViolationException de) {
            throw new MtsException("ConstraintViolationException: this should not happen on a find.", de);
        }
        return result;
    }

    @Override
    public <S> List<S> findObjectByQueryList(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws MtsException, NotFoundException {
        logger.debug("Generic BaseDAO.findObjectByQueryList method called for class: ", queryClass.getSimpleName());
        List<S> result = null;
        try {
            // requiredType drops into the first position of findMain propertyBag and the incoming propertyBag becomes the second argument
            preFindBy(QueryType.OBJECT_LIST, baseDTO, queryClass, sessionDTO, propertyBagDTO);
            propertyBagDTO.put("skipLimit", true);
            result = (List<S>) performDML(QueryType.OBJECT_LIST, false, baseDTO, queryClass, sessionDTO, requiredType, propertyBagDTO);
            // requiredType drops into the first position of findMain propertyBag and the incoming propertyBag becomes the second argument
            postFindBy(QueryType.OBJECT_LIST, result, baseDTO, queryClass, sessionDTO, propertyBagDTO);
        } catch (ConstraintViolationException de) {
            throw new MtsException("ConstraintViolationException: this should not happen on a find.", de);
        }
        return result;
    }

    @Override
    public void registerParentSetter(final Class<? extends BaseDTO> parentDTO, final Class childRegistrationClass) {
        final String METHODNAME = "registerParentSetter ";

        // Log begin
        logger.logBegin(METHODNAME);
        logger.debug("Attempting to register ", childRegistrationClass, " for the parentDTO class: ", parentDTO);

        try {
            parentChildMap.put(parentDTO, childRegistrationClass);
            psoMap.put(parentDTO, new ParentSetterOperation() {
                @Override
                public int setChildrenOnParent(BaseDTO baseDTO, Class queryClass, Class childBOQueryClass, boolean rollbackOnNotFound, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
                        throws NotFoundException, MtsException {
                    final String METHODNAME = "setChildrenOnParent";
                    List<T> results;
                    results = findByQueryList(baseDTO, rollbackOnNotFound, childBOQueryClass, sessionDTO, propertyBagDTO);
                    //                logger.debug("registerParentSetterDML queryClass: ",queryClass);
                    //                logger.debug("registerParentSetterDML childBOQueryClass: ",childBOQueryClass);
                    //                logger.debug("registerParentSetterDML childRegistrationClass: ",childRegistrationClass);
                    //                logger.debug("registerParentSetterDML baseDTO children: ", baseDTO.getChildrenDTOs(childRegistrationClass));
                    logger.debug(METHODNAME, "results.size()=", results != null ? results.size() : null);
                    baseDTO.setChildrenDTOs(childRegistrationClass, (List<BaseDTO>) results);
                    //                logger.debug("registerParentSetterDML baseDTO children: ", baseDTO.getChildrenDTOs(childRegistrationClass));
                    //                logger.debug("registerParentSetterDML baseDTO children size: ", baseDTO.getChildrenDTOs(childRegistrationClass) != null ? baseDTO.getChildrenDTOs(childRegistrationClass).size() : null);
                    return results != null ? results.size() : 0;
                }
            });
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public void registerDML(Class queryClass, QueryCallback queryCallback, boolean rollbackOnNotFound) throws MtsException {
        if (rollbackOnNotFound) {
            queryCallback.setRollbackOnNotFound(rollbackOnNotFound);
        }
        queryCallback.setSortFieldOrderByMap(sortFieldOrderByMap);
        queryCallback.setTableAlias(tableAlias);
        registerDML(queryClass, queryCallback);
    }

    public void registerDML(Class queryClass, QueryCallback queryCallback) throws MtsException {
        if (globalRowLimit > 0) {
            queryCallback.setRowLimit(globalRowLimit);
        }
        queryCallback.setDatabaseType(databaseType);
        queryCallback.setQueryClass(queryClass);
        queryCallback.setDebugDML(debugDAO);
        queryCallback.setTableAlias(tableAlias);
        dmlMap.put(queryClass, queryCallback);
    }

    protected <S> S performDML(QueryType queryType, boolean rollbackOnNotFound, BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws MtsException, NotFoundException, ConstraintViolationException {
        final String METHODNAME = "performDML ";
        logger.logBegin(METHODNAME);
        if (logger.isDebugEnabled() || debugDAO) {
            logger.debug(METHODNAME, "performing queryType: ", queryType, " - queryClass: ", queryClass, " - requiredType: ", requiredType);
            logger.debug(METHODNAME, "queryType: " + queryType);
            logger.debug(METHODNAME, "rollbackOnNotFound: " + rollbackOnNotFound);
            logger.debug(METHODNAME, "baseDTO: " + baseDTO);
            logger.debug(METHODNAME, "queryClass: " + queryClass == null ? queryClass : queryClass.getCanonicalName());
            logger.debug("dmlMap.containsKey(queryClass) = ", dmlMap.containsKey(queryClass));
        }
        if (dmlMap.containsKey(queryClass)) {
            QueryCallback dmlOperation = dmlMap.get(queryClass);
            if (logger.isDebugEnabled() || debugDAO) {
                logger.warn("is dmlOperation not null: ", dmlOperation != null);
            }
            if (rollbackOnNotFound) {
                dmlOperation.setRollbackOnNotFound(rollbackOnNotFound);
            }
            BaseRowMapper<? extends BaseDTO> mapper = tableMapperMap.get(dmlOperation.getTableName());
            if (mapper == null) {
                throw new MtsException(logger.error(
                        dmlOperation.getTableName(),
                        " not found in ",
                        this.getClass().getCanonicalName(),
                        " tableMapperMap. A mapper must be mapped to this table value."));
            }
            return (S) dmlOperation.execute(namedParameterJdbcTemplate, queryType, baseDTO, queryClass, sessionDTO, requiredType, mapper, propertyBagDTO);
        } else {
            throw new MtsException(logger.error(queryClass, " not found in ", this.getClass().getCanonicalName(), " dmlMap."));
        }
    }

    protected void registerStandardDMLInterfaces(String tableName) throws MtsException {

        // Find an instance by primary key
        this.registerDML(PrimaryKey.class, new QueryCallback<T>(tableName) {
            @Override
            protected String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                return getSelectByPrimaryKeyDML();
            }
        }, false);

        // Add a new instance
        this.registerDML(Add.class, new QueryCallback<T>(tableName) {
            @Override
            protected String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                return getInsertDML();
            }
        }, false);

        // Update an existing instance
        this.registerDML(Update.class, new QueryCallback<T>(tableName) {
            @Override
            protected String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                return getUpdateDML();
            }
        }, false);

        // Delete an instance
        this.registerDML(Delete.class, new QueryCallback<T>(tableName) {
            @Override
            protected String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                return getDeleteDML();
            }
        }, false);

        this.registerDML(FindAll.class, new QueryCallback<T>(getDtoTableName()) {
            @Override
            protected String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                return getSelectDML();
            }
        }, false);

        if (cached) {
            this.registerDML(CacheAll.class, new QueryCallback<T>(getDtoTableName()) {
                @Override
                protected String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                    return getSelectDML();
                }
            }, false);
        }
    }

    /*
     * checkSqlOrReturnCode - used by stored procedure/function calls to centralize sql/return error handling
     */
    protected int checkSqlOrReturnCode(SimpleJdbcCall simpleJdbcCall, Map outputParms, String sqlCodeParm) throws MtsException {
        final String METHODNAME = "checkSqlOrReturnCode ";
        return checkSqlOrReturnCode(simpleJdbcCall, outputParms, sqlCodeParm, 0);
    }

    /*
     * checkSqlOrReturnCode - used by stored procedure/function calls to centralize sql/return error handling
     */
    protected int checkSqlOrReturnCode(SimpleJdbcCall simpleJdbcCall, Map outputParms, String sqlCodeParm,
            int ignorePLSQLErrorCode) throws MtsException {
        final String METHODNAME = "checkSqlOrReturnCode ";

        // Get the output parms
        Object objSqlCode = outputParms.get(sqlCodeParm);

        int sqlCode = -9999;
        if (objSqlCode != null) {
            // Handle BigDecimal cast class exception
            if (objSqlCode instanceof BigDecimal) {
                sqlCode = ((BigDecimal) objSqlCode).intValue();
            } else {
                sqlCode = (Integer) objSqlCode;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug(METHODNAME + "sqlCode=" + sqlCode);
        }
        logger.info(METHODNAME + "sqlCode=" + sqlCode);

        if (sqlCode != 0 && sqlCode != ignorePLSQLErrorCode) {
            throw new MtsException(simpleJdbcCall.getCatalogName() + "." + simpleJdbcCall.getProcedureName() + " pSqlCode: "
                    + (Constants.PLSQL_ERROR_CODES.containsKey(sqlCode) ? sqlCode + " ("
                    + Constants.PLSQL_ERROR_CODES.get(sqlCode) + ")" : sqlCode));
        }

        return sqlCode;
    }

    protected void clearPredicateValues() {
        queryMapPredicates = new ArrayList<String>();
    }

    protected void setPredicateValue(String predicate) {
        if (predicate != null) {
            queryMapPredicates.add(predicate);
        }
    }

    protected void setPredicateValue(BaseDTO baseDTO, String predicate, String key, WildCardOption wildCardOption, char wildCharacter) {
        getPredicateValue(baseDTO, predicate, key, wildCardOption, wildCharacter, true);
    }

    protected String getPredicateValue(BaseDTO baseDTO, String predicate, String key, WildCardOption wildCardOption, char wildCharacter, boolean addToPredicateMap) {
        boolean valueSet = false;
        String result = predicate;
        Object value = baseDTO.getQueryMap().get(key);
        logger.debug("Initial Predicate Value(", key, "): ", value);
        if (value != null) {
            logger.debug("Initial Predicate Value Class(", key, "): ", value.getClass());

            // String instance only support wildcards
            if (value instanceof String) {
                String criteria = ((String) value).trim();
                logger.debug("Initial Predicate Criteria(", key, "): ", criteria);

                if (!criteria.isEmpty()) {
                    boolean wildcardSet = false;

                    // Check if wild card exists ?
                    if (wildCardOption == WildCardOption.Detect) {
                        if (criteria.contains("*")) {
                            criteria = criteria.replace('*', wildCharacter);
                            wildcardSet = true;
                        }
                    } else if (wildCardOption == WildCardOption.Force) {
                        if (criteria.contains("*")) {
                            criteria = criteria.replace('*', wildCharacter);
                        } else {
                            if (!criteria.startsWith(String.valueOf(wildCharacter))) {
                                criteria = wildCharacter + criteria;
                            }
                            if (!criteria.endsWith(String.valueOf(wildCharacter))) {
                                criteria += wildCharacter;
                            }
                        }
                        wildcardSet = true;
                        
                    } else if (wildCardOption == WildCardOption.Trailing) {
                        if (criteria.contains("*")) {
                            criteria = criteria.replace('*', wildCharacter);
                        } else {
                            if (!criteria.endsWith(String.valueOf(wildCharacter))) {
                                criteria += wildCharacter;
                            }
                        }
                        wildcardSet = true;
                    }

                    // Force the predicate operator
                    if (wildcardSet) {
                        // Replace equals with like
                        result = predicate.replaceAll("(?:=)", "like");
                    } else {
                        // Swap like with equals
                        result = predicate.toLowerCase().replaceAll("(?:like)", "=");
                    }

                    valueSet = true;
                    logger.debug("Criteria Set: ", criteria);
                    baseDTO.getQueryMap().put(key, criteria);
                }
            } else {
                valueSet = true;
            }
        }
        if (valueSet && addToPredicateMap) {
            queryMapPredicates.add(result);
        }

        value = baseDTO.getQueryMap().get(key);
        logger.debug("Final Predicate (", key, "): ", result);
        logger.debug("Final Predicate Value(", key, "): ", value);
        if (value != null) {
            logger.debug("Final Predicate Value Class(", key, "): ", value.getClass());
        }
        return result;
    }

    protected void setDetectWildcardPredicateValue(BaseDTO baseDTO, String predicate, String key) {
        // Interogates predicate value for an * and replaces with the wild card % symbol
        setPredicateValue(baseDTO, predicate, key, WildCardOption.Detect, '%');
    }

    protected void setWildcardPredicateValue(BaseDTO baseDTO, String predicate, String key) {
        // Replaces * with '%'
        // If it does have a wildcard, it replaces it with '%' symbol
        // If it doesnt have a wildcard, it will surround value with a % symbol
        setPredicateValue(baseDTO, predicate, key, WildCardOption.Force, '%');
    }
    
    protected void setTrailingWildcardPredicateValue(BaseDTO baseDTO, String predicate, String key) {
        // Replaces * with '%'
        // If it does have a wildcard, it replaces it with '%' symbol
        // If it doesnt have a wildcard, it will add  % symbol to the end of the value
        setPredicateValue(baseDTO, predicate, key, WildCardOption.Trailing, '%');
    }
    
    protected void setNonWildcardPredicateValue(BaseDTO baseDTO, String predicate, String key) {
        // Does not force a wild card or detect a wild condition
        setPredicateValue(baseDTO, predicate, key, WildCardOption.None, '%');
    }

    protected String getAndClearPredicateMap(String prefix, String suffix, Operator Operator) {
        String result = "";
//        logger.debug("getAndClearPredicateMap", "queryMapPredicates.size()=", queryMapPredicates.size());
        if (queryMapPredicates.size() > 0) {
            result = prefix + StringUtils.getStringFromArray(queryMapPredicates, " " + Operator.toString() + " ") + suffix;
        }
//        logger.debug("getAndClearPredicateMap", "result=", result);
        clearPredicateValues();
        return result;
    }

    /**
     *
     * @param outputParms
     * @param methodName
     */
    public void logOutputParms(Map outputParms, String methodName) {
        if (logger.isDebugEnabled()) {
            Iterator it = outputParms.keySet().iterator();
            while (it.hasNext()) {
                String key = (String) it.next();
                Object value = outputParms.get(key);
                logger.debug(methodName + "key= " + key + " value=" + value);
            }
        }
    }

    protected static void setLowerQueryMapValue(BaseDTO baseDTO, String parameter, String key, MapSqlParameterSource namedParameters) {
        setQueryMapValue(baseDTO, parameter, key, namedParameters, StringCase.Lower);
    }

    protected static void setUpperQueryMapValue(BaseDTO baseDTO, String parameter, String key, MapSqlParameterSource namedParameters) {
        setQueryMapValue(baseDTO, parameter, key, namedParameters, StringCase.Upper);
    }
    
    private static void setQueryMapValue(BaseDTO baseDTO, String parameter, String key, MapSqlParameterSource namedParameters, StringCase stringCase) {
        final String METHODNAME = "setQueryMapValue ";
        Object value = baseDTO.getQueryMap().get(key);
        if (value != null) {
            if (value instanceof String) {
                if (stringCase == StringCase.Lower) {
                    value = value.toString().toLowerCase();
                }
                else if (stringCase == StringCase.Upper) {
                    value = value.toString().toUpperCase();
                }
            } else if (value instanceof Date) {
                value = DateUtils.getFormattedDate((Date) value, "MM/dd/yyyy");
            }
        }
        namedParameters.addValue(parameter, value);
    }

    protected static void setExactQueryMapValue(BaseDTO baseDTO, String parameter, String key, MapSqlParameterSource namedParameters) {
        setQueryMapValue(baseDTO, parameter, key, namedParameters, StringCase.Insenstive);
    }

    protected void registerTableMapper(BaseRowMapper<? extends BaseDTO> mapper) {
        registerTableMapper(getDtoTableName(), mapper);
    }

    protected void registerTableMapper(String tableName, BaseRowMapper<? extends BaseDTO> mapper) {
        boolean exists = tableMapperMap.containsKey(tableName);
        if (exists) {
            logger.warn("Registering another table mapper for: ", tableName);
        }
        tableMapperMap.put(tableName, mapper);
    }

    protected BaseRowMapper getRegisteredTableMapper(String tableName) {
        return tableMapperMap.get(tableName);
    }

    @Override
    public <S> S getNewPrimaryKey(String autoKeySequence, SessionDTO sessionDTO, Class<S> primaryKeyClass) throws MtsException {
        if (autoKeySequence == null || autoKeySequence.trim().isEmpty()) {
            throw new MtsException("Auto key by sequence is on but sequence is not set for: " + this.getClass().getSimpleName());
        }

        String sql;
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        
        switch (getDatabaseType()) {
            case ORACLE:
                sql = "select " + StringUtils.stripNonAlphaNumberic(autoKeySequence, false) + ".nextval from dual";
                break;
            case POSTGRESQL:
            	parameters.addValue("sequence", StringUtils.stripNonAlphaNumberic(autoKeySequence, false));
                sql = "select nextval(:sequence)";
                break;
            default:
                throw new MtsException("Sequences are not supported for " + this.getClass().getCanonicalName() + " (" + getDatabaseType() + ")");
        }
        return namedParameterJdbcTemplate.queryForObject(sql, parameters, primaryKeyClass);
    }

    public Map<Class, QueryCallback> getDmlMap() {
        return dmlMap;
    }

    public String getDeleteDML() {
        return deleteDML;
    }

    public void setDeleteDML(String deleteDML) {
        this.deleteDML = deleteDML;
    }

    public String getInsertDML() {
        return insertDML;
    }

    public void setInsertDML(String insertDML) {
        this.insertDML = insertDML;
    }

    public String getOrderByDML() {
        return orderByDML;
    }

    public void setOrderByDML(String orderByDML) {
        this.orderByDML = orderByDML;
    }

    public String getSelectByPrimaryKeyDML() {
        return selectByPrimaryKeyDML;
    }

    public void setSelectByPrimaryKeyDML(String selectByPrimaryKeyDML) {
        this.selectByPrimaryKeyDML = selectByPrimaryKeyDML;
    }

    public String getSelectDML() {
        return selectDML;
    }

    public void setSelectDML(String selectDML) {
        this.selectDML = selectDML;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public void setTableAlias(String tableAlias) {
        this.tableAlias = tableAlias;
    }

    public String getUpdateDML() {
        return updateDML;
    }

    public void setUpdateDML(String updateDML) {
        this.updateDML = updateDML;
    }

    public String getDtoTableName() {
        if (dtoTableName == null) {
            throw new RuntimeJdbcException(
                    String.format(
                            "%s is not bound to a table - add the Table annotation to the DTO and define its table name.",
                            getDtoClass().getSimpleName()));
        }
        return dtoTableName;
    }

    public String getDatabaseId() {
        if (databaseId == null) {
            throw new RuntimeJdbcException(
                    String.format(
                            "%s is not bound to a table - add the Table annotation to the DTO and define its table name.",
                            getDtoClass().getSimpleName()));
        }
        return databaseId;
    }

    public DatabaseType getDatabaseType() {
        if (databaseType == null) {
            throw new RuntimeJdbcException(
                    String.format(
                            "%s is not bound to a table - add the Table annotation to the DTO and define its table name.",
                            getDtoClass().getSimpleName()));
        }
        return databaseType;
    }

    /**
     * sets the select/insert/update/delete/orderby sql via the DbMGRLocal
     * parsing of the table xml file.
     */
    private void initializeSqlFromDbMGR() {
        final String METHODNAME = "initializeDataFromXML: ";
        Map<String, String> dtoSqlMap = dbMGRLocal.getDtoSqlMap(dtoClass);
        try {
            if (dtoTableName != null) {
                if (dtoSqlMap != null) {
                    setSelectDML(dtoSqlMap.get("select"));
                    setTableAlias(dtoSqlMap.get("tableAlias"));
                    setSelectByPrimaryKeyDML(dtoSqlMap.get("selectByPrimaryKey"));
                    setInsertDML(dtoSqlMap.get("insert"));
                    setUpdateDML(dtoSqlMap.get("update"));
                    setDeleteDML(dtoSqlMap.get("delete"));
                    setOrderByDML(dtoSqlMap.get("orderBy"));
                    sortFieldOrderByMap.put("default", dtoSqlMap.get("orderBy"));
                } else {
                    logger.debug(METHODNAME, "No XML resource exists for: " + dtoTableName);
                }
            } else {
                logger.error(METHODNAME, "dtoTableName is not set - annotate the DTO with @Table.");
            }
        } catch (Exception e) {
            logger.error(METHODNAME + "Exception on XML table initialization.");
            logger.error(e);
        }
    }

    /**
     * registers query class mapped DML from the DbMGRLocal parsing of the table
     * xml file.
     *
     * @throws MtsException
     */
    private void registerDML() throws MtsException {
        final String METHODNAME = "registerDML ";
        if (xmlTableResource != null) {
            Boolean registerStandardDML = xmlTableResource.isRegisterStandardDMLInterfaces();

            if (registerStandardDML == null) {
                registerStandardDML = false;
            }
            if (registerStandardDML) {
                registerStandardDMLInterfaces(getDtoTableName());
            }
            for (QueryOperation queryOperation : xmlTableResource.getQueryOperations()) {
                if (queryOperation != null) {
                    String tableName = queryOperation.getTableName();
                    if (tableName != null) {
                        tableName = tableName.trim();
                    }
                    String queryClassString = queryOperation.getQueryClass().trim();
                    Class queryClass = null;
                    final String queryDml = queryOperation.getQueryDml().getValue().trim();
                    final String queryDmlTableAlias = queryOperation.getQueryDml().getTableAlias();
                    Boolean rollbackOnNotFound = queryOperation.isRollbackOnNotFound();
                    if (rollbackOnNotFound == null) {
                        rollbackOnNotFound = false;
                    }
                    if (tableName == null || tableName.isEmpty()) {
                        tableName = getDtoTableName();
                    }
                    if (queryClassString != null && !queryClassString.isEmpty()) {
                        try {
                            queryClass = Class.forName(queryClassString);
                        } catch (ClassNotFoundException e) {
                            logger.error(e);
                        }
                    }
                    if (queryClass != null && !queryDml.isEmpty()) {
                        this.registerDML(queryClass, new QueryCallback(tableName, sortFieldOrderByMap, rollbackOnNotFound, queryDmlTableAlias) {
                            @Override
                            protected String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                                return queryDml;
                            }
                        });
                    } else {
                        throw new MtsException("queryClass or queryDml was null: " + queryClass + " - " + queryDml);
                    }
                } else {
                    throw new MtsException("queryOperation was null");
                }
            }
        } else {
            logger.debug("xmlTableResource was null");
        }
    }

    /**
     * Database specific routine for adding boolean values to the named
     * parameter list.
     *
     * @param namedParameters
     * @param key
     * @param value
     */
    protected void addBooleanValueToNamedParameters(MapSqlParameterSource namedParameters, String key, boolean value) {
        if (getDatabaseType() == DatabaseType.ORACLE) {
            namedParameters.addValue(key, value ? "Y" : "N");
        } else {
            namedParameters.addValue(key, value);
        }
    }

    /**
     * Database specific routine for getting the boolean values out of a result
     * set.
     *
     * @param rs
     * @param key
     * @return
     * @throws SQLException
     */
    protected Boolean getBooleanValueFromResultSet(ResultSet rs, String key) throws SQLException {
        Boolean result;
        if (getDatabaseType() == DatabaseType.ORACLE) {
            result = "Y".equalsIgnoreCase(rs.getString(key));
        } else {
            result = rs.getBoolean(key);
        }
        return result;
    }
}
