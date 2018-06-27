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

import java.util.List;
import java.util.Map;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.ejb.remote.GeneralMGRRemote;
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
import org.cdsframework.rs.support.CoreRsConstants;
import org.cdsframework.util.ClassUtils;
import org.cdsframework.util.EJBUtils;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.ObjectUtils;
import org.cdsframework.util.StringUtils;

/**
 * The BaseGeneralMGR implements the {@link org.cdsframework.ejb.remote.GeneralMGRRemote [GeneralMGRRemote]} interface.
 *
 * @author HLN Consulting, LLC
 */
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public abstract class BaseGeneralMGR implements GeneralMGRRemote {

    protected final LogUtils logger;

    public BaseGeneralMGR(Class mgrClass) {
        logger = LogUtils.getLogger(mgrClass);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public <T extends BaseDTO> T save(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, ConstraintViolationException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "save ";
        long start = System.nanoTime();
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        logger.logBegin(METHODNAME);
        T result = null;
        try {
            DTOState operationDTOState = dto.getOperationDTOState();
            logger.debug(METHODNAME, "operationDTOState=" + operationDTOState);
            
            //
            // This is used to force logic below
            // Add support for DTO Stateless client, see RS client scenarios (curl, etc)
            //
            if (operationDTOState != DTOState.UPDATED) {
                boolean refreshDTO = ObjectUtils.objectToBoolean(propertyBagDTO.get(CoreRsConstants.REFRESH_DTO));
                if (refreshDTO) {
                    // Force logic below to call updateMain
                    operationDTOState = DTOState.UPDATED;
                }
                logger.debug(METHODNAME, "operationDTOState after refresh flag=" + operationDTOState);
            }
            
            Class opClass = null;
            String queryClass = propertyBagDTO.getQueryClass();
            if (!StringUtils.isEmpty(queryClass)) {
                opClass = ClassUtils.dtoClassForName(dto, queryClass);
            }
            logger.debug(METHODNAME, "opClass=", opClass);
            switch (operationDTOState) {
                case NEW:
                case NEWMODIFIED:
                    result = bo.addMain(dto, (opClass == null ? Add.class : opClass), sessionDTO, propertyBagDTO);
                    break;
                case UPDATED:
                    result = bo.updateMain(dto, (opClass == null ? Update.class : opClass), sessionDTO, propertyBagDTO);
                    break;
                case DELETED:
                    bo.deleteMain(dto, (opClass == null ? Delete.class : opClass), sessionDTO, propertyBagDTO);
                    break;
                case UNSET:
                    logger.warn(METHODNAME, "operationDTOState = UNSET - returning original...");
                    result = dto;
                    break;
                default:
                    throw new MtsException("Unexpected operationDTOState: " + operationDTOState);
            }
        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);                                                
            logger.logEnd(METHODNAME);
        }
        return result;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public <S extends BaseDTO> S newInstance(Class<S> dtoClass, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "newInstance ";
        logger.logBegin(METHODNAME);
        verifyNotNull(dtoClass, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        S resultDTO = null;
        try {
            BaseBO<S> bo = EJBUtils.getDtoBo(dtoClass);
            resultDTO = bo.newInstanceMain(sessionDTO, propertyBagDTO);
        } finally {
            logger.logEnd(METHODNAME);
        }
        return resultDTO;
    }

    @Override
    public <T extends BaseDTO> T findByPrimaryKey(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByPrimaryKey ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return (T) bo.findByPrimaryKeyMain(dto, (List) propertyBagDTO.getChildClassDTOs(), sessionDTO, propertyBagDTO);
    }

    @Override
    public <T extends BaseDTO> List<T> findByQueryList(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQueryList ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return bo.findByQueryListMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), (List) propertyBagDTO.getChildClassDTOs(), sessionDTO, propertyBagDTO);
    }

    @Override
    public <T extends BaseDTO> T findByQuery(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findByQuery ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return (T) bo.findByQueryMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), (List) propertyBagDTO.getChildClassDTOs(), sessionDTO, propertyBagDTO);
    }

    @Override
    public <S> List<S> findObjectByQueryList(BaseDTO dto, SessionDTO incomingSessionDTO, Class<S> requiredType, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findObjectByQueryList ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO bo = EJBUtils.getDtoBo(dto.getClass());
        return (List<S>) bo.findObjectByQueryListMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), sessionDTO, requiredType, propertyBagDTO);
    }

    @Override
    public <S> S findObjectByQuery(BaseDTO dto, SessionDTO incomingSessionDTO, Class<S> requiredType, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "findObjectByQuery ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO bo = EJBUtils.getDtoBo(dto.getClass());
        return (S) bo.findObjectByQueryMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), sessionDTO, requiredType, propertyBagDTO);
    }

    @Override
    public <T extends BaseDTO> List<T> customQueryList(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "customQueryList ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return bo.customQueryListMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), (List) propertyBagDTO.getChildClassDTOs(), sessionDTO, propertyBagDTO);
    }

    @Override
    public <T extends BaseDTO> T customQuery(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "customQuery ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return (T) bo.customQueryMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), (List) propertyBagDTO.getChildClassDTOs(), sessionDTO, propertyBagDTO);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public <T extends BaseDTO> T customSave(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException, ConstraintViolationException {
        final String METHODNAME = "customSave ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return (T) bo.customSaveMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), (List) propertyBagDTO.getChildClassDTOs(), sessionDTO, propertyBagDTO);
    }

    @Override
    public <T extends BaseDTO> Map<String, byte[]> exportData(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        final String METHODNAME = "exportData ";
        verifyNotNull(dto, METHODNAME);
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return bo.exportDataMain(dto, ClassUtils.dtoClassForName(dto, propertyBagDTO.getQueryClass()), sessionDTO, propertyBagDTO);
    }

    @Override
    public <T extends BaseDTO> void importData(Class<T> dtoClass, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException,
            ConstraintViolationException {
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dtoClass);
        bo.importDataMain(ClassUtils.dtoClassForName(dtoClass, propertyBagDTO.getQueryClass()), sessionDTO, propertyBagDTO);
    }

    @Override
    public <T extends BaseDTO> byte[] getReport(T dto, SessionDTO incomingSessionDTO, PropertyBagDTO incomingPropertyBagDTO)
            throws ValidationException, NotFoundException, MtsException, AuthenticationException, AuthorizationException {
        PropertyBagDTO propertyBagDTO = verifyPropertyBagDTO(incomingPropertyBagDTO);
        SessionDTO sessionDTO = verifySessionDTO(incomingSessionDTO);
        BaseBO<T> bo = EJBUtils.getDtoBo(dto.getClass());
        return bo.getReportMain(dto, sessionDTO, propertyBagDTO);
    }
    
    private PropertyBagDTO verifyPropertyBagDTO(PropertyBagDTO incomingPropertyBagDTO) {
        PropertyBagDTO propertyBagDTO = incomingPropertyBagDTO;
        if (propertyBagDTO == null) {
            propertyBagDTO = new PropertyBagDTO();
        }
        return propertyBagDTO;
    }

    private SessionDTO verifySessionDTO(SessionDTO incomingSessionDTO) {
        SessionDTO sessionDTO = incomingSessionDTO;
        if (sessionDTO == null) {
            sessionDTO = new SessionDTO();
        }
        return sessionDTO;
    }

    private <T extends BaseDTO> void verifyNotNull(T dto, String methodName) throws MtsException {
        if (dto == null) {
            throw new MtsException("The DTO that was passed to " + methodName + " was null.");
        }
    }

    private <S extends BaseDTO> void verifyNotNull(Class<S> dtoClass, String methodName) throws MtsException {
        if (dtoClass == null) {
            throw new MtsException("The DTO Class that was passed to " + methodName + " was null.");
        }
    }
}
