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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.cdsframework.annotation.ReferenceDTO;
import org.cdsframework.base.BaseBO;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.enumeration.DTOState;
import org.cdsframework.exceptions.AuthenticationException;
import org.cdsframework.exceptions.AuthorizationException;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.exceptions.ValidationException;
import org.cdsframework.group.Add;
import org.cdsframework.group.Update;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.EJBUtils;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.support.CoreConstants;
import org.cdsframework.util.support.DeepCopy;

/**
 * Provides a Reference manager to query common not cached data for reference purposes.
 *
 * @author HLN Consulting, LLC
 */
@Stateless
@LocalBean
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class ReferenceMGRLocal {

    private static final LogUtils logger = LogUtils.getLogger(ReferenceMGRLocal.class);

    /**
     * Sets a reference DTO on a parent DTO.
     *
     * @param parentDTO
     * @param queryClass
     * @param childClassDTOs
     * @param sessionDTO
     * @param propertyBagDTO
     * @throws MtsException
     * @throws ValidationException
     * @throws NotFoundException
     * @throws AuthenticationException
     * @throws AuthorizationException
     */
    public void findReferenceDTO(
            BaseDTO parentDTO,
            Class queryClass,
            List<Class> childClassDTOs,
            SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO)
            throws MtsException, ValidationException, NotFoundException, AuthenticationException, AuthorizationException {

        final String METHODNAME = "findReferenceDTO ";
        logger.logBegin(METHODNAME);
        logger.debug(METHODNAME, "parentDTOClass=", parentDTO);
        List<Field> fields = null;
        long start = System.nanoTime();

        try {
            if (parentDTO != null) {

                // Get the ReferenceDTO fields
                fields = parentDTO.getReferenceDTOs();
                logger.debug(METHODNAME, "fields: ", fields);

                // Navigate the field list
                for (Field field : fields) {

                    BaseDTO referenceDTO = null;

                    // Get the ReferenceDTO class
                    Class referenceDTOClass = field.getType();
                    logger.debug(METHODNAME, parentDTO, " - has referenceDTOClass: ", referenceDTOClass);

                    ReferenceDTO referenceDTOAnnotation = field.getAnnotation(ReferenceDTO.class);
                    logger.debug(METHODNAME, "referenceDTOAnnotation: ", referenceDTOAnnotation);

                    boolean isNotFoundAllowed = referenceDTOAnnotation.isNotFoundAllowed();
                    logger.debug(METHODNAME, "isNotFoundAllowed: ", isNotFoundAllowed, " referenceDTOClass=", referenceDTOClass);

                    boolean isDiscardChildren = referenceDTOAnnotation.discardChildren();
                    logger.debug(METHODNAME, "isDiscardChildren: ", isDiscardChildren, " referenceDTOClass=", referenceDTOClass);

                    // Map is created for selfReferencing Cached DTO's
                    HashMap<Object, BaseDTO> baseDTOMap = (HashMap<Object, BaseDTO>) propertyBagDTO.get(referenceDTOClass.getName());
                    logger.debug(METHODNAME, "referenceDTOClass=", referenceDTOClass, " baseDTOMap=", baseDTOMap);

                    if (baseDTOMap != null) {

                        try {
                            BaseDTO referenceKeyDTO = (BaseDTO) field.get(parentDTO);
                            logger.debug(METHODNAME, "referenceKeyDTO: ", referenceKeyDTO);

                            if (referenceKeyDTO != null) {
                                referenceDTO = baseDTOMap.get(referenceKeyDTO.getPrimaryKey());
                                logger.debug(METHODNAME, "found referenceDTO: ", referenceDTO);

                                if (referenceDTO != null) {
                                    logger.debug(METHODNAME,
                                            "found a match for referenceDTOClass.getName()=",
                                            referenceDTOClass,
                                            " referenceKeyDTO=",
                                            referenceKeyDTO);
                                    // Set the referenceDTO on the parentDTO
                                    field.set(parentDTO, referenceDTO);
                                } else {
                                    if (!isNotFoundAllowed) {
                                        logger.error(METHODNAME,
                                                "A NotFoundException has occurred on referenceKeyDTO.getPrimaryKey()=",
                                                referenceKeyDTO.getPrimaryKey(),
                                                " for class ", referenceKeyDTO.getClass());
                                        throw new MtsException("The ReferenceDTOClass " + referenceDTOClass.getName()
                                                + " primaryKey=" + referenceKeyDTO.getPrimaryKey() + " was not found in the map.");
                                    }
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            logger.error(METHODNAME + "An IllegalArgumentException has occurred, Message: " + e.getMessage(), e);
                        } catch (IllegalAccessException e) {
                            logger.error(METHODNAME + "An IllegalAccessException has occurred, Message: " + e.getMessage(), e);
                        }
                    } else {

                        BaseBO referenceBO = EJBUtils.getDtoBo(referenceDTOClass);
                        logger.debug(METHODNAME, "referenceBO=", referenceBO);

                        try {
                            // Get the referenceDTO
                            BaseDTO referenceKeyDTO = (BaseDTO) field.get(parentDTO);
                            logger.debug(METHODNAME, "referenceKeyDTO: ", referenceKeyDTO);

                            if (referenceKeyDTO != null) {
                                referenceDTO = referenceKeyDTO;
                                logger.debug(referenceBO, ": looking up - ", referenceKeyDTO);

                                try {
                                    boolean executeFindByPrimaryKey = true;
                                    if (referenceBO.isSelfReferencing()) {
                                        DTOUtils.processAncestorMap(parentDTO, referenceKeyDTO, propertyBagDTO);

                                        List<BaseDTO> ancestorList = DTOUtils.getAncestorListFromProbertyBagDTO(referenceKeyDTO, propertyBagDTO);
                                        int index = ancestorList.indexOf(referenceKeyDTO);
//                                        logger.debug(METHODNAME, "index=", index, "; parentDTO=", parentDTO, "; referenceKeyDTO=", referenceKeyDTO);
//                                        logger.debug(METHODNAME, "ancestorList=", ancestorList);
                                        if (index > -1) {
                                            BaseDTO ancestorDTO = ancestorList.get(index);
                                            referenceDTO = ancestorDTO;
//                                            DTOUtils.copyDtoDeclaredFields(ancestorDTO, referenceDTO);
                                            executeFindByPrimaryKey = false;
//                                            propertyBagDTO.getPropertyMap().put("recursiveNodeEncountered", true);
                                            logger.debug(METHODNAME, "Hit a recursive DTO: ", referenceKeyDTO);
                                            logger.debug(METHODNAME, "    ancestorList: ", ancestorList);
                                        }
                                    }

                                    if (executeFindByPrimaryKey) {
                                        // Get the referenceBO associated with the class and get the ReferenceDTO
                                        String callingMGR = CoreConstants.CALLINGMGR + getClass().getSimpleName();
                                        propertyBagDTO.put(callingMGR, true);
                                        referenceDTO = referenceBO.findByPrimaryKeyMain(referenceKeyDTO, childClassDTOs, sessionDTO, propertyBagDTO);
                                        logger.debug(METHODNAME, "found referenceDTO: ", referenceDTO);
                                        propertyBagDTO.remove(callingMGR);
                                    }

                                } catch (NotFoundException e) {
                                    logger.debug("NotFoundException encountered: ", isNotFoundAllowed);
                                    if (!isNotFoundAllowed) {
                                        logger.error(METHODNAME,
                                                "A NotFoundException has occurred on referenceKeyDTO.getPrimaryKey()=",
                                                referenceKeyDTO.getPrimaryKey(),
                                                " for class ", referenceKeyDTO.getClass());
                                        throw new MtsException("A NotFoundException has occurred, wrapped in an MtsException for rollback control.", e);
                                    }
                                }

                                // Set the referenceDTO on the parentDTO
                                field.set(parentDTO, referenceDTO);
                            }
                        } catch (IllegalArgumentException e) {
                            logger.error(METHODNAME, "An IllegalArgumentException has occurred, Message: ", e.getMessage(), e);
                        } catch (IllegalAccessException e) {
                            logger.error(METHODNAME, "An IllegalAccessException has occurred, Message: ", e.getMessage(), e);
                        }
                    }

                    // don't want children of referenceDTO present on field.
                    if (referenceDTO != null && isDiscardChildren) {
                        referenceDTO = DeepCopy.copy(referenceDTO);
                        referenceDTO.setChildDTOMap(new HashMap<Class, List<BaseDTO>>());
                    }
                }
            } else {
                logger.warn(METHODNAME, "Parent DTO was null: ", parentDTO);
            }
        } finally {
            logger.logEnd(METHODNAME);
            if (logger.isDebugEnabled()) {
                logger.logDuration(
                        METHODNAME + "for " + (fields != null ? fields.size() : 0) + " fields on "
                        + (parentDTO != null ? parentDTO.getClass().getSimpleName() : null), start);
            }
        }
    }

    /**
     * Method used to delete ReferenceDTOs, delegates to Reference BO's deleteMain.
     *
     * @param parentDTO
     * @param queryClass
     * @param childClassDTOs
     * @param sessionDTO
     * @param propertyBagDTO
     * @throws NotFoundException
     * @throws MtsException
     * @throws ValidationException
     * @throws AuthenticationException
     * @throws AuthorizationException
     * @throws ConstraintViolationException
     */
    public void deleteReferenceDTO(
            BaseDTO parentDTO,
            Class queryClass,
            List<Class> childClassDTOs,
            SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ValidationException,
            AuthenticationException, AuthorizationException, ConstraintViolationException {

        final String METHODNAME = "deleteReferenceDTO ";
        logger.logBegin(METHODNAME);
        logger.debug(METHODNAME, "parentDTO=", parentDTO);
        List<Field> referenceDTOs = null;
        long start = System.nanoTime();

        try {

            // must not be null
            if (parentDTO != null) {

                referenceDTOs = parentDTO.getReferenceDTOs();
                logger.debug(METHODNAME, "parentDTO.getReferenceDTOs().size()=", referenceDTOs.size());

                // process annotations
                for (Field referenceField : referenceDTOs) {

                    BaseDTO referenceDTO = getReferenceDTO(referenceField, parentDTO);
                    logger.debug(METHODNAME, "referenceDTO=", referenceDTO);

                    // must not be null
                    if (referenceDTO != null) {

                        ReferenceDTO referenceAnnotation = referenceField.getAnnotation(ReferenceDTO.class);

                        logger.debug(METHODNAME, "referenceDTO.getPrimaryKey()=", referenceDTO.getPrimaryKey());

                        // must have a primary key
                        if (referenceDTO.getPrimaryKey() != null) {

                            logger.debug(METHODNAME, "referenceDTO.isDeleted()=", referenceDTO.isDeleted());
                            logger.debug(METHODNAME, "referenceAnnotation.isDeleteCascade()=", referenceAnnotation.isDeleteCascade());

                            // must be delete cascade or deleted
                            if (referenceAnnotation.isDeleteCascade() || referenceDTO.isDeleted()) {

                                boolean deleteAllowed = DTOUtils.isDeleteAllowed(referenceDTO.getClass());
                                logger.debug(METHODNAME, "DTOUtils.isDeleteAllowed(referenceDTO.getClass())=", deleteAllowed);

                                // must be delete allowed
                                if (deleteAllowed) {

                                    logger.debug(METHODNAME, "Deleting: ", referenceDTO);

                                    referenceDTO.delete(true);
                                    BaseBO referenceBO = EJBUtils.getDtoBo(referenceDTO.getClass());
                                    referenceBO.deleteMain(referenceDTO, queryClass, sessionDTO, propertyBagDTO);
                                } else {
                                    logger.debug(METHODNAME, "delete not allowed!");
                                }
                            } else {
                                logger.debug(METHODNAME,
                                        "referenceAnnotation is not delete cascade or referenceDTO is not deleted: ",
                                        referenceAnnotation.isDeleteCascade(), ", ",
                                        referenceDTO.isDeleted());
                            }
                        } else {
                            logger.error(METHODNAME,
                                    "trying to delete a reference DTO that doesn't have a primary key: parentDTO=",
                                    parentDTO, "; referenceDTO=", referenceDTO);
                        }
                    } else {
                        logger.debug(METHODNAME, "referenceDTO is null!");
                    }
                }
            } else {
                logger.warn(METHODNAME, "Parent DTO was null: ", parentDTO);
            }
        } finally {
            logger.logEnd(METHODNAME);
            if (logger.isDebugEnabled()) {
                logger.logDuration(
                        METHODNAME + "for " + (referenceDTOs != null ? referenceDTOs.size() : 0) + " referenceDTOs on "
                        + (parentDTO != null ? parentDTO.getClass().getSimpleName() : null), start);
            }
        }
    }

    /**
     * Method used to add or update ReferenceDTOs, delegates to Reference BO's addMain/updateMain.
     *
     * @param parentDTO
     * @param queryClass
     * @param childClassDTOs
     * @param sessionDTO
     * @param propertyBagDTO
     * @throws NotFoundException
     * @throws MtsException
     * @throws ValidationException
     * @throws AuthenticationException
     * @throws AuthorizationException
     * @throws ConstraintViolationException
     */
    public void addOrUpdateReferenceDTO(
            BaseDTO parentDTO,
            Class queryClass,
            List<Class> childClassDTOs,
            SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException, ValidationException,
            AuthenticationException, AuthorizationException, ConstraintViolationException {

        final String METHODNAME = "addOrUpdateReferenceDTO ";
        logger.logBegin(METHODNAME);
        logger.debug(METHODNAME, "queryClass=", queryClass);
        List<Field> referenceDTOs = null;
        long start = System.nanoTime();

        try {

            // must not be null
            if (parentDTO != null) {

                referenceDTOs = parentDTO.getReferenceDTOs();
                logger.debug(METHODNAME, "parentDTO.getReferenceDTOs().size()=", referenceDTOs.size());

                for (Field referenceField : referenceDTOs) {

                    BaseDTO referenceDTO = getReferenceDTO(referenceField, parentDTO);

                    // must not be null
                    if (referenceDTO != null) {

                        // Check operation state as the referenceDTO may have children that may have changed
                        DTOState operationDTOState = referenceDTO.getOperationDTOState();
                        logger.debug(METHODNAME, "operationDTOState=", operationDTOState);

                        if (operationDTOState == DTOState.UPDATED
                                || operationDTOState == DTOState.NEW
                                || operationDTOState == DTOState.NEWMODIFIED) {

                            BaseBO referenceBO = EJBUtils.getDtoBo(referenceDTO.getClass());

                            if (operationDTOState == DTOState.UPDATED) {
                                logger.debug(METHODNAME, "Updating: ", referenceDTO);
                                if (queryClass == Add.class) {
                                    queryClass = Update.class;
                                }
                                referenceDTO = referenceBO.updateMain(referenceDTO, queryClass, sessionDTO, propertyBagDTO);
                            } else if (operationDTOState == DTOState.NEW
                                    || operationDTOState == DTOState.NEWMODIFIED) {
                                logger.debug(METHODNAME, "Adding: ", referenceDTO);
                                referenceDTO = referenceBO.addMain(referenceDTO, queryClass, sessionDTO, propertyBagDTO);
                            }
                            if (referenceDTO != null) {
                                try {
                                    referenceField.set(parentDTO, referenceDTO);
                                } catch (IllegalArgumentException e) {
                                    throw new MtsException(e.getMessage(), e);
                                } catch (IllegalAccessException e) {
                                    throw new MtsException(e.getMessage(), e);
                                }
                            } else {
                                throw new MtsException("After an Add/Update ReferenceDTO the return value is null!");
                            }
                        }
                    } else {
                        logger.debug(METHODNAME, "referenceDTO was null!");
                    }

                }
            } else {
                logger.warn("Parent DTO was null: ", parentDTO);
            }
        } finally {
            logger.logEnd(METHODNAME);
//            logger.logDuration(
//                    METHODNAME + "for " + (referenceDTOs != null ? referenceDTOs.size() : 0) + " referenceDTOs on "
//                    + (parentDTO != null ? parentDTO.getClass().getSimpleName() : null), start);
        }
    }

    /**
     * Get the reference DTO instance off of the parent if it is updateable and not read-only.
     *
     * @param referenceField
     * @param parentDTO
     * @return
     * @throws MtsException
     */
    private BaseDTO getReferenceDTO(Field referenceField, BaseDTO parentDTO) throws MtsException {

        final String METHODNAME = "getReferenceDTO ";
        BaseDTO referenceDTO = null;

        // must not be null
        if (referenceField == null) {
            throw new MtsException(METHODNAME + "referenceField is null!");
        }

        // must not be null
        if (parentDTO == null) {
            throw new MtsException(METHODNAME + "parentDTO is null!");
        }

        Class<? extends BaseDTO> referenceDtoClass = (Class<? extends BaseDTO>) referenceField.getType();
        logger.debug(METHODNAME, parentDTO, " - has referenceDTOClass: ", referenceDtoClass);

        ReferenceDTO referenceAnnotation = referenceField.getAnnotation(ReferenceDTO.class);
        logger.debug(METHODNAME, referenceField, " - has referenceAnnotation: ", referenceAnnotation);

        logger.debug(METHODNAME, "referenceAnnotation.isUpdateable()=", referenceAnnotation.isUpdateable());

        // must be updateable
        if (referenceAnnotation.isUpdateable()) {

            boolean readOnly = DTOUtils.isReadOnly(referenceDtoClass);
            logger.debug(METHODNAME, "DTOUtils.isReadOnly(referenceDtoClass)=", readOnly);

            // must not be read only
            if (!readOnly) {

                try {
                    referenceDTO = (BaseDTO) referenceField.get(parentDTO);
                    logger.debug(METHODNAME, "referenceDTO=", referenceDTO);
                } catch (IllegalAccessException e) {
                    throw new MtsException("This should not happen!", e);
                }

            } else {
                logger.debug(METHODNAME, "ReferenceDTO is read only!");
            }
        } else {
            logger.debug(METHODNAME, "ReferenceDTO is not updateable!");
        }
        return referenceDTO;
    }
}
