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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct; 
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.enumeration.DTOState;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.exceptions.AuthenticationException;
import org.cdsframework.exceptions.AuthorizationException;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.exceptions.ValidationException;
import org.cdsframework.group.Add;
import org.cdsframework.group.Delete;
import org.cdsframework.group.Update;
import org.cdsframework.util.ClassUtils;
import org.cdsframework.util.EJBUtils;
import org.cdsframework.util.LogUtils;

@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public abstract class BaseMGR<T extends BaseDTO> {

    protected BaseBO<T> baseBO;
    protected LogUtils logger;
    private Class<T> dtoClass;

    @PostConstruct
    private void postConstructor() {
        logger = LogUtils.getLogger(getClass());
        dtoClass = ClassUtils.getTypeArgument(BaseMGR.class, getClass());
        try {
            baseBO = EJBUtils.getDtoBo(dtoClass);
            initialize();
        } catch (MtsException e) {
            logger.error(e);
        }
    }

    protected void initialize() throws MtsException {
        // descendant override
    }

    public T newInstance(SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "newInstance ";
        logger.logBegin(METHODNAME);
        T resultDTO = null;
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            resultDTO = baseBO.newInstanceMain(sessionDTO, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
        return resultDTO;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public T save(BaseBO<T> baseBO, T baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "save ";
        long start = System.nanoTime();
        logger.logBegin(METHODNAME);
        T resultDTO = null;
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);

            DTOState operationDTOState = baseDTO.getOperationDTOState();
            logger.debug(METHODNAME, "operationDTOState=" + operationDTOState);
            switch (operationDTOState) {
                case NEW: case NEWMODIFIED:
                    resultDTO = baseBO.addMain(baseDTO, Add.class, sessionDTO, newPropertyBagDTO);
                    break;
                case UPDATED:
                    resultDTO = baseBO.updateMain(baseDTO, Update.class, sessionDTO, newPropertyBagDTO);
                    break;
                case DELETED:
                    baseBO.deleteMain(baseDTO, Delete.class, sessionDTO, newPropertyBagDTO);
                    break;
                case UNSET:
                    logger.warn(METHODNAME, "operationDTOState = UNSET - returning original...");
                    resultDTO = baseDTO;
                    break;
                default:
                    throw new MtsException("Unexpected operationDTOState: " + operationDTOState);
            }
        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);                                                
            logger.logEnd(METHODNAME);
        }
        return resultDTO;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public T save(T baseDTO, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException, AuthenticationException, AuthorizationException {
        return save(baseDTO, sessionDTO, new PropertyBagDTO());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public T save(T baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "save ";
        logger.logBegin(METHODNAME);
        try {
            T result = this.save(baseBO, baseDTO, sessionDTO, propertyBagDTO);
            return result;
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public List<T> findByQueryList(BaseBO<T> baseBO, T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryList ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return baseBO.findByQueryListMain(baseDTO, queryClass, childClassDTOs, sessionDTO, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public List<T> findByQueryList(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findByQueryList(baseDTO, queryClass, childClassDTOs, sessionDTO, new PropertyBagDTO());
    }

    public List<T> findByQueryList(T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryList ";
        logger.logBegin(METHODNAME);
        try {
            return this.findByQueryList(this.baseBO, baseDTO, queryClass, childClassDTOs, sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public List<T> findByQueryList(T baseDTO, String queryClass, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findByQueryList(baseDTO, queryClass, sessionDTO, new PropertyBagDTO());
    }

    public List<T> findByQueryList(T baseDTO, String queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryList ";
        logger.logBegin(METHODNAME);
        try {
            return this.findByQueryList(baseDTO, queryClass, new ArrayList(), sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public List<T> findByQueryList(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findByQueryList(baseDTO, queryClass, childClassDTOs, sessionDTO, new PropertyBagDTO());
    }

    public List<T> findByQueryList(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryList ";
        logger.logBegin(METHODNAME);
        try {
            return this.findByQueryList(this.baseBO, baseDTO, ClassUtils.dtoClassForName(baseDTO, queryClass), childClassDTOs, sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public List<T> customQueryList(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        return customQueryList(baseDTO, queryClass, childClassDTOs, sessionDTO, new PropertyBagDTO());
    }

    // Used for custom processing
    public List<T> customQueryList(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "customQueryList ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return baseBO.customQueryListMain(baseDTO, ClassUtils.dtoClassForName(baseDTO, queryClass), childClassDTOs, sessionDTO, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public T findByQuery(BaseBO<T> baseBO, T baseDTO, Class queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQuery ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return baseBO.findByQueryMain(baseDTO, queryClass, childClassDTOs, sessionDTO, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public T findByQuery(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findByQuery(baseDTO, queryClass, childClassDTOs, sessionDTO, new PropertyBagDTO());
    }

    public T findByQuery(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQuery ";
        logger.logBegin(METHODNAME);
        try {
            return this.findByQuery(this.baseBO, baseDTO, ClassUtils.dtoClassForName(baseDTO, queryClass), childClassDTOs, sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public T findByQuery(T baseDTO, String queryClass, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findByQuery(baseDTO, queryClass, sessionDTO, new PropertyBagDTO());
    }

    public T findByQuery(T baseDTO, String queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQuery ";
        logger.logBegin(METHODNAME);
        try {
            return (T) findByQuery(baseDTO, queryClass, new ArrayList(), sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public T customQuery(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(new PropertyBagDTO());
        return customQuery(baseDTO, queryClass, childClassDTOs, sessionDTO, newPropertyBagDTO);
    }

    // Used for custom processing
    public T customQuery(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "customQuery ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return baseBO.customQueryMain(baseDTO, ClassUtils.dtoClassForName(baseDTO, queryClass), childClassDTOs, sessionDTO, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    // Used for custom processing
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public T customSave(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        return customSave(baseDTO, queryClass, childClassDTOs, sessionDTO, new PropertyBagDTO());
    }

    // Used for custom processing
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public T customSave(T baseDTO, String queryClass, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "customSave ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return baseBO.customSaveMain(baseDTO, ClassUtils.dtoClassForName(baseDTO, queryClass), childClassDTOs, sessionDTO, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public T findByPrimaryKey(BaseBO<T> baseBO, Object primaryKey, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByPrimaryKey ";
        logger.logBegin(METHODNAME);
        long startTime = System.nanoTime();

        T baseDTO = null;
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            // Create a new instance of the BO's DTO
            baseDTO = (T) baseBO.getDtoClass().newInstance();

            // Set the PrimaryKey on it
            baseDTO.setPrimaryKey(primaryKey);

            // Find by the Primary Key
            baseDTO = baseBO.findByPrimaryKeyMain(baseDTO, childClassDTOs, sessionDTO, newPropertyBagDTO);

        } catch (InstantiationException ex) {
            throw new MtsException("An InstantiationException has occured, Message: " + ex.getMessage());
        } catch (IllegalAccessException ex) {
            throw new MtsException("An IllegalAccessException has occured, Message: " + ex.getMessage());
        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, startTime);                                                
            logger.logEnd(METHODNAME);
        }

        return baseDTO;
    }

    public T findByPrimaryKey(Object primaryKey, List<Class> childClassDTOs, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findByPrimaryKey(primaryKey, childClassDTOs, sessionDTO, new PropertyBagDTO());
    }

    public T findByPrimaryKey(Object primaryKey, List<Class> childClassDTOs, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByPrimaryKey ";
        logger.logBegin(METHODNAME);
        try {
            return this.findByPrimaryKey(this.baseBO, primaryKey, childClassDTOs, sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public T findByPrimaryKey(Object primaryKey, SessionDTO sessionDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findByPrimaryKey(primaryKey, sessionDTO, new PropertyBagDTO());
    }

    public T findByPrimaryKey(Object primaryKey, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByPrimaryKey ";
        logger.logBegin(METHODNAME);
        try {
            return (T) findByPrimaryKey(primaryKey, new ArrayList(), sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    protected <S> S findObjectByQuery(BaseBO<T> baseBO, T baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findStringByQuery ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return baseBO.findObjectByQueryMain(baseDTO, queryClass, sessionDTO, requiredType, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public <S> S findObjectByQuery(T baseDTO, String queryClass, SessionDTO sessionDTO, Class<S> requiredType)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findObjectByQuery(baseDTO, queryClass, sessionDTO, requiredType, new PropertyBagDTO());
    }

    public <S> S findObjectByQuery(T baseDTO, String queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findStringByQuery ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return this.findObjectByQuery(this.baseBO, baseDTO, ClassUtils.dtoClassForName(baseDTO, queryClass), sessionDTO, requiredType, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    protected <S> List<S> findObjectByQueryList(BaseBO<T> baseBO, T baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findStringByQueryList ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return baseBO.findObjectByQueryListMain(baseDTO, queryClass, sessionDTO, requiredType, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public <S> List<S> findObjectByQueryList(T baseDTO, String queryClass, SessionDTO sessionDTO, Class<S> requiredType)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        return findObjectByQueryList(baseDTO, queryClass, sessionDTO, requiredType, new PropertyBagDTO());
    }

    public <S> List<S> findObjectByQueryList(T baseDTO, String queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findStringByQueryList ";
        logger.logBegin(METHODNAME);
        try {
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return this.findObjectByQueryList(this.baseBO, baseDTO, ClassUtils.dtoClassForName(baseDTO, queryClass), sessionDTO, requiredType, newPropertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    public Map<String, byte[]> exportData(T baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "exportData ";
            PropertyBagDTO newPropertyBagDTO = getPropertyBagDTO(propertyBagDTO);
            return this.baseBO.exportDataMain(baseDTO, ClassUtils.dtoClassForName(baseDTO, propertyBagDTO.getQueryClass()), sessionDTO, newPropertyBagDTO);
    }

    public void importData(SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        this.baseBO.importDataMain(ClassUtils.dtoClassForName(dtoClass, propertyBagDTO.getQueryClass()), sessionDTO, propertyBagDTO);
    }

    private PropertyBagDTO getPropertyBagDTO(PropertyBagDTO propertyBagDTO) {
        PropertyBagDTO newPropertyBagDTO = propertyBagDTO;
        if (propertyBagDTO == null) {
            newPropertyBagDTO = new PropertyBagDTO();
        }
        // Identify the calling MGR
        newPropertyBagDTO.setCaller(this.getClass());
        return newPropertyBagDTO;
    }
}