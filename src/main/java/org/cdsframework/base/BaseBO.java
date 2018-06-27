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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.groups.Default;
import org.apache.commons.io.IOUtils;
import org.cdsframework.annotation.Cached;
import org.cdsframework.annotation.GeneratedValue;
import org.cdsframework.annotation.ParentChildRelationship;
import org.cdsframework.annotation.RowsReturnCountBehavior;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.ejb.local.AppLogMGRLocal;
import org.cdsframework.ejb.local.CacheMGRLocal;
import org.cdsframework.ejb.local.JasperServerMGRLocal;
import org.cdsframework.ejb.local.PropertyMGRLocal;
import org.cdsframework.ejb.local.ReferenceMGRLocal;
import org.cdsframework.enumeration.DTOState;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.enumeration.Operation;
import org.cdsframework.enumeration.PermissionType;
import org.cdsframework.enumeration.QueryType;
import org.cdsframework.exceptions.AuthenticationException;
import org.cdsframework.exceptions.AuthorizationException;
import org.cdsframework.exceptions.CacheLoadException;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.exceptions.ValidationException;
import org.cdsframework.group.CacheAll;
import org.cdsframework.group.Delete;
import org.cdsframework.group.FindAll;
import org.cdsframework.group.None;
import org.cdsframework.group.PrimaryKey;
import org.cdsframework.rs.support.CoreRsConstants;
import org.cdsframework.util.AuthenticationUtils;
import org.cdsframework.util.BrokenRule;
import org.cdsframework.util.ClassUtils;
import org.cdsframework.util.DTOCopy;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.EJBUtils;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.ObjectUtils;
import org.cdsframework.util.StringUtils;
import org.cdsframework.util.comparator.ChildDTOListStateComparator;
import org.cdsframework.util.comparator.ParentChildRelationshipAddUpdateOrderComparator;
import org.cdsframework.util.comparator.ParentChildRelationshipDeleteOrderComparator;
import org.cdsframework.util.support.CoreConstants;

@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public abstract class BaseBO<T extends BaseDTO> {

    protected LogUtils logger;
    @EJB
    private PropertyMGRLocal propertyMGRLocal;
    // Validation factory
    private ValidatorFactory validatorFactory;
    // Security Manager Local
    private BaseSecurityMGR securityMGRLocal;
    // BO Configuration
    protected Configuration<T> configuration;
    // CacheMGR Manager
    @EJB
    private CacheMGRLocal cacheMgrLocal;
    // BO Reference Manager
    @EJB
    private ReferenceMGRLocal referenceMGRLocal;
    // HashMap contain a reference of childDTO's to ChildBO's
    private Map<Class, Configuration<? extends BaseDTO>> childConfigurations = new HashMap<Class, Configuration<? extends BaseDTO>>();
    // Class representing the generics type argument
    protected Class<T> dtoClass;
    // DAO component
    private BaseDAO<T> dao;
    // set flag to debug just this BO...
    private boolean debugBO = false;
    // set flag to determine if the dto typed object is cached
    private boolean cached = false;
    // set flag to determine if the cached DTO is selfReferencing
    private boolean selfReferencing = false;
    // set flag to determine if the dto typed object should be refreshed after an Add or Update
    private boolean refreshOnAddOrUpdate = false;
    private Comparator dtoComparator = null;
    private Map<Class, Comparator> parentChildComparatorMap = new HashMap<Class, Comparator>();
    @EJB
    private JasperServerMGRLocal jasperServerMGRLocal;
    @EJB 
    private AppLogMGRLocal appLogMGRLocal;
    private boolean logExceptions = false;
    
    @PostConstruct
    public void postConstructor() {

        final String METHODNAME = "postConstructor ";

        logger = LogUtils.getLogger(this.getClass());

        // Log begin
        logger.logBegin(METHODNAME);

        try {
            preInitialize();

            // Get the Validator Factory, SecurityMGRLocal and ReferenceMGRLocal
//            propertyMGRLocal = EJBUtils.getPropertyMGRLocal();
            validatorFactory = EJBUtils.getValidatorFactory();
            securityMGRLocal = EJBUtils.getSecurityMGRLocal();
//            referenceMGRLocal = EJBUtils.getReferenceMGRLocal();
//            cacheMgrLocal = EJBUtils.getCacheMGRLocal();

            // Check references
            if (propertyMGRLocal == null) {
                throw new IllegalStateException(METHODNAME + "Uninitialized propertyMGRLocal");
            }

            if (validatorFactory == null) {
                throw new IllegalStateException(METHODNAME + "Uninitialized validatorFactory");
            }

//            if (securityMGRLocal == null) {
//                throw new IllegalStateException(METHODNAME + "Uninitialized securityManagerLocal");
//            }
            if (referenceMGRLocal == null) {
                throw new IllegalStateException(METHODNAME + "Uninitialized referenceMGRLocal");
            }

            if (cacheMgrLocal == null) {
                throw new IllegalStateException(METHODNAME + "Uninitialized cacheMgrLocal");
            }

            logExceptions = ObjectUtils.objectToBoolean(propertyMGRLocal.get(CoreConstants.LOG_EXCEPTIONS));
            logger.debug(METHODNAME, "logExceptions=", logExceptions);
            // Call initializeMain
            this.initializeMain();

            if (cached) {
                try {
                    initializeCache();
                } catch (Exception ex) {
                    logger.error(METHODNAME, "A Exception occurred while calling initializeCache; Message: ", ex);
                }
            }
        } catch (MtsException e) {
            logger.error(METHODNAME, "An MtsException has occurred, Message:", e.getMessage(), e);
            //appLogMGRLocal.queueAppErrorLog(e, null);
        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
    }

    public Class getDtoClass() {
        return dtoClass;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Map<Class, Configuration<? extends BaseDTO>> getChildConfigurations() {
        return childConfigurations;
    }

    // Initializes DAO reference and ChildBO via DtoClassToQueryMap
    // The map contains a list of childDTOs with there appropriate Query classes
    protected void initializeMain() throws MtsException {
        final String METHODNAME = "initializeMain ";
        // Log begin
        logger.logBegin(METHODNAME);

        try {
            if (dtoClass == null) {
                dtoClass = ClassUtils.getTypeArgument(BaseBO.class, getClass());
            }
            if (configuration == null) {
                configuration = new Configuration<T>(dtoClass, this, debugBO);
            }
            dao = EJBUtils.getDtoDao(dtoClass);
            cached = DTOUtils.isCached(dtoClass);
            if (cached) {
                selfReferencing = DTOUtils.getCached(dtoClass).isSelfReferencing();
            }
            refreshOnAddOrUpdate = DTOUtils.isRefreshOnAddOrUpdate(dtoClass);
            dtoComparator = DTOUtils.getDtoComparator(dtoClass);

            for (Entry<Class<? extends BaseDTO>, Class> entry : configuration.getChildQueryMap().entrySet()) {
                if (logger.isDebugEnabled() || debugBO) {
                    logger.debug(
                            dtoClass.getCanonicalName(), " ParentChildRelationship: child dto ",
                            entry.getKey().getCanonicalName(), " maps to query class ",
                            entry.getValue().getCanonicalName());
                }
                registerChildConfiguration(entry.getKey(), entry.getValue());
            }
            initialize();
            if (logger.isDebugEnabled() || debugBO) {
                logConfig();
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    protected void preInitialize() throws MtsException {
        // descendant override
    }

    protected void initialize() throws MtsException {
        // descendant override
    }

    public void setDebugBO(boolean debugBO) {
        this.debugBO = debugBO;
    }

    public BaseDAO<T> getDao() {
        return dao;
    }

    public void setDao(BaseDAO dao) {
        this.dao = dao;
    }

    public PropertyMGRLocal getPropertyMGRLocal() {
        return propertyMGRLocal;
    }

    // Used to register the child BO's for use by post finder, post add, update, remove methods
    private <C extends BaseDTO> void registerChildConfiguration(Class<C> childDto, Class queryClass) {
        final String METHODNAME = "registerChildBO ";

        // Log begin
        logger.logBegin(METHODNAME);
        try {
            if (logger.isDebugEnabled()) {
                logger.debug(METHODNAME + " queryClass=" + queryClass);
            }
            Configuration childConfiguration;
            // Store the config
            if (dtoClass == childDto) {
                logger.debug(METHODNAME, "The Parent is a child, register self");
                childConfiguration = new Configuration<C>((BaseBO<C>) this, childDto, queryClass, debugBO);
            } else {
                logger.debug(METHODNAME, "The Parent is not a child, get access to the child and register it");
                childConfiguration = new Configuration<C>(childDto, queryClass, debugBO);
            }
            childConfigurations.put(queryClass, childConfiguration);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    protected void processDTOCreateLastModDateId(T baseDTO, Operation operation, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        final String METHODNAME = "processDTOCreateLastModDateId ";
        logger.logBegin(METHODNAME);
        try {

            // If its new or updated set the LastModId
            //
            if (operation != Operation.FIND && (baseDTO.isNew() || baseDTO.isUpdated())) {
                // Get the date
                Date now = new Date();
                baseDTO.setLastModId(sessionDTO.getUserDTO().getUsername());
                baseDTO.setLastModDatetime(now);
                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, "baseDTO.setLastModDatetime(now)=", now);
                }
                if (baseDTO.isNew()) {
                    if (baseDTO.getCreateDatetime() == null) {
                        baseDTO.setCreateDatetime(now);
                    }
                    if (StringUtils.isEmpty(baseDTO.getCreateId())) {
                        baseDTO.setCreateId(sessionDTO.getUserDTO().getUsername());
                    }
                }
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    private void processAuditId(T baseDTO) {
        final String METHODNAME = "processAuditId ";
//        logger.debug(METHODNAME, "baseDTO.getDTOStates()=", baseDTO.getDTOStates(), "baseDTO.isAudit()=", baseDTO.isAudit());

        // Handle Audit Id
        if (baseDTO != null && baseDTO.isAudit()) {
//            logger.debug(METHODNAME, "baseDTO.getAuditId()=", baseDTO.getAuditId());

            if (baseDTO.isNew()) {
//            if (baseDTO.isNew() || baseDTO.isUpdated()) {        
                // Audit Id must be prepoluated, use randomuuid to populate
//                logger.debug(METHODNAME, " before baseDTO.getAuditId()=", baseDTO.getAuditId());
                if (StringUtils.isEmpty(baseDTO.getAuditId())) {
                    baseDTO.setAuditId(StringUtils.getHashId());
                }
//                logger.debug(METHODNAME, " after baseDTO.getAuditId()=", baseDTO.getAuditId());

            }
        }
    }

    private void processDTOAutoKeyMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "processDTOPrimaryKeyMain ";
        logger.logBegin(METHODNAME);
        try {
            if (baseDTO != null && baseDTO.isNew()) {
                if (!baseDTO.isNoId()) {
                    // Call descendant level 
                    preProcessDTOAutoKey(baseDTO, queryClass, sessionDTO, propertyBagDTO);
                    // Set by other source, client etc
                    if (!baseDTO.hasPrimaryKey()) {
                        if (baseDTO.isPKGeneratedSourceAuto()) {
                            logger.debug(METHODNAME, " before autoSetPrimaryKeys(), name=", baseDTO.getClass().getName());
                            boolean result = baseDTO.autoSetPrimaryKeys();
                            logger.debug(METHODNAME, "Evaluating auto id generation: ", result, " - ", baseDTO.getClass().getSimpleName());
                        } else if (baseDTO.isPKGeneratedSourceSequence()) {
                            List<Field> fields = DTOUtils.getPKGeneratedSourceSequenceFields(baseDTO.getClass());
                            if (fields.size() == 1) {
                                Field field = fields.get(0);
                                GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);
                                if (generatedValue != null) {
                                    String dataSource = generatedValue.dataSource();
                                    if (dataSource != null) {
                                        Object sequenceValue = dao.getNewPrimaryKey(dataSource, sessionDTO, field.getType());
                                        DTOUtils.setPrimaryKey(baseDTO, sequenceValue);
                                    }
                                }
                            } else {
                                for (Field field : DTOUtils.getPKGeneratedSourceSequenceFields(baseDTO.getClass())) {
                                    GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);
                                    if (generatedValue != null) {
                                        String dataSource = generatedValue.dataSource();
                                        if (dataSource != null) {
                                            Object sequenceValue = dao.getNewPrimaryKey(dataSource, sessionDTO, field.getType());
                                            field.setAccessible(true);
                                            try {
                                                field.set(baseDTO, sequenceValue);
                                                if (logger.isDebugEnabled()) {
                                                    logger.debug(METHODNAME, "sequence set for ", field.getName(), " = ", baseDTO.getPrimaryKey(), "(", sequenceValue, ")");
                                                }
                                            } catch (IllegalAccessException e) {
                                                logger.error(e);
                                                throw new MtsException(e.getMessage());
                                            }
                                        } else {
                                            logger.warn("Sequence source was null for field: " + field.getName());
                                        }
                                    } else {
                                        logger.warn("GeneratedValue came back null from field: " + field.getName());
                                    }
                                }
                            }
                        }
                    }
                    postProcessDTOAutoKey(baseDTO, queryClass, sessionDTO, propertyBagDTO);
                }
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    protected void postProcessDTOAutoKey(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException,
            AuthenticationException, AuthorizationException {
    }

    protected void preProcessDTOAutoKey(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException,
            AuthenticationException, AuthorizationException {
    }

    private void processBeginMain(T baseDTO, Operation operation, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "processBeginMain ";
        logger.logBegin(METHODNAME);
        try {
            // Process the DTOState
            processDTOState(baseDTO, operation, sessionDTO);

            // Set Create/LastMod Date timestamps
            processDTOCreateLastModDateId(baseDTO, operation, sessionDTO, propertyBagDTO);

            // Call descendant level processing
            processBegin(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    // For descendant override
    protected void processBegin(T baseDTO, Operation operation, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "processBegin ";
    }

    // Main line processDTOState all Main methods follow
    private void processDTOState(T baseDTO, Operation operation, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "processDTOState ";

        // Log begin
        logger.logBegin(METHODNAME, baseDTO);
        try {

            if (baseDTO != null) {
                logger.debug(METHODNAME, "baseDTO.getClass().getName()=", baseDTO.getClass().getName());
                // Find DTO's may have isNew, the reset will take care of it
                if (operation == Operation.FIND) {
//                    baseDTO.resetDTOState();
                    DTOUtils.unsetDTOState(baseDTO);
                }

                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, "operation=", operation);
                    logger.debug(METHODNAME, "sessionDTO.getUserId()=", sessionDTO.getUserDTO().getUserId());
                    logger.debug(METHODNAME, "DTOState= ", baseDTO.getDTOStates());
                }

                // Match Operation to isNew, isDeleted, isUpdated flags
                if (operation == Operation.ADD) {
                    if (!baseDTO.isNew()) {
                        throw new MtsException(operation + " requires " + baseDTO + ".isNew to be true, state= " + baseDTO.getDTOStates());
                    }
                } else if (operation == Operation.DELETE) {
                    if (!baseDTO.isDeleted()) {
                        throw new MtsException(operation + " requires " + baseDTO + ".isDeleted to be true, state= " + baseDTO.getDTOStates());
                    }
                } else if (operation == Operation.UPDATE) {
                    // BaseDTO doesn't need to be Updated but it also can't be isNew or isDeleted
                    if (baseDTO.isNew() || baseDTO.isDeleted()) {
                        throw new MtsException(operation + " requires " + baseDTO + ".isUpdated to be true or false, state= " + baseDTO.getDTOStates());
                    }
                }

                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, "baseDTO.getCreateId()=", baseDTO.getCreateId());
                    logger.debug(METHODNAME, "baseDTO.getCreateDatetime()=", baseDTO.getCreateDatetime());
                    logger.debug(METHODNAME, "baseDTO.getLastModId()=", baseDTO.getLastModId());
                    logger.debug(METHODNAME, "baseDTO.getLastModDatetime()=", baseDTO.getLastModDatetime());
                }

            }

        } finally {
            // Log end
            logger.logEnd(METHODNAME, baseDTO);
        }
    }

    protected void processRowsReturned(T baseDTO, Operation operation, Class queryClass,
            SessionDTO sessionDTO, int rowsReturned, PropertyBagDTO propertyBagDTO) throws MtsException {
        if (rowsReturned == 0) {
            RowsReturnCountBehavior rowsReturnCountBehaviorValue = DTOUtils.getRowsReturnCountBehaviorValue(baseDTO.getClass());
            boolean ignoreException = false;
            if (rowsReturnCountBehaviorValue != null) {
                switch (operation) {
                    case DELETE:
                        ignoreException = rowsReturnCountBehaviorValue.isDeleteCountIgnored();
                        break;
                    case UPDATE:
                        ignoreException = rowsReturnCountBehaviorValue.isUpdateCountIgnored();
                        break;
                    case ADD:
                        ignoreException = rowsReturnCountBehaviorValue.isInsertCountIgnored();
                        break;
                    default:
                        ignoreException = false;
                }
            }

            if (!ignoreException) {
                throw new MtsException("The baseDTO.getPrimaryKey()=" + baseDTO.getPrimaryKey()
                        + " class= " + baseDTO.getClass().getSimpleName()
                        + " queryClass= " + queryClass
                        + " operation " + operation + " could not be achieved. The data may have changed between the"
                        + " time it was retrieved and the time the operation was executed. Please refresh your data"
                        + " and try again.");
            }
        }
    }

    private T addOrUpdate(T baseDTO, List<Class> childClassDTOs, Operation operation, Class queryClass, List<Class> validationClasses,
            boolean skipCheckAuthority, List<T> childrenDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {

        long start = System.nanoTime();
        final String METHODNAME = "addOrUpdate ";

        logger.logBegin(METHODNAME);
        if (baseDTO == null) {
            throw new MtsException(METHODNAME + "baseDTO is null!");
        }
        T resultDTO = null;

        try {

            // Perform all the standard processing, each of these execute descendant level calls
            if (!skipCheckAuthority) {
                this.checkAuthorityMain(baseDTO, operation, queryClass, sessionDTO, propertyBagDTO);
            }

            if (operation == Operation.UPDATE) {
                // RefreshDTO can be used independant of an RSClient (Client can force a refresh of the DTO)
                boolean refreshDTO = ObjectUtils.objectToBoolean(propertyBagDTO.get(CoreRsConstants.REFRESH_DTO));
                // RSClient indicates that a rest client is aware of both the DTOState and the PropertyChangeEventMap
                boolean rsClient = ObjectUtils.objectToBoolean(propertyBagDTO.get(CoreRsConstants.RS_CLIENT));

                logger.debug(METHODNAME, "baseDTO.getClass().getSimpleName()=", baseDTO.getClass().getSimpleName(), " refreshDTO=", refreshDTO, " rsClient=", rsClient);
                if (rsClient || refreshDTO) {
                    if (logger.isDebugEnabled()) {                    
                        logger.debug(METHODNAME, "incoming baseDTO.getDTOStates()=",  baseDTO.getDTOStates(), 
                            " incoming baseDTO.getPrimaryKey()=",  baseDTO.getPrimaryKey(), 
                            " incoming baseDTO.getPropertyChangeEventMap()=",  baseDTO.getPropertyChangeEventMap());
                    }
                    //
                    // Only refresh is primary key is valued, NEW Objects shouldn't have a Primary Key valued
                    // Primary Keys are typically generated, there may be exceptions in the future
                    //
                    // Alternative is to check if its baseDTO.isUpdated() but this will only be set for RSClients
                    // NON RSClients (curl, javascript, etc) will always be NEW, 
                    // possibly add flag in RSClient to identify that this is an RSClient submission
                    //
                    
                    // RS Client ?
                    if ( rsClient ) { 
                        // PropertyChangeEventMap is NOT serialized (reverted in BaseDTO) see notes below
                        // PropertyChangeEventMap will be empty for rsClient until it can be serialized
                        //
                        // RS Clients need more work to support the contents of PropertyChangeEventMap
                        //
                        // This issue is that the value in the map is an object with an unknown type via JSON
                        // 
                        // Dates, Enumerations, Longs, Integers, ReferenceDTO's except for Strings need to be deserialized into the
                        // object they represent. 
                        //
                        // We need to convert the object into its type (use the propertyName to identify the type)
                        // Give the type to a deserializer so that it can reconstruct the object and assign it back into
                        // the maps value
                        //
                        // For Javascript clients this would work for all but complex types
                        // If there is a ReferenceDTO the javascript client would need to store all the data for the 
                        // ReferenceDTO ie state: { code: "NY, description: "New York State" } etc
                          
                        if ( baseDTO.isUpdated() && baseDTO.getPropertyChangeEventMap().isEmpty() ) {
                            refreshDTO = true ;
                        }
                        else {
                            // Short circuit, will occur when the DTO has not changed
                            refreshDTO = false;
                        }
                    }
                    logger.debug(METHODNAME, "refreshDTO=", refreshDTO);
                    
                    if (refreshDTO ) {
                        logger.debug(METHODNAME, "attempting to call findByPrimaryKeyMain");
                        
                        // This will throw a not found exception if the client tries to UPDATE a truely NEW DTO
                        T latestDTO = findByPrimaryKeyMain(baseDTO, new ArrayList<Class>(), sessionDTO, propertyBagDTO);
                        baseDTO = (T) DTOCopy.copyProperty(baseDTO, latestDTO);
                        logger.debug(METHODNAME, "refreshed baseDTO.getDTOStates()=",  baseDTO.getDTOStates(), 
                                " refreshed baseDTO.getPropertyChangeEventMap()=",  baseDTO.getPropertyChangeEventMap());
                    }
                    //
                    // Its possible that after the data is refreshed NOTHING is updated at this level.
                    // If property listener was configured and nothing exists in the PropertyChangeEventMap 
                    // the logic could force the DTO to the UNSET state, saving a DAO operation, but still process the child
                    // 
                    // Add Entity (dynamic update flag to indicate that the DTO is fully configured
                    //
                }
            }
            
            //
            // Validate that the ChildrenDTOs to the Registered BO
            //
            // This routine can be turned off after a successful implementation, depending on the cost of this routine, 
            // we could add a flag that tells us that this DTO has been validated.
            //
            checkChildrenDTOs(baseDTO, operation);
            
            //
            // Call Descendant method to change Operation and Query Class, decendant will need to change DTOState as well
            //
            Map<String, Object> operationInfo = preAddOrUpdate(baseDTO, operation, queryClass, sessionDTO, propertyBagDTO);
            if (operationInfo != null) {
                queryClass = (Class) operationInfo.get("queryClass");
                operation = (Operation) operationInfo.get("operation");
                baseDTO = (T) operationInfo.get("baseDTO");
                // If UNSET return
                if (baseDTO.getOperationDTOState() == DTOState.UNSET) {
                    return baseDTO;
                }
            }

            // Sets the Audit Id if the DTO is being audited
            processAuditId(baseDTO);

            // Generate automatic keys
            processDTOAutoKeyMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);

            // Begin the process
            if (baseDTO.isNew()) {
                processBeginMain(baseDTO, Operation.ADD, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            } else {
                processBeginMain(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            }

            // New
            if (baseDTO.isNew()) {
                // PreAdd processing
                preAddMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
            } // Updated ?
            else if (baseDTO.isUpdated()) {
                // PreUpdate processing
                preUpdateMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
            }

            // Validation
            if (baseDTO.isNew() || baseDTO.isUpdated()) {
                if (baseDTO.isNew()) {
                    validateMain(baseDTO, Operation.ADD, queryClass, validationClasses, sessionDTO, propertyBagDTO);
                } else {
                    validateMain(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
                }
            }

            //
            // Add or Update any reference DTO's flagged as add or updateable
            //
            // To Do: Determine if updateable referenceDTOs exist prior to calling this routine
            //
            if (baseDTO.isReferenceDTOsExist()) {
                referenceMGRLocal.addOrUpdateReferenceDTO(baseDTO, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            }

            // Track rows returned from dao
            int rowsReturned = 0;
            if (baseDTO.isNew()) {
                // Perform the add
                rowsReturned = dao.add(baseDTO, queryClass, sessionDTO, propertyBagDTO);
            } else if (baseDTO.isUpdated()) {
                // Perform the update
                rowsReturned = dao.update(baseDTO, queryClass, sessionDTO, propertyBagDTO);
            } else {
                // Force a refresh
                rowsReturned = 1;
            }
            
//            logger.debug(METHODNAME, "rowsReturned=", rowsReturned);

            // rowsReturned = 0 indicates that the add or updated failed
            if (rowsReturned > 0) {
                // Set the resultDTO
                resultDTO = baseDTO;

                //
                // Call findByPrimaryKey after a successful dao call to get the latestDTO
                // This gets the latest data from the database, its possible that there are values
                // initialized in triggers or in the DAO which would not be available to the logic
                // that follows. Therefore a call to findByPrimaryKeyMain will bring back the
                // latest data.
                //
                // Called only if the dtoType is cached or requires a refreshAfterUpdate
                if (refreshOnAddOrUpdate || cached) {
                    T latestDTO = dao.findByPrimaryKey(baseDTO, sessionDTO, propertyBagDTO);

                    // This step transfers the childrenDTOs from the dto that is being processed,
                    // to the latestDTO.
                    //
                    // Note: This was always done for the top level dto (ie. parentDTO)
                    // and due to the unification of add/update the childDTO will now inherit
                    // this behavior.
                    //
                    // If it becomes necessary a flag can be added in the argument list to
                    // control this behavior when executed from the child level processing.
                    resultDTO = transferChildren(latestDTO, operation, queryClass, baseDTO, propertyBagDTO);

                    //
                    // Locate any referenceDTO that exists on the resultDTO
                    // This is necessary step since the prior step transferChildren
                    // has not transfered the referenceDTOs on the dto being processed.
                    //
                    // Note: This would not be necessary if the transferChildren transferred the
                    // referenceDTO's on the dto being processed to the latestDTO.
                    //
                    // The added benefit in this step is that the processes the follow will
                    // receive the latest referenceDTOs. Some processes that follow require
                    // access to referenceDTO in order to achieve there goal.
                    //
                    // This is an additional step for the childDTO as a result of
                    // the transferChildren operation. The last iteration of child processing
                    // did not refresh the childDTO from the database via dao.findByPrimaryKey
                    // But it did call dao.findByPrimaryKey to ensure that the add or update
                    // operation succeeded.
                    // This is an additional cost to childDTO processing, but cached data
                    // will not incur additional cost. Non cached data will.
                    //
                    if (executeFindReferenceDTOMain(resultDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO)) {
                        // Call the Reference MGR Local
                        referenceMGRLocal.findReferenceDTO(resultDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
                    } else {
                        propertyBagDTO.getPropertyMap().remove("recursiveNodeEncountered");
                    }
                }

                // New ?
                if (baseDTO.isNew()) {
                    // Call any postAddMain processing
                    postAddMain(resultDTO, queryClass, sessionDTO, propertyBagDTO);
                } // Updated
                else if (baseDTO.isUpdated()) {
                    // Call any postUpdateMain processing
                    postUpdateMain(resultDTO, queryClass, sessionDTO, propertyBagDTO);
                }

                // Based on the operation call the appropriate child process
                if (operation == Operation.ADD) {
                    // Add the children
                    addChildrenMain(resultDTO, childClassDTOs, queryClass, validationClasses, sessionDTO, propertyBagDTO);
                } else if (operation == Operation.UPDATE) {
                    // Update the children
                    updateChildrenMain(resultDTO, childClassDTOs, queryClass, validationClasses, sessionDTO, propertyBagDTO);
                }

                // Refresh autoRetrieve flagged childDTOs or RefreshChildClassDTOs in the propertyBagDTO
                findChildrenMain(resultDTO, queryClass, validationClasses, new ArrayList<Class>(), sessionDTO, propertyBagDTO);

                // If it cached, refresh the cache with the new instance
                if (cached) {
                    initializeCache();
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug(METHODNAME, "Trying to refresh: ", resultDTO.getPrimaryKey());
                        logger.debug(METHODNAME, "baseDTO dtoState: ", resultDTO.getDTOState());
                        logger.debug(METHODNAME, "operation: ", operation);
                    }
                    if (baseDTO.isNew()) {
                        cacheMgrLocal.save(resultDTO, Operation.ADD);
                    } else if (baseDTO.isUpdated() || operation == Operation.UPDATE) {
                        cacheMgrLocal.save(resultDTO, Operation.UPDATE);
                    }
                }

            } else {
                // Process rowsReturned, contains error logic which can be overriden in descendant BO
                processRowsReturned(baseDTO, operation, queryClass, sessionDTO, rowsReturned, propertyBagDTO);
            }

            // Update the childDTO reference in the childrenDTOs list
            if (childrenDTOs != null) {
                updateChildReference(baseDTO, resultDTO, childrenDTOs);
            }

            processEndMain(resultDTO, childClassDTOs, operation, queryClass, sessionDTO, propertyBagDTO);

        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);                                                
            logger.logEnd(METHODNAME);
        }
        return resultDTO;
    }

    // newInstance called by MGR EJB
    public T newInstanceMain(SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException,
            AuthenticationException, AuthorizationException {
        final String METHODNAME = "newInstanceMain ";
        try {
            return newInstance(sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, null, sessionDTO, propertyBagDTO);
            throw e;            
        }            
    }

    protected T newInstance(SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException,
            AuthenticationException, AuthorizationException {
        T resultDTO = null;
        try {
            resultDTO = dtoClass.newInstance();
        } catch (IllegalAccessException e) {
            throw new MtsException(e.getMessage());
        } catch (InstantiationException e) {
            throw new MtsException(e.getMessage());
        }
        return resultDTO;
    }

//    // Should eventually take MANDATORY off to allow addMain to function with or without a Transaction
//    public T addMainSupports(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
//            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
//            AuthenticationException, AuthorizationException {
//        return addMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
//    }
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public T addMainNew(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {
        return addMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // addMain called by MGR EJB
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public T addMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {
        long start = System.nanoTime();
        final String METHODNAME = "addMain ";
        if (queryClass == null) {
            throw new MtsException("queryClass cannot be null");
        }

        // Log begin
        logger.logBegin(METHODNAME);
        T resultDTO = null;

        try {
            if (configuration.isAddAllowed()) {
                List<Class> validationClasses = new ArrayList<Class>();
                validationClasses.add(Default.class);
                validationClasses.add(PrimaryKey.class);
                resultDTO = addOrUpdate(baseDTO, DTOUtils.getChildClassDTOs(baseDTO, new ArrayList<Class>()), Operation.ADD, queryClass,
                        validationClasses, false, null, sessionDTO, propertyBagDTO);
            }
        }
        catch (ValidationException | NotFoundException | ConstraintViolationException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        } finally {
            // Log end
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);                                                
            logger.logEnd(METHODNAME);
        }
        return resultDTO;
    }

    protected Map<String, Object> preAddOrUpdate(T baseDTO, Operation operation, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {
        return null;
    }

    private void checkChildrenDTOs(T baseDTO, Operation operation) throws MtsException {
        final String METHODNAME = "checkChildrenDTOs ";
        if (baseDTO != null) {
            if (logger.isDebugEnabled()) {
                logger.debug(METHODNAME, "on ", baseDTO.getClass().getSimpleName());
            }
            for (Map.Entry<Class, List<BaseDTO>> entry : baseDTO.getChildDTOMap().entrySet()) {
                //
                // On an ADD operation go through the children and make sure they are all new
                // Sending in childDTOs with other then new causes an error and notifies caller
                //
                if (operation == Operation.ADD) {
                    List<BaseDTO> childrenDTOs = entry.getValue();
                    for (BaseDTO childDTO : childrenDTOs) {
                        if (!childDTO.isNew()) {
                            String errorMessage = logger.error(METHODNAME, "The ", this.getDtoClass().getSimpleName(),
                                    " has a ", baseDTO.getDTOState(), " DTOState and has a ", childDTO.getClass().getSimpleName(),
                                    " child with a ", childDTO.getDTOState(), " DTOState.",
                                    " When an ADD operation is invoked, both the Parent and the child must be NEW or NEWMODIFIED.");
                            throw new MtsException(errorMessage);
                        }
                    }
                } //
                // On an UPDATE or DELETE Opertation
                // Review Parents Primary key to ensure it matches the childs Foreign key
                // For Child DTOs that are UPDATED, DELETED or even UNSET
                // This ensures the caller hasnt accidentally cominged children from different parents
                //
                else if (operation == Operation.UPDATE || operation == Operation.DELETE) {
                }

                Class queryClass = entry.getKey();
                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, " checking queryClass ", queryClass.getSimpleName());
                    for (Class clazz : childConfigurations.keySet()) {
                        logger.debug(METHODNAME, " checking queryClass ", queryClass.getSimpleName(), " against ", clazz.getSimpleName());
                    }
                }
                Configuration registeredChildBO = childConfigurations.get(queryClass);
                if (registeredChildBO == null) {
                    String errorMessage = logger.error(METHODNAME, "The ChildrenDTOs queryClass ", queryClass, " was not registered with a Business Object. ",
                            "Did you forget to registered a Business Object on ", this.getDtoClass());
                    throw new MtsException(errorMessage);
                }
            }
        } else {
            logger.error(METHODNAME, "baseDTO is null!");
        }
    }

    //
    // Method used to add children, delegates to ChildBO's addChildren
    //
    private void addChildrenMain(T parentDTO, List<Class> childClassDTOs, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        long start = System.nanoTime();
        final String METHODNAME = "addChildrenMain ";
        // Log begin
        logger.logBegin(METHODNAME);
        try {
            if (logger.isDebugEnabled() || debugBO) {
                logger.debug(METHODNAME, " childBOs size: ", configuration.getChildQueryClassAddUpdateOrder().size());
            }
            for (Class childQueryClass : configuration.getChildQueryClassAddUpdateOrder()) {
                Configuration registeredChildBO = childConfigurations.get(childQueryClass);

                // Get the Registered Child BO
                BaseBO childBO = registeredChildBO.getDtoBo();

                if (logger.isDebugEnabled() || debugBO) {
                    logger.debug(METHODNAME, "childBO", childBO, " childBO.getConfiguration().isAddsChild()= ", childBO.getConfiguration().isAddsChild());
                }

                // Get the child configuration
                Configuration childConfiguration = childBO.getConfiguration();

                // Can the Add the children ?
                if (childConfiguration.isAddsChild() && !childConfiguration.isVanity(parentDTO.getClass())) {

                    Class childBOQueryClass = registeredChildBO.getQueryClass();
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug(METHODNAME, " childBOQueryClass: ", childBOQueryClass);
                    }

                    List<BaseDTO> childrenDTOs = parentDTO.getChildrenDTOs(childBOQueryClass, DTOState.NEW);
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug(METHODNAME, " childrenDTOs size: ", childrenDTOs.size());
                    }

                    if (logger.isDebugEnabled() || debugBO) {
                        logChildren(METHODNAME, parentDTO, childBO, childrenDTOs, childBOQueryClass);
                    }

                    // Is Empty
                    if (!childrenDTOs.isEmpty()) {
                        // Call ChildBO.addChildren
                        childBO.addChildren(parentDTO, childrenDTOs, childClassDTOs, queryClass, childBOQueryClass, validationClasses, sessionDTO, propertyBagDTO);
                    }
                }
            }
        } finally {
            // Log end
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);                                                
            logger.logEnd(METHODNAME);
        }
    }

    private void updateChildReference(T originalDTO, T latestDTO, List<T> childrenDTOs)
            throws MtsException {
        final String METHODNAME = "updateChildReference ";
        logger.logBegin(METHODNAME);

        try {
            //
            // Since the addOrUpdate returns a completely refreshed resultDTO
            // replace the reference of the childDTO in the arrayList
            // with the resultDTO
            //
            if (refreshOnAddOrUpdate || cached) {
                int index = childrenDTOs.indexOf(originalDTO);
                if (index >= 0) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(METHODNAME, "index= ", index, " originalDTO.getUuid()= ", originalDTO != null ? originalDTO.getUuid() : null,
                                "childrenDTOs.get(", index, ").getUuid()= ", index > -1 ? childrenDTOs.get(index).getUuid() : null);
                    }
                    childrenDTOs.set(index, latestDTO);
                } else {
                    throw new MtsException(METHODNAME + "Could not locate originalDTO to transfer, index="
                            + index + " originalDTO.getPrimaryKey()=" + originalDTO.getPrimaryKey());
                }
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    // addChildren called by ParentBO EJB
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void addChildren(BaseDTO parentDTO, List<T> childrenDTOs, List<Class> childClassDTOs, Class queryClass, Class childBOQueryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {
        long start = System.nanoTime();
        final String METHODNAME = "addChildren ";

        // Log begin
        logger.logBegin(METHODNAME);
        try {
            if (configuration.isAddsChild()) {

                // Any children
                if (childrenDTOs.size() > 0) {
                    // set the parent on the propertyBag in the event a child needs access to the parent
                    propertyBagDTO.setParentDTO((Class<BaseDTO>) parentDTO.getClass(), parentDTO);

                    // Iterate through all the children
                    Iterator<T> iterator = childrenDTOs.iterator();

                    // Set the childs foreign from the parents primary key
                    Object parentPrimaryKey = parentDTO.getPrimaryKey();
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug(METHODNAME, "PARENT PRIMARY KEY: ", parentPrimaryKey);
                    }

                    while (iterator.hasNext()) {
                        // Get the child
                        T childDTO = iterator.next();

                        // New
                        if (childDTO.isNew()) {
                            // Set the ForeignKey on the childDTO
                            childDTO.setForeignKey(parentDTO.getClass(), parentPrimaryKey);
                        }
                        addOrUpdate(childDTO, childClassDTOs, Operation.ADD, queryClass, validationClasses, false,
                                (List<T>) parentDTO.getChildrenDTOs(childBOQueryClass), sessionDTO, propertyBagDTO);
                    }
                }
            } else {
                logger.error(METHODNAME, "was called on ", this, " and should not have been.",
                        " See this.configuration.isAddsChild()=", this.configuration.isAddsChild());
            }
        } finally {
            // Log end
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);                                                
            logger.logEnd(METHODNAME);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public T updateMainNew(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {
        return updateMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // updateMain called by MGR EJB
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public T updateMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {

        final String METHODNAME = "updateMain ";
        if (queryClass == null) {
            throw new MtsException("queryClass cannot be null");
        }

        // Log begin
        logger.logBegin(METHODNAME);
        T resultDTO = null;

        try {
            if (configuration.isUpdateAllowed()) {
                List<Class> validationClasses = new ArrayList();
                validationClasses.add(Default.class);
                validationClasses.add(PrimaryKey.class);
                resultDTO = addOrUpdate(baseDTO, DTOUtils.getChildClassDTOs(baseDTO, new ArrayList<Class>()), Operation.UPDATE, queryClass,
                        validationClasses, false, null, sessionDTO, propertyBagDTO);
            }
        }
        catch (ValidationException | NotFoundException | ConstraintViolationException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
            
        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
        return resultDTO;
    }

    //
    // Method used to update children, delegates to ChildBO's updateChild
    //
    private void updateChildrenMain(T parentDTO, List<Class> childClassDTOs, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "updateChildren ";

        // Log begin
        logger.logBegin(METHODNAME);

        try {
            for (Class childQueryClass : configuration.getChildQueryClassAddUpdateOrder()) {
                Configuration registeredChildBO = childConfigurations.get(childQueryClass);
                BaseBO childBO = registeredChildBO.getDtoBo();

                if (logger.isDebugEnabled() || debugBO) {
                    logger.debug(METHODNAME, "childBO", childBO, " childBO.getConfiguration().isUpdatesChild()= ", childBO.getConfiguration().isUpdatesChild());
                }

                // Get the child configuration
                Configuration childConfiguration = childBO.getConfiguration();

                // Can the Update the children ?
                if (childConfiguration.isUpdatesChild() && !childConfiguration.isVanity(parentDTO.getClass())) {

                    Class childBOQueryClass = registeredChildBO.getQueryClass();
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug(METHODNAME, " childBOQueryClass: ", childBOQueryClass);
                    }

                    List<BaseDTO> childrenDTOs = parentDTO.getChildrenDTOs(childBOQueryClass);
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug(METHODNAME, " childrenDTOs size: ", childrenDTOs.size());
                    }

                    if (logger.isDebugEnabled() || debugBO) {
                        logChildren(METHODNAME, parentDTO, childBO, childrenDTOs, childBOQueryClass);
                    }

                    // Call ChildBO.updateChildren
                    if (!childrenDTOs.isEmpty()) {
                        childBO.updateChildren(parentDTO, childrenDTOs, childClassDTOs, queryClass, childBOQueryClass, validationClasses, sessionDTO, propertyBagDTO);
                    }

                }
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
    }

    // updateChildren called by ParentBO EJB
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void updateChildren(BaseDTO parentDTO, List<T> childrenDTOs, List<Class> childClassDTOs, Class queryClass, Class childBOQueryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {
        final String METHODNAME = "updateChildren ";

        // Log begin
        logger.logBegin(METHODNAME);

        try {

            // Calls the BO with the parent so that less BO calls are made. Otherwise a
            // BO add/update or remove call would be necessary for each of the children.
            // Since remove may have children it is necessary to call the BO to remove the child
            // so that it can handle the cascade DELETE processing
            //
            if (configuration.isUpdatesChild()) {
                if (childrenDTOs.size() > 0) {
                    // set the parent on the propertyBag in the event a child needs access to the parent
                    propertyBagDTO.setParentDTO((Class<BaseDTO>) parentDTO.getClass(), parentDTO);
                    Collections.sort(childrenDTOs, new ChildDTOListStateComparator());

                    // Set the childs foreign from the parents primary key
                    Object parentPrimaryKey = parentDTO.getPrimaryKey();
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug(METHODNAME, "PARENT PRIMARY KEY: ", parentPrimaryKey);
                    }

                    // Process the children
                    Iterator<T> iterator = childrenDTOs.iterator();
                    while (iterator.hasNext()) {
                        // Get the child
                        T childDTO = iterator.next();

                        // Handle setting the primary key
                        if (childDTO.isNew()) {
                            // Set the ForeignKey on the childDTO
                            childDTO.setForeignKey(parentDTO.getClass(), parentPrimaryKey);
                        }

                        // If its NOT DELETED
                        if (!childDTO.isDeleted()) {
                            // If the child isNew or isUpdated or (NOT deleted (Called to refresh if flag is set))
                            addOrUpdate(childDTO, childClassDTOs, Operation.UPDATE, queryClass, validationClasses, false,
                                    (List<T>) parentDTO.getChildrenDTOs(childBOQueryClass), sessionDTO, propertyBagDTO);
                        } else {
                            // It's been deleted, call deleteMain so that the baseDTO is processed following remove logic.
                            // Delete logic ensures that the childDTOs are deleted before there parents.
                            // Since this object is a child its children will be deleted first if they exist
                            deleteMain(childDTO, Delete.class, sessionDTO, propertyBagDTO);
                            iterator.remove();
                        }
                    }
                }
            } else {
                logger.error(METHODNAME, "was called on ", this, " and should not have been.",
                        " See this.configuration.isUpdatesChild()=", this.configuration.isUpdatesChild());
            }

        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void deleteMainNew(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,
            AuthenticationException, AuthorizationException {
        deleteMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }
    
    // deleteMain called by MGR EJB
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void deleteMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException,
            AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "deleteMain ";
        Operation operation = Operation.DELETE;
        List<Class> validationClasses = new ArrayList();
        validationClasses.add(PrimaryKey.class);
        if (queryClass == null) {
            throw new MtsException("queryClass cannot be null");
        }

        // Log begin
        logger.logBegin(METHODNAME);
        try {
            if (configuration.isDeleteAllowed()) {

                // Perform all the standard processing, each of these execute descendant level calls
                this.checkAuthorityMain(baseDTO, operation, queryClass, sessionDTO, propertyBagDTO);

                // Validate the ChildrenDTOs to the Registered BO
                checkChildrenDTOs(baseDTO, operation);

                // Sets the Audit Id if the DTO is being audited
                processAuditId(baseDTO);

                // Begin the process
                processBeginMain(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);

                // PreAdd processing
                preDeleteMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);

                // Validation
                validateMain(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);

                // Delete the child first
                deleteChildrenMain(baseDTO, queryClass, validationClasses, sessionDTO, propertyBagDTO);

                // Perform the remove
                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, "about to delete baseDTO.getClass().getSimpleName()=", baseDTO.getClass().getSimpleName(),
                            " baseDTO.getPrimaryKey()=", baseDTO.getPrimaryKey());
                }

                // Added logic to handle delete without a primary key, occurs when client deletes a New DTO
                Object primaryKey = baseDTO.getPrimaryKey();
                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, "primaryKey=", primaryKey);
                }
                int rowsReturned = 0;
                if (primaryKey != null) {
                    rowsReturned = dao.delete(baseDTO, queryClass, sessionDTO, propertyBagDTO);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, "rowsReturned=", rowsReturned);
                }

                // Delete reference DTO's flagged as deleteable
                // Moved from above since a ReferenceDTO may be a related to the BaseDTO that's being deleted (a parent to the DTO being deleted)
                if (baseDTO.isReferenceDTOsExist()) {
                    referenceMGRLocal.deleteReferenceDTO(baseDTO, queryClass, validationClasses, sessionDTO, propertyBagDTO);
                }

                if (rowsReturned > 0) {
                    // Call any postDeleteMain processing
                    postDeleteMain(baseDTO, queryClass, sessionDTO, propertyBagDTO);
                } else // Do we have a primary key?
                {
                    if (primaryKey != null) {
                        // Process rowsReturned, contains error logic which can be overriden in descendant BO
                        processRowsReturned(baseDTO, operation, queryClass, sessionDTO, rowsReturned, propertyBagDTO);
                    }
                }

                if (cached) {
                    cacheMgrLocal.save(baseDTO, operation);
                }

                // End the process
                processEndMain(baseDTO, new ArrayList(), operation, queryClass, sessionDTO, propertyBagDTO);

            }
        }
        catch (ValidationException | NotFoundException | ConstraintViolationException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
            
        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
    }

    //
    // Method used to remove children, delegates to ChildBO's deleteChildMain
    //
    private void deleteChildrenMain(T parentDTO, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ValidationException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "deleteChildrenMain ";

        // Log begin
        logger.logBegin(METHODNAME);

        try {
            for (Class childQueryClass : configuration.getChildQueryClassDeleteOrder()) {
                Configuration registeredChildBO = childConfigurations.get(childQueryClass);
                BaseBO childBO = registeredChildBO.getDtoBo();

                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME, "childBO", childBO, " childBO.getConfiguration().isDeletesChild()= ", childBO.getConfiguration().isDeletesChild());
                }

                // Get the child configuration
                Configuration childConfiguration = childBO.getConfiguration();

                // Can the Delete the children ?
                if (childConfiguration.isDeletesChild() && !childConfiguration.isVanity(parentDTO.getClass())) {
                    Class childBOQueryClass = registeredChildBO.getQueryClass();
                    //List<BaseDTO> childrenDTOs = parentDTO.getChildrenDTOs(childBOQueryClass);
                    List<BaseDTO> childrenDTOs = parentDTO.getChildrenDTOs(childBOQueryClass, DTOState.DELETED);

                    if (logger.isDebugEnabled()) {
                        logChildren(METHODNAME, parentDTO, childBO, childrenDTOs, childBOQueryClass);
                    }

                    // Is Empty
                    if (!childrenDTOs.isEmpty()) {
                        // Call ChildBO.deleteChildren
                        childBO.deleteChildren(parentDTO, childrenDTOs, queryClass, validationClasses, sessionDTO, propertyBagDTO);
                    }
                }
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
    }

    // deleteChildren called by ParentBO EJB
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void deleteChildren(BaseDTO parentDTO, List<T> childrenDTOs, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException,
            AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "deleteChildren ";

        // Log begin
        logger.logBegin(METHODNAME);

        try {
            if (configuration.isDeletesChild()) {

                if (childrenDTOs.size() > 0) {
                    // set the parent on the propertyBag in the event a child needs access to the parent
                    propertyBagDTO.setParentDTO((Class<BaseDTO>) parentDTO.getClass(), parentDTO);
                    Iterator<T> iterator = childrenDTOs.iterator();
                    while (iterator.hasNext()) {
                        // Get the child
                        T childDTO = iterator.next();

                        // Call the remove
                        deleteMain(childDTO, queryClass, sessionDTO, propertyBagDTO);
                        iterator.remove();
                    }
                }
            } else {
                logger.error(METHODNAME, "was called on ", this, " and should not have been.",
                        " See this.configuration.isDeletesChild()=", this.configuration.isDeletesChild());
            }

        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
    }

    public Map<Object, T> getCachedMap() throws MtsException, NotFoundException {
        final String METHODNAME = "getCachedMap ";
        logger.logBegin(METHODNAME);
        Map<Object, T> cachedMap = new LinkedHashMap<Object, T>();
        try {
            if (cached) {
                initializeCache();
                cachedMap = cacheMgrLocal.getCachedMap(dtoClass);
            } else {
                throw new MtsException("DtoClass " + dtoClass.getSimpleName() + " is not cached, this should only be called for cached DTO's");
            }

        } finally {
            logger.logEnd(METHODNAME);
        }
        return cachedMap;
    }

    public T getCachedDTOByPrimaryKey(T baseDTO) throws MtsException, NotFoundException {
        final String METHODNAME = "getCachedDTOByPrimaryKey ";
        logger.logBegin(METHODNAME, baseDTO);
        T resultDTO = null;
        try {
            if (cached) {
                initializeCache();
                try {
                    resultDTO = cacheMgrLocal.getCachedDTOByPrimaryKey(baseDTO);
                    Object primaryKey = baseDTO.getPrimaryKey();
                    if (resultDTO == null && primaryKey != null && !"".equals(primaryKey)) {
                        logger.debug(METHODNAME, "Could not find the primaryKey=", primaryKey, " in the cache for ", dtoClass.getSimpleName());
                    }
                } catch (EJBException e) {
                    logger.warn(METHODNAME + e.getMessage());
                } catch (CacheLoadException e) {
                    logger.warn(METHODNAME + e.getMessage());
                }
            } else {
                throw new MtsException("DtoClass " + baseDTO.getClass().getSimpleName() + " is not cached, this should only be called for cached DTO's");
            }
        } finally {
            logger.logEnd(METHODNAME, baseDTO);
        }
        return resultDTO;
    }

    /**
     * If this BO's DTO is cached then this method is called to prime the cache manager with the data.
     *
     * @throws MtsException
     * @throws NotFoundException
     */
    protected void primeCache() throws MtsException, NotFoundException {
        final String METHODNAME = "primeCache ";
        try {
            T queryDTO = null;
            try {
                queryDTO = dtoClass.newInstance();
            } catch (InstantiationException e) {
                logger.error("InstantiationException - this shouldn't happen: ", this.getClass().getSimpleName(), e);
            } catch (IllegalAccessException e) {
                logger.error("IllegalAccessException - this shouldn't happen: ", this.getClass().getSimpleName(), e);
            }
            DTOUtils.unsetDTOState(queryDTO);
            try {
                // Determine if the cached object has the auto cache feature set. If it does then the child classes are derived.
                List<Class> childClasses;
                Cached cachedAnnotation = DTOUtils.getCached(dtoClass);
                if (cachedAnnotation != null && cachedAnnotation.isAutoCached()) {
                    // Derive child classes - this allows a DTO to have a child autoretrieve set to false
                    // but when the DTO is cached you can specify a child to be cached as well.
                    childClasses = DTOUtils.getDtoChildClasses(dtoClass);
                    logger.debug(METHODNAME, "auto added childclasses: ", childClasses);
                    logger.debug(METHODNAME, "about to call findMain ", dtoClass.getSimpleName());
                } else {
                    childClasses = new ArrayList<Class>();
                }
                List<T> baseDTOs = findMain(QueryType.QUERY_LIST, queryDTO, CacheAll.class, childClasses, AuthenticationUtils.getInternalSessionDTO(), new PropertyBagDTO());
                cacheMgrLocal.initializeCache(dtoClass, baseDTOs);
            } catch (ValidationException e) {
                logger.error("ValidationException - this shouldn't happen: ", e);
            } catch (AuthenticationException e) {
                logger.error("AuthenticationException - this shouldn't happen: ", e);
            } catch (AuthorizationException e) {
                logger.error("AuthorizationException - this shouldn't happen: ", e);
            }
        } catch (Exception ex) {
            logger.error(METHODNAME, "A Exception occurred while calling initializeCache; Message: ", ex);
            cacheMgrLocal.initializeCache(dtoClass, new ArrayList<T>());
        }
    }

    /**
     * Checks to see if cache is initialized. If not primeCahce is called.
     *
     * @throws MtsException
     * @throws NotFoundException
     */
    public void initializeCache() throws MtsException, NotFoundException {
        final String METHODNAME = "initializeCache ";
        boolean cacheInitialized = cacheMgrLocal.isCacheInitialized(dtoClass);
        //logger.debug(METHODNAME, "cacheInitialized=", cacheInitialized, " dtoClass=", dtoClass.getSimpleName() );
        if (!cacheInitialized) {
            primeCache();
        }
    }

    private List<T> findMain(QueryType queryType, T parentDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findMain ";
        try {
            return queryListMain(queryType, parentDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        } catch (ConstraintViolationException e) {
            logger.error("A ConstraintViolationException has occurred, Message: ", e.getMessage(), e);
            throw new MtsException(METHODNAME, "A ConstraintViolationException has occurred", e);
        } finally {
            logger.logEnd(METHODNAME, parentDTO);
        }
    }

    private List<T> queryListMain(QueryType queryType, T parentDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "queryListMain ";
        logger.logBegin(METHODNAME, parentDTO);

        final Operation operation;
        if (queryType == QueryType.CUSTOM_SAVE) {
            operation = Operation.CUSTOM_SAVE;
        } else {
            operation = Operation.FIND;
        }
        List<T> baseDTOs = new ArrayList();
        List<Class> validationClasses = new ArrayList();
        validationClasses.add(queryClass);
        try {

            this.checkAuthorityMain(parentDTO, operation, queryClass, sessionDTO, propertyBagDTO);
            this.processBeginMain(parentDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            this.validateMain(parentDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);

            //
            // Alter the path of the query via a descendant override, allows for custom query to be called via the same interface
            // We can not remove the remotes calls to customQueryMain/customQueryListMain
            queryType = preQuery(queryType, parentDTO, queryClass, sessionDTO, propertyBagDTO);
            
            // Handles Finds
            if (queryType == QueryType.PRIMARY_KEY) {
                baseDTOs.add(dao.findByPrimaryKey(parentDTO, sessionDTO, propertyBagDTO));
            } else if (queryType == QueryType.QUERY) {
                baseDTOs.add(dao.findByQuery(parentDTO, queryClass, sessionDTO, propertyBagDTO));
                
            } else if (queryType == QueryType.QUERY_LIST) {
                if (!DTOUtils.isQueryLazy(parentDTO) && cached && queryClass == FindAll.class) {
                    baseDTOs.addAll(getCachedMap().values());
                } else {
                    baseDTOs = dao.findByQueryList(parentDTO, queryClass, sessionDTO, propertyBagDTO);
                    // Stash a map of the BaseDTOs in the propertyBag to be passed around and used for lookup purpose
                    if (cached && selfReferencing && queryClass == CacheAll.class) {
                        // Is the baseDTOMap in the propertyBagDTO ?
                        if (propertyBagDTO.get(dtoClass.getName()) == null) {
                            propertyBagDTO.put(dtoClass.getName(), getBaseDTOHashMap(baseDTOs));
                        }
                    }
                }

                // Handle custom save
            } else if (queryType == QueryType.CUSTOM_SAVE) {
                T baseDTO = customSave(parentDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
                if (baseDTO != null) {
                    baseDTOs.add(baseDTO);
                }
                // Handles custom query and query list
            } else if (queryType == QueryType.CUSTOM_QUERY) {
                // Execute custom logic to return the DTO, does not call a DAO to obtain data
                T baseDTO = customQuery(parentDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
                if (baseDTO != null) {
                    baseDTOs.add(baseDTO);
                }
            } else if (queryType == QueryType.CUSTOM_QUERY_LIST) {
                // Execute custom logic to return a List of DTOs, does not call a DAO to obtain data
                baseDTOs = customQueryList(parentDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
            } else {
                throw new UnsupportedOperationException("Unsupported QueryType: " + queryType);
            }
            this.processBaseDTOs(parentDTO, baseDTOs, operation, queryClass, null, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME, parentDTO);
        }
        return baseDTOs;
    }
    
    public QueryType preQuery(QueryType queryType, T parentDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
        throws ValidationException, NotFoundException, ConstraintViolationException, MtsException,AuthenticationException, AuthorizationException {
        Boolean isNoDAO = DTOUtils.isNoDAO(dtoClass);
        
        if (isNoDAO) {
            // Reroute
            if (queryType == QueryType.PRIMARY_KEY || queryType == QueryType.QUERY) {
                queryType = QueryType.CUSTOM_QUERY;
            }
            else if (queryType == QueryType.QUERY_LIST) {
                queryType = QueryType.CUSTOM_QUERY_LIST;
            }
            else {
                throw new MtsException("SyntheticDTOs do NOT support " + queryType);
            }
        }
    
        return queryType;
    }

    public List <T> executeQueryListMain(T parentDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws NotFoundException, MtsException {
        return dao.findByQueryList(parentDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    public <S> S findObjectByQueryMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findStringByQueryMain ";
        try {
            List<Class> validationClasses = new ArrayList();
            validationClasses.add(queryClass);
            this.checkAuthorityMain(baseDTO, Operation.FIND, queryClass, sessionDTO, propertyBagDTO);
            this.processBeginMain(baseDTO, Operation.FIND, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            this.validateMain(baseDTO, Operation.FIND, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            S result = dao.findObjectByQuery(baseDTO, queryClass, sessionDTO, requiredType, propertyBagDTO);
            return result;
        } catch (ConstraintViolationException e) {
            logger.error(e);
            throw new MtsException(logger.error("ConstraintViolationException should not happen on a find: ", e.getMessage()));
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public <S> List<S> findObjectByQueryListMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findObjectByQueryListMain ";
        try {
            List<Class> validationClasses = new ArrayList();
            validationClasses.add(queryClass);
            this.checkAuthorityMain(baseDTO, Operation.FIND, queryClass, sessionDTO, propertyBagDTO);
            this.processBeginMain(baseDTO, Operation.FIND, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            this.validateMain(baseDTO, Operation.FIND, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            List<S> result = dao.findObjectByQueryList(baseDTO, queryClass, sessionDTO, requiredType, propertyBagDTO);
            return result;
        } catch (ConstraintViolationException e) {
            logger.error(e);
            throw new MtsException(logger.error("ConstraintViolationException should not happen on a find: ", e.getMessage()));
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    private HashMap<Object, T> getBaseDTOHashMap(List<T> baseDTOs) throws MtsException {
        HashMap<Object, T> baseDTOMap = new HashMap<Object, T>();
        final String METHODNAME = "getBaseDTOHashMap ";
        logger.logBegin(METHODNAME);
        try {
            for (T baseDTO : baseDTOs) {
                baseDTOMap.put(baseDTO.getPrimaryKey(), baseDTO);
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
        return baseDTOMap;
    }

    private void processBaseDTOs(BaseDTO parentDTO, List<T> baseDTOs, Operation operation, Class queryClass, Class childBOQueryClass,
            List<Class> validationClasses, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "processBaseDTOs ";

        logger.logBegin(METHODNAME);
        try {
            this.preProcessBaseDTOsMain(parentDTO, baseDTOs, operation, queryClass, childBOQueryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
            for (T baseDTO : baseDTOs) {
                // It is not necessary to call checkAuthority and validateMain for every childDTO
                this.processBeginMain(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
                this.preFindByMain(baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
                // Do we have any reference DTO's annotated on the BaseDTO
                if (executeFindReferenceDTOMain(baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO)) {
                    // Call the Reference MGR Local
                    referenceMGRLocal.findReferenceDTO(baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
                } else {
                    propertyBagDTO.getPropertyMap().remove("recursiveNodeEncountered");
                }
                this.findChildrenMain(baseDTO, queryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
                this.postFindByMain(baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
                this.processEndMain(baseDTO, childClassDTOs, operation, queryClass, sessionDTO, propertyBagDTO);
            }
            this.postProcessBaseDTOsMain(parentDTO, baseDTOs, operation, queryClass, childBOQueryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    // findByQueryListMain called by MGR EJB, with childClassDTOs List
    // Pass in different Groups types to perform different queries
    public List<T> findByQueryListMain(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryListMain ";
        try {
            return findMain(QueryType.QUERY_LIST, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        }            
    }

    // Used to perform custom processing, DAO is NOT called
    public List<T> customQueryListMain(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "customQueryListMain ";
        try {
            return queryListMain(QueryType.CUSTOM_QUERY_LIST, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | ConstraintViolationException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public T findByQueryMain(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryMain ";
        try {
            return findByQueryMain(QueryType.QUERY, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        }            
    }

    // Used to perform custom processing, DAO is NOT called
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public T customSaveMain(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "customSaveMain ";
        try {
            return queryMain(QueryType.CUSTOM_SAVE, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | ConstraintViolationException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        }        
    }

    // Used to perform custom processing, DAO is NOT called
    public T customQueryMain(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "customQueryMain ";
        // Uses queryMain as it contains all stubs that are necessary
        try {
            return queryMain(QueryType.CUSTOM_QUERY, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | ConstraintViolationException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        }            
    }

    private T findByQueryMain(QueryType queryType, T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryMain ";
        logger.logBegin(METHODNAME);
        try {
            return queryMain(queryType, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        } catch (ConstraintViolationException e) {
            logger.error(e);
            throw new MtsException(logger.error("ConstraintViolationException should not happen on a find: ", e.getMessage()));
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    private T queryMain(QueryType queryType, T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "queryMain ";
        logger.logBegin(METHODNAME, baseDTO);
        T result = null;
        List<T> results;
        try {
            results = queryListMain(queryType, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
            if (results != null) {
                if (results.size() == 1) {
                    result = results.get(0);
                } else if (results.size() > 1) {
                    throw new MtsException("Unexpected result size > 1: should be 1 was: " + results.size());
                }
            } else {
                throw new MtsException("Unexpected null returned by findMain method.");
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
        return result;
    }

    public T findByPrimaryKeyMain(T baseDTO, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByPrimaryKeyMain ";
        logger.logBegin(METHODNAME, baseDTO);
        T resultDTO = null;
        try {
            // If checked check cache first
            if (cached) {
                initializeCache();
                resultDTO = getCachedDTOByPrimaryKey(baseDTO);
            }
            // Normally flow here if not cached OR if cached object is null
            if (resultDTO == null) {
                resultDTO = findByQueryMain(QueryType.PRIMARY_KEY, baseDTO, PrimaryKey.class, childClassDTOs, sessionDTO, propertyBagDTO);
                if (resultDTO == null) {
                    throw new NotFoundException("resultDTO could not be located!");
                }
            }
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        } finally {
            logger.logEnd(METHODNAME, baseDTO);
        }
        return resultDTO;
    }

    /**
     * Main routine for calling locally implemented export routine.
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws ValidationException
     * @throws NotFoundException
     * @throws MtsException
     * @throws AuthenticationException
     * @throws AuthorizationException
     */
    public Map<String, byte[]> exportDataMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "exportDataMain ";
        try {
            securityMGRLocal.checkAuthority(PermissionType.SELECT, dtoClass, sessionDTO, propertyBagDTO);
            return exportData(baseDTO, queryClass, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        }            
    }

    /**
     * Override for local implementation of export routine.
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws ValidationException
     * @throws NotFoundException
     * @throws MtsException
     * @throws AuthenticationException
     * @throws AuthorizationException
     */
    public Map<String, byte[]> exportData(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "exportData ";
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Main routine for calling locally implemented import routine.
     *
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @throws ValidationException
     * @throws NotFoundException
     * @throws MtsException
     * @throws AuthenticationException
     * @throws AuthorizationException
     * @throws ConstraintViolationException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void importDataMain(Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "importDataMain ";
        try {
            securityMGRLocal.checkAuthority(PermissionType.INSERT, dtoClass, sessionDTO, propertyBagDTO);
            importData(queryClass, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | ConstraintViolationException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, null, sessionDTO, propertyBagDTO);
            throw e;            
        }            
    }

    /**
     * Override for local implementation of import routine.
     *
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @throws ValidationException
     * @throws NotFoundException
     * @throws MtsException
     * @throws AuthenticationException
     * @throws AuthorizationException
     * @throws ConstraintViolationException
     */
    public void importData(Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "importData ";
        throw new UnsupportedOperationException("Not supported yet.");
    }

    // checkAuthorityMain called internally
    private void checkAuthorityMain(T baseDTO, Operation operation, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "checkAuthority ";

        // Log begin
        logger.logBegin(METHODNAME, baseDTO);

        // don't allow null DTOs...
        if (baseDTO == null) {
            throw new MtsException("baseDTO is null!");
        }

        try {

            // Set permission type
            PermissionType permissionType = PermissionType.getPermissionTypeByOperation(operation, baseDTO.getDTOState());

            // Call Security Manager
            if (securityMGRLocal != null) {
                propertyBagDTO.put("checkAuthSrc", getClass().getSimpleName());
                securityMGRLocal.checkAuthority(permissionType, dtoClass, sessionDTO, propertyBagDTO);
            }

            this.checkAuthority(baseDTO, operation, queryClass, sessionDTO, propertyBagDTO);
        } finally {
            // Log end
            logger.logEnd(METHODNAME, baseDTO);
        }
    }

    // Allow for descendant level override, implementation for fine grained Authorization
    protected void checkAuthority(T baseDTO, Operation operation, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, AuthorizationException {
    }

    // validateMain called internally
    private void validateMain(T baseDTO, Operation operation, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "validateMain ";

        // Log begin
        logger.logBegin(METHODNAME, baseDTO);

        try {
            validate(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            if (operation == Operation.FIND || operation == Operation.DELETE) {
                validateFindOrDelete(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            } else if (operation == Operation.ADD || operation == Operation.UPDATE || operation == Operation.CUSTOM_SAVE) {
                validateAddOrUpdate(baseDTO, operation, queryClass, validationClasses, sessionDTO, propertyBagDTO);
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME, baseDTO);
        }
    }

    protected void validate(T baseDTO, Operation operation, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException {
        final String METHODNAME = "validate ";

        // Log begin
        logger.logBegin(METHODNAME, baseDTO);
        try {
            if (baseDTO != null) {
                logger.debug(METHODNAME + "Validating baseDTO: ", baseDTO.getClass().getSimpleName());
                // For all operations validate the DTO ( as constraints are added to the DTO this may change )
                if (!validationClasses.isEmpty()) {
                    Validator validator = validatorFactory.getValidator();
                    Set<ConstraintViolation<T>> violations = validator.validate(baseDTO, validationClasses.toArray(new Class[0]));

                    // Any violations ?
                    if (violations.size() > 0) {
                        List<BrokenRule> brokenRules = new ArrayList();
                        for (ConstraintViolation<T> violation : violations) {
                            logger.debug(METHODNAME, "creating BrokenRule with violation message: ", violation.getMessage());
                            logger.debug(METHODNAME, "creating BrokenRule with violation property path: ", violation.getPropertyPath());
                            brokenRules.add(new BrokenRule(violation));
                        }
                        throw new ValidationException(brokenRules);
                    }
                }
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME, baseDTO);
        }
    }

    // Allow for descendant level override
    protected void validateAddOrUpdate(T baseDTO, Operation operation, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "validateAddOrUpdate ";
    }

    // Allow for descendant level override
    protected void validateFindOrDelete(T baseDTO, Operation operation, Class queryClass, List<Class> validationClasses, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "validateFindOrDelete ";
    }

    private boolean executeFindReferenceDTOMain(T parentDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws ValidationException {
        final String METHODNAME = "executeFindReferenceDTOMain ";

        if (parentDTO != null && parentDTO.isReferenceDTOsExist()) {
            if (selfReferencing) {
                Object recursiveNodeEncountered = propertyBagDTO.getPropertyMap().get("recursiveNodeEncountered");
                if (recursiveNodeEncountered != null && ((Boolean) recursiveNodeEncountered)) {
                    logger.error(METHODNAME, "recursiveNodeEncountered: ", true);
                    return false;
                }
                List<BaseDTO> ancestorList = DTOUtils.getAncestorListFromProbertyBagDTO(parentDTO, propertyBagDTO);
                if (ancestorList.contains(parentDTO)) {
                    propertyBagDTO.getPropertyMap().put("recursiveNodeEncountered", true);
                    logger.warn(METHODNAME, "Hit a recursive DTO: ", parentDTO);
                    logger.warn(METHODNAME, "    ancestorList: ", ancestorList);
                    return false;
                }
            }
            return executeFindReferenceDTO(parentDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        } else {
            return false;
        }
    }

    protected boolean executeFindReferenceDTO(T parentDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        return true;
    }

    // Method used to allow descendant to determine if findChildren should execute
    private boolean executeFindChildrenMain(T parentDTO, Class queryClass, List<Class> validationClasses,
            List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws ValidationException {
        final String METHODNAME = "executeFindChildrenMain ";

        if (selfReferencing) {
            Object recursiveNodeEncountered = propertyBagDTO.getPropertyMap().get("recursiveNodeEncountered");
            if (recursiveNodeEncountered != null && ((Boolean) recursiveNodeEncountered)) {
                logger.error(METHODNAME, "recursiveNodeEncountered: ", true);
                return false;
            }
            List<BaseDTO> ancestorList = DTOUtils.getAncestorListFromProbertyBagDTO(parentDTO, propertyBagDTO);
            if (ancestorList.contains(parentDTO)) {
                propertyBagDTO.getPropertyMap().put("recursiveNodeEncountered", true);
                logger.warn(METHODNAME, "Hit a recursive DTO: ", parentDTO);
                logger.warn(METHODNAME, "    ancestorList: ", ancestorList);
                return false;
            }
        }

        return executeFindChildren(parentDTO, queryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
    }

    // Method used to allow descendant to determine if findChildren should execute
    protected boolean executeFindChildren(T parentDTO, Class queryClass, List<Class> validationClasses,
            List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        return true;
    }

    //
    // Method used to load the childClassDTOs upon request, required call to registerChildConfiguration
    // Delegate to ChildBO's
    //
    private void findChildrenMain(T parentDTO, Class queryClass, List<Class> validationClasses, List<Class> childClassDTOs,
            SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, MtsException, AuthenticationException, AuthorizationException, NotFoundException {
        final String METHODNAME = "findChildrenMain ";

        // Log begin
        logger.logBegin(METHODNAME);
        T result = parentDTO;

        try {
            if (childClassDTOs == null) {
                throw new MtsException("The childClassDTOs argument can not be NULL, please correct your implementation.");
            }
            // Get the childClassDTOs that need to be freshed
            List<Class> refreshChildClassDTOs = propertyBagDTO.getRefreshChildClassDTOs();

            // Determine if findChildren should execute?
            if (executeFindChildrenMain(result, queryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO)) {
                for (Configuration<? extends BaseDTO> childConfiguration : childConfigurations.values()) {
                    if (logger.isDebugEnabled() || debugBO) {
                        logger.debug("ACCESSING CHILD CONFIG: ", childConfiguration.getDtoClass().getCanonicalName());
                    }
                    Class childClassDTO = childConfiguration.getDtoClass();
                    if ((childClassDTOs.contains(childClassDTO) && !childConfiguration.isVanity(parentDTO.getClass()))
                            || childConfiguration.isAutoRetrieve(parentDTO.getClass())
                            || refreshChildClassDTOs.contains(childClassDTO)) {
                        try {
                            BaseBO childBO = childConfiguration.getDtoBo();
                            if (logger.isDebugEnabled()) {
                                logger.debug(METHODNAME, "calling ", childBO, ".findChildren");
                            }
                            // Call the ChildBO
                            boolean rollbackOnNotFound = false;
                            if (!childConfiguration.isChildNotFoundAllowed(parentDTO.getClass())) {
                                rollbackOnNotFound = true;
                            }
                            result = (T) childBO.findChildren(result, queryClass, childConfiguration.getQueryClass(),
                                    validationClasses, childClassDTOs, rollbackOnNotFound, sessionDTO, propertyBagDTO);
                        } catch (NotFoundException ex) {
                            // Its acceptable to have a NotFoundException as a ParentDTO may not have any childDTO's unless
                            // AutoRetrieve mode indicates that it is not acceptable
                            if (logger.isDebugEnabled()) {
                                logger.debug(METHODNAME, "A NotFoundException has occurred when attempting to find the children of the BO ",
                                        childConfiguration.getDtoBo(), " with the queryClass ", queryClass);
                            }
                            if (childConfiguration.isAutoRetrieve(parentDTO.getClass()) && !childConfiguration.isChildNotFoundAllowed(parentDTO.getClass())) {
                                throw new MtsException("A NotFoundException has occurred, wrapped in an MtsException for rollback control.", ex);
                            }
                        }
                    }
                }
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME);
        }
    }

    //
    // findChildren called by MGR EJB with childClassDTOs
    // Pass in different Groups types to perform different queries
    //
    public BaseDTO findChildren(BaseDTO parentDTO, Class queryClass, Class childBOQueryClass, List<Class> validationClasses,
            List<Class> childClassDTOs, boolean rollbackOnNotFound, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException,
            AuthenticationException, AuthorizationException {
        final String METHODNAME = "findChildren ";
        final Operation operation = Operation.FIND;

        // Log begin
        logger.logBegin(METHODNAME, parentDTO);

        try {
            // It is not necessary to call validateMain on the parentDTO as it was already called on the Parent BO
            // this.processDTOState(parentDTO, operation, queryClass, validationClasses, sessionDTO, false, false, propertyBagDTO);
            // Execute the query
            if (logger.isDebugEnabled() || debugBO) {
                logger.debug("SETTING CHILDREN ON PARENT: ",
                        parentDTO == null ? parentDTO : parentDTO.getClass().getCanonicalName(),
                        " - queryClass: ",
                        queryClass == null ? queryClass : queryClass.getCanonicalName(),
                        " - childBOQueryClass: ",
                        childBOQueryClass == null ? childBOQueryClass : childBOQueryClass.getCanonicalName());
            }
            if (parentDTO != null) {
                // set the parent on the propertyBag in the event a child needs access to the parent
                propertyBagDTO.setParentDTO((Class<BaseDTO>) parentDTO.getClass(), parentDTO);

                // Set the Parents children
                int childCount = dao.setParentsChildren(parentDTO, queryClass, childBOQueryClass, rollbackOnNotFound, sessionDTO, propertyBagDTO);
                logger.debug(METHODNAME, childBOQueryClass, " child query class count: ", childCount);

                // Set the Parent Child Comparator
                this.setParentChildComparator(parentDTO, queryClass, childBOQueryClass);

                //
                // To do Insert validation of all existing childDTOs here if there is no childClassDTOs
                //
                // Get the children
                List<T> childrenDTOs = parentDTO.getChildrenDTOs(childBOQueryClass, dtoClass);

                if (selfReferencing) {
                    DTOUtils.processAncestorMap(parentDTO, (List) childrenDTOs, propertyBagDTO);
                }

                this.processBaseDTOs(parentDTO, childrenDTOs, operation, queryClass, childBOQueryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
            } else {
                logger.error("parentDTO was null!!!");
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME, parentDTO);
        }
        return parentDTO;
    }

    private void setParentChildComparator(BaseDTO parentDTO, Class queryClass, Class childBOQueryClass) {
        final String METHODNAME = "setParentChildComparator ";
        // Log begin
        logger.logBegin(METHODNAME, parentDTO);

        try {

            if (!parentChildComparatorMap.containsKey(childBOQueryClass)) {
                Class childQueryClass = DTOUtils.getQueryClassFromDtoQueryMap(parentDTO.getClass(), dtoClass);
                if (childBOQueryClass == childQueryClass) {
                    // create a comparator for sorting this set of children if a parentchild comparator is registered
                    if (!parentChildComparatorMap.containsKey(childBOQueryClass)) {
                        Comparator lComparator = null;
                        ParentChildRelationship parentChildRelationship = DTOUtils.getParentChildRelationshipMapByQueryClass(parentDTO.getClass()).get(childBOQueryClass);
                        if (parentChildRelationship != null) {
                            if (parentChildRelationship.comparatorClass() != null && parentChildRelationship.comparatorClass() != None.class) {
                                try {
                                    lComparator = (Comparator) parentChildRelationship.comparatorClass().newInstance();
                                    logger.debug("Found relationship level comparator: ", lComparator.getClass().getSimpleName());
                                } catch (InstantiationException e) {
                                    System.err.println(e.getMessage());
                                } catch (IllegalAccessException e) {
                                    System.err.println(e.getMessage());
                                }
                            }
                        }
                        parentChildComparatorMap.put(childBOQueryClass, lComparator);
                    }
                }
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME, parentDTO);
        }
    }

    private void preAddMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "preAddMain ";
        // Call descendant level
        this.preAdd(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // Allows for descendant level override
    protected void preAdd(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "preAdd ";
    }

    private void postAddMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "postAddMain ";

        // Call descendant level
        this.postAdd(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // Allows for descendant level override
    protected void postAdd(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "postAdd ";
    }

    private void preUpdateMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "preUpdateMain ";
        // Call descendant level
        this.preUpdate(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // Allows for descendant level override
    protected void preUpdate(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "preUpdate ";
    }

    private void postUpdateMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "postUpdateMain ";
        // Call descendant level
        this.postUpdate(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // Allows for descendant level override
    protected void postUpdate(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "postUpdate ";
    }

    private void preDeleteMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "preDelete ";
        // Call descendant level
        this.preDelete(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // Allows for descendant level override
    protected void preDelete(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "preDelete ";
    }

    private void postDeleteMain(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "postDeleteMain ";
        // Call descendant level
        this.postDelete(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    // Allows for Descendant level override
    protected void postDelete(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException, ValidationException, AuthenticationException,
            AuthorizationException {
        final String METHODNAME = "postDelete ";
    }

    //
    // Transfer the children from the latestBaseDTO to the BaseDTO
    // SDN: added transfer of dto state and property change event map
    // This is necessary since the dao add, update returns raw BaseDTO from the database
    //
    private T transferChildren(T fetchedBaseDTO, Operation operation, Class queryClass, T baseDTO, PropertyBagDTO propertyBagDTO) {
        fetchedBaseDTO.setChildDTOMap(baseDTO.getChildDTOMap());
        DTOUtils.setDTOState(fetchedBaseDTO, baseDTO.getDTOState());
        fetchedBaseDTO.setPropertyChangeEventMap(baseDTO.getPropertyChangeEventMap());
        return fetchedBaseDTO;
    }

    // Currently populates childClassDTOs via registered autoRetrieve childConfigurations
    private void postFindByMain(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ValidationException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "postFindByMain ";

        // call descendant level postFindBy
        this.postFindBy(baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
    }

    // Allow for descendant level override
    protected void postFindBy(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ValidationException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "postFindBy ";
    }

    private void preFindByMain(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ValidationException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "preFindByMain ";

        // Log begin
        logger.logBegin(METHODNAME, baseDTO);
        try {
            // call descendant level preFindBy
            this.preFindBy(baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        } finally {
            // Log end
            logger.logEnd(METHODNAME, baseDTO);
        }

    }

    // Allow for descendant level override
    protected void preFindBy(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ValidationException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "preFindBy ";
    }

    private void preProcessBaseDTOsMain(BaseDTO parentDTO, List<T> baseDTOs, Operation operation, Class queryClass, Class childBOQueryClass,
            List<Class> validationClasses, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "preProcessBaseDTOsMain ";
        this.preProcessBaseDTOs(parentDTO, baseDTOs, operation, queryClass, childBOQueryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
    }

    protected List<T> customQueryList(T parentDTO, Class queryClass,
            List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "customQueryList ";
        return new ArrayList<T>();
    }

    protected T customQuery(T parentDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "customQuery ";
        T baseDTO = null;
        return baseDTO;
    }

    protected T customSave(T parentDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        final String METHODNAME = "customSave ";
        T baseDTO = null;
        return baseDTO;
    }

    protected void preProcessBaseDTOs(BaseDTO parentDTO, List<T> baseDTOs, Operation operation, Class queryClass, Class childBOQueryClass,
            List<Class> validationClasses, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "preProcessBaseDTOs ";
    }

    private void postProcessBaseDTOsMain(BaseDTO parentDTO, List<T> baseDTOs, Operation operation, Class queryClass, Class childBOQueryClass,
            List<Class> validationClasses, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "postProcessBaseDTOsMain ";

        Comparator sortComparator = null;
        // Is there a parent child comparator
        if (childBOQueryClass != null) {
            sortComparator = parentChildComparatorMap.get(childBOQueryClass);
        }
        // Is there a dtoComparator
        if (sortComparator == null) {
            sortComparator = dtoComparator;
        }
        // if there is a registered OrderBy annotation - apply the sort
        if (sortComparator != null && baseDTOs != null && baseDTOs.size() > 1 && !DTOUtils.isQueryLazy(parentDTO)) {
            logger.debug("Sorting by sortComparator: ", sortComparator);
            Collections.sort(baseDTOs, sortComparator);
        } else {
            logger.debug("No sortComparator found for: ", dtoClass);
        }

        this.postProcessBaseDTOs(parentDTO, baseDTOs, operation, queryClass, validationClasses, childClassDTOs, sessionDTO, propertyBagDTO);
    }

    protected void postProcessBaseDTOs(BaseDTO parentDTO, List<T> baseDTOs, Operation operation, Class queryClass,
            List<Class> validationClasses, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "postProcessBaseDTOs ";
    }

    private void processEndMain(T baseDTO, List<Class> childClassDTOs, Operation operation, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "processEndMain ";
        logger.logBegin(METHODNAME, baseDTO);
        try {
            if (baseDTO != null) {
                // Call descendant override
                this.processEnd(baseDTO, childClassDTOs, operation, queryClass, sessionDTO, propertyBagDTO);
                // Reset the DTO state and clears the PropertyChangeEventMap
                if (operation != Operation.DELETE) {
                    DTOUtils.unsetDTOState(baseDTO);
                }
            }
        } finally {
            // Log end
            logger.logEnd(METHODNAME, baseDTO);
        }
    }

    // For descendant override
    protected void processEnd(T baseDTO, List<Class> childClassDTOs, Operation operation, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "processEnd ";
    }

    private void logChildren(String methodName, T parentDTO, BaseBO childBO, List<BaseDTO> childrenDTOs, Object childBOQueryClass) {
        logger.debug(methodName, "parentDTO=", parentDTO.toString());
        logger.debug(methodName, "childBO=", childBO.toString());
        logger.debug(methodName, "childBOQueryClass=", childBOQueryClass.toString());
        logger.debug(methodName, "childrenDTOs=", "" + childrenDTOs.size());
        if (childrenDTOs.size() > 0) {
            logger.debug(methodName, "childrenDTOs.get(0)=", childrenDTOs.get(0).toString());
        }
    }

    protected boolean isOperationName(PropertyBagDTO propertyBagDTO, String operationName) {
        return PropertyBagDTO.isOperationName(propertyBagDTO, operationName);
    }

    protected boolean isQueryMapEmpty(T baseDTO) {
        boolean queryMapEmpty = true;
        for (Entry<String, Object> entry : baseDTO.getQueryMap().entrySet()) {
            String key = entry.getKey();
            logger.debug("key=" + entry.getKey() + " value=" + entry.getValue());
            if (!(key.equalsIgnoreCase(CoreConstants.LAZY) || key.equalsIgnoreCase(CoreConstants.FILTERS)
                    || key.equalsIgnoreCase(CoreConstants.LAZY_ROW_OFFSET) || key.equalsIgnoreCase(CoreConstants.LAZY_PAGE_SIZE)
                    || key.equalsIgnoreCase(CoreConstants.LAZY_ROWCOUNT) || key.equalsIgnoreCase(CoreConstants.SORT_FIELD)
                    || key.equalsIgnoreCase(CoreConstants.SORT_ORDER))) {
                if (entry.getValue() != null) {
                    queryMapEmpty = false;
                    break;
                }
            }
        }
        return queryMapEmpty;
    }

    public BaseSecurityMGR getSecurityMGRLocal() {
        return securityMGRLocal;
    }

    public List<Class> getDtoChildClasses() {
        return DTOUtils.getDtoChildClasses(dtoClass);
    }

    public boolean isSelfReferencing() {
        return selfReferencing;
    }

    public void setSelfReferencing(boolean selfReferencing) {
        this.selfReferencing = selfReferencing;
    }
    
    public byte[] getReportMain(T baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "getReportMain ";        
        try {
            return getReport(baseDTO, sessionDTO, propertyBagDTO);
        }
        catch (ValidationException | NotFoundException | MtsException | AuthenticationException | AuthorizationException e) {
            logException(METHODNAME, e, baseDTO, sessionDTO, propertyBagDTO);
            throw e;            
        }
    }
    
    public byte[] getReport(T baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "getReport ";        

        if (baseDTO == null) {
            throw new IllegalArgumentException(METHODNAME + "baseDTO was null");
        }
        
        Map<String, Object> queryMap = baseDTO.getQueryMap();
        
        String reportPath = (String) propertyBagDTO.get("reportPath"); 
        if (StringUtils.isEmpty(reportPath)) {
            throw new IllegalArgumentException(METHODNAME + "reportPath was null");
        }
        
        // Jasper parameter map
        logger.debug(METHODNAME, "queryMap: ", queryMap);
        logger.debug(METHODNAME, "reportPath: ", reportPath);
        
        // Check the user's authority to inoke
        propertyBagDTO.put("checkAuthSrc", getClass().getSimpleName());
        getSecurityMGRLocal().checkAuthority(PermissionType.SELECT, baseDTO.getClass(), sessionDTO, propertyBagDTO);

        byte[] byteArray = new String().getBytes();
        String reportType = null;
        
        // Get the Report Type
        if (reportPath.contains(".")) {
            int periodPos = reportPath.indexOf(".");
            reportType = reportPath.substring(periodPos + 1);
            reportPath = reportPath.substring(0, periodPos);
        }
        else {
            // Default to html
            reportType = "html";
        }
        
        logger.debug(METHODNAME, "reportType=", reportType);
        
        if (reportType.equalsIgnoreCase("pdf") || reportType.equalsIgnoreCase("xls")) {
            InputStream inputStream = null;
            if (reportType.equalsIgnoreCase("pdf")) {
                logger.debug(METHODNAME, "calling getPdfReport");
                inputStream = jasperServerMGRLocal.getPdfReport(reportPath, queryMap);
                logger.debug(METHODNAME, "returning from getPdfReport");

            }
            else if (reportType.equalsIgnoreCase("xls")) {
                logger.debug(METHODNAME, "calling getXlsReport");
                inputStream = jasperServerMGRLocal.getXlsReport(reportPath, queryMap);
                logger.debug(METHODNAME, "returning from getXlsReport");

            }
            logger.debug(METHODNAME, "inputStream=", inputStream);
            if (inputStream != null) {
                try {
                    byteArray = IOUtils.toByteArray(inputStream);

                } catch (IOException ex) {
                    logger.error(METHODNAME, "An IOException has occurred; Message: ", ex.getMessage(), ex);
                }
            }
        }
        else {
            throw new IllegalArgumentException(METHODNAME + "unsupported reportType " + reportType);
        }
        return byteArray;    
    }    

    public JasperServerMGRLocal getJasperServerMGRLocal() {
        return jasperServerMGRLocal;
    }

    public AppLogMGRLocal getAppLogMGRLocal() {
        return appLogMGRLocal;
    }

    // Inner Class used to store Parent and Child DTO Configurations and BOs
    public static class Configuration<S extends BaseDTO> {

        private Class<S> dtoClass;
        private BaseBO<S> dtoBo;
        private Class queryClass;
        private Map<Class<? extends BaseDTO>, Class> childQueryMap;
        private boolean debug = false;
        private List<Class> childQueryClassDeleteOrder;
        private List<Class> childQueryClassAddUpdateOrder;

        public Configuration(Class<S> dtoClass, BaseBO<S> dtoBo, boolean debug) {
            this.dtoClass = dtoClass;
            this.queryClass = dtoClass;
            this.dtoBo = dtoBo;
            this.debug = debug;
        }

        public Configuration(Class<S> dtoClass, Class queryClass, boolean debug) {
            this.dtoClass = dtoClass;
            this.queryClass = queryClass;
            this.debug = debug;
        }

        public Configuration(BaseBO<S> dtoBo, Class<S> dtoClass, Class queryClass, boolean debug) {
            this.dtoBo = dtoBo;
            this.dtoClass = dtoClass;
            this.queryClass = queryClass;
            this.debug = debug;
        }

        public Class<S> getDtoClass() {
            return dtoClass;
        }

        public BaseBO<S> getDtoBo() throws MtsException {
            if (dtoBo == null) {
                dtoBo = EJBUtils.getDtoBo(dtoClass, debug);
            }
            return dtoBo;
        }

        public Map<Class<? extends BaseDTO>, Class> getChildQueryMap() {
            if (childQueryMap == null) {
                childQueryMap = DTOUtils.getDtoQueryMap(dtoClass);
            }
            return childQueryMap;
        }

        public List<Class> getChildQueryClassDeleteOrder() {
            if (childQueryClassDeleteOrder == null) {
                childQueryClassDeleteOrder = new LinkedList<Class>();
                List<ParentChildRelationship> parentChildRelationships = new ArrayList<ParentChildRelationship>(DTOUtils.getParentChildRelationshipMapByDTO(dtoClass).values());
                Collections.sort(parentChildRelationships, new ParentChildRelationshipDeleteOrderComparator());
                for (ParentChildRelationship parentChildRelationship : parentChildRelationships) {
                    childQueryClassDeleteOrder.add(parentChildRelationship.childQueryClass());
                }
            }
            return childQueryClassDeleteOrder;
        }

        public List<Class> getChildQueryClassAddUpdateOrder() {
            if (childQueryClassAddUpdateOrder == null) {
                childQueryClassAddUpdateOrder = new LinkedList<Class>();
                List<ParentChildRelationship> parentChildRelationships = new ArrayList<ParentChildRelationship>(DTOUtils.getParentChildRelationshipMapByDTO(dtoClass).values());
                Collections.sort(parentChildRelationships, new ParentChildRelationshipAddUpdateOrderComparator());
                for (ParentChildRelationship parentChildRelationship : parentChildRelationships) {
                    childQueryClassAddUpdateOrder.add(parentChildRelationship.childQueryClass());
                }
            }
            return childQueryClassAddUpdateOrder;
        }

        public Class getQueryClass() {
            return queryClass;
        }

        public boolean isAutoRetrieve(Class<? extends BaseDTO> foreignKeyClass) throws MtsException {
            return DTOUtils.isAutoRetrieve(dtoClass, foreignKeyClass);
        }

        public boolean isReadOnly() {
            return DTOUtils.isReadOnly(dtoClass);
        }

        public boolean isAddAllowed() {
            return DTOUtils.isAddAllowed(dtoClass);
        }

        public boolean isDeleteAllowed() {
            return DTOUtils.isDeleteAllowed(dtoClass);
        }

        public boolean isUpdateAllowed() {
            return DTOUtils.isUpdateAllowed(dtoClass);
        }

        public boolean isChildNotFoundAllowed(Class<? extends BaseDTO> foreignKeyClass) throws MtsException {
            return DTOUtils.isChildNotFoundAllowed(dtoClass, foreignKeyClass);
        }

        public boolean isDeletesChild() {
            return DTOUtils.deletesChild(dtoClass);
        }

        public boolean isAddsChild() {
            return DTOUtils.addsChild(dtoClass);
        }

        public boolean isUpdatesChild() {
            return DTOUtils.updatesChild(dtoClass);
        }

        public boolean isVanity(Class<? extends BaseDTO> foreignKeyClass) throws MtsException {
            return DTOUtils.isVanity(dtoClass, foreignKeyClass);
        }
    }

    private void logException(String methodName, Exception e, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
//        final String METHODNAME = "logException ";
////        if (e instanceof NotFoundException) {
////            logger.error(methodName, "An ", e.getClass().getSimpleName(), " has occurred, Message:", e.getMessage());
////        }
////        else {
////            logger.error(methodName, "An ", e.getClass().getSimpleName(), " has occurred, Message:", e.getMessage(), e);
////        }
////        logger.debug(METHODNAME, "logExceptions=", logExceptions);
        if (logExceptions) {
            if (!(e instanceof NotFoundException)) {
                appLogMGRLocal.queueAppLog(LogLevel.ERROR, e, baseDTO, sessionDTO, propertyBagDTO);
            }
        }
    }
    private void logConfig() throws MtsException {
        Map<Class<? extends BaseDTO>, Class> childQueryMap = configuration.getChildQueryMap();
        for (Entry<Class<? extends BaseDTO>, Class> entry : childQueryMap.entrySet()) {
            logger.debug("CHILD DTO: ", entry.getKey());
            logger.debug("CHILD QUERYCLASS: ", entry.getValue());
        }
        logger.debug("CONFIG isAddAllowed: ", configuration.isAddAllowed());
        logger.debug("CONFIG isAddsChild: ", configuration.isAddsChild());
        logger.debug("CONFIG isAutoRetrieve: ", configuration.isAutoRetrieve(this.getDtoClass()));
        logger.debug("CONFIG isChildNotFoundAllowed: ", configuration.isChildNotFoundAllowed(this.getDtoClass()));
        logger.debug("CONFIG isDeleteAllowed: ", configuration.isDeleteAllowed());
        logger.debug("CONFIG isDeletesChild: ", configuration.isDeletesChild());
        logger.debug("CONFIG isReadOnly: ", configuration.isReadOnly());
        logger.debug("CONFIG isUpdateAllowed: ", configuration.isUpdateAllowed());
        logger.debug("CONFIG isUpdateAllowed: ", configuration.isUpdateAllowed());
        for (Entry<Class, Configuration<? extends BaseDTO>> entry : childConfigurations.entrySet()) {
            logger.debug("CHILD CONFIG FOUND FOR: ", entry.getKey().getCanonicalName());
            logger.debug("CHILD CONFIG isAddAllowed: ", entry.getValue().isAddAllowed());
            logger.debug("CHILD CONFIG isAddsChild: ", entry.getValue().isAddsChild());
            logger.debug("CHILD CONFIG isAutoRetrieve: ", entry.getValue().isAutoRetrieve(this.getDtoClass()));
            logger.debug("CHILD CONFIG isChildNotFoundAllowed: ", entry.getValue().isChildNotFoundAllowed(this.getDtoClass()));
            logger.debug("CHILD CONFIG isDeleteAllowed: ", entry.getValue().isDeleteAllowed());
            logger.debug("CHILD CONFIG isDeletesChild: ", entry.getValue().isDeletesChild());
            logger.debug("CHILD CONFIG isReadOnly: ", entry.getValue().isReadOnly());
            logger.debug("CHILD CONFIG isUpdateAllowed: ", entry.getValue().isUpdateAllowed());
            logger.debug("CHILD CONFIG isUpdatesChild: ", entry.getValue().isUpdatesChild());
        }
    }
}
