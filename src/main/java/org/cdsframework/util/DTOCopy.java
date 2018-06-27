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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.bind.annotation.XmlTransient;
import org.apache.commons.beanutils.PropertyUtils;
import org.cdsframework.annotation.ReferenceDTO;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.enumeration.DTOState;
import org.cdsframework.exceptions.MtsException;

/**
 *
 * @author HLN Consulting LLC
 */
public class DTOCopy {

    private final static LogUtils logger = LogUtils.getLogger(DTOCopy.class);

    public static BaseDTO copyProperty(BaseDTO sourceDTO, BaseDTO targetDTO) throws MtsException {
        final String METHODNAME = "copyProperty ";

        Class objClass = targetDTO.getClass();
        List<Field> fields = ClassUtils.getNonBaseDTODeclaredFields(objClass);
        for (Field field : fields) {
            try {
                XmlTransient xmlTransient = field.getAnnotation(XmlTransient.class);
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                if (xmlTransient == null || jsonProperty != null ) {
                    DTOCopy.copyProperty(sourceDTO, targetDTO, field);
                }
            } catch (java.lang.ArrayStoreException e) {
                logger.error(METHODNAME + "field.getName(): " + field.getName() + " caused an exception " + e.getMessage());
                DTOCopy.copyProperty(sourceDTO, targetDTO, field);
            }
        }
        
        copyChildDTOMap(sourceDTO, targetDTO);
        return targetDTO;
    }

    private static void copyChildDTOMap(BaseDTO sourceDTO, BaseDTO targetDTO) {
        if (!sourceDTO.getChildDTOMap().isEmpty()) {
            Set<Entry<Class, List<BaseDTO>>> childDTOMapEntrySet = sourceDTO.getChildDTOMap().entrySet();

            for (Entry<Class, List<BaseDTO>> dtoMapEntry : childDTOMapEntrySet) {
                List<BaseDTO> sourceClassDTOs = dtoMapEntry.getValue();
                for (BaseDTO dto : sourceClassDTOs) {

                    // If there is a primary key, its likely going to be updated
                    if (dto.getPrimaryKey() != null) {
                        //
                        // Is there a supplied DTO State? (typically from a RS client its not supplied) but there are exceptions
                        // If it is supplied it could be NEW or DELETE
                        //
                        // NEW DTO's with the primary key assigned are NOT typical, but there could be exceptions in the future
                        // To delete a specific set of the Parent DTO's children, the child DTO, DTOState must be set to DELETED
                        //
                        if (dto.getDTOState() == null) {
                            DTOUtils.setDTOState(dto, DTOState.UPDATED);
                        }
                    }
                    else {
                        // No Primary Key, defaults to NEW DTO, not exceptions, what else could it be
                        DTOUtils.setDTOState(dto, DTOState.NEW);
                    }
                }
            }
            targetDTO.getChildDTOMap().putAll(sourceDTO.getChildDTOMap());
        }
    }
    
    private static void setDTOState(BaseDTO sourceDTO) {
        Set<Entry<Class, List<BaseDTO>>> childDTOMapEntrySet = sourceDTO.getChildDTOMap().entrySet();
        
        for (Entry<Class, List<BaseDTO>> dtoMapEntry : childDTOMapEntrySet) {
            List<BaseDTO> sourceClassDTOs = dtoMapEntry.getValue();
            for (BaseDTO dto : sourceClassDTOs) {
                
                // If there is a primary key, its likely going to be updated
                if (dto.getPrimaryKey() != null) {
                    //
                    // Is there a supplied DTO State? (typically from a RS client its not supplied) but there are exceptions
                    // If it is supplied it could be NEW or DELETE
                    //
                    // NEW DTO's with the primary key assigned are NOT typical, but there could be exceptions in the future
                    // To delete a specific set of the Parent DTO's children, the child DTO, DTOState must be set to DELETED
                    //
                    if (dto.getDTOState() == null) {
                        DTOUtils.setDTOState(dto, DTOState.UPDATED);
                    }
                }
                else {
                    // No Primary Key, defaults to NEW DTO, not exceptions, what else could it be
                    DTOUtils.setDTOState(dto, DTOState.NEW);
                }
                
//                setDTOState(dto);
            }
        }
        
    }
    
    private static void copyProperty(BaseDTO sourceDTO, BaseDTO targetDTO, Field field) throws MtsException {
        final String METHODNAME = "copyProperty ";
        try {
            Object sourceValue = PropertyUtils.getProperty(sourceDTO, field.getName());
            if (logger.isDebugEnabled()) {
                logger.debug(METHODNAME + "sourceValue=" + sourceValue);
            }

            if (sourceValue instanceof BaseDTO) {
                Object targetValue = PropertyUtils.getProperty(targetDTO, field.getName());
                ReferenceDTO referenceDTOAnnotation = field.getAnnotation(ReferenceDTO.class);

                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME + "sourceValue is a BaseDTO, field.getName()=" + field.getName() + " sourceValue.getClass().getSimpleName()=" + sourceValue.getClass().getSimpleName());
                    logger.debug(METHODNAME + "targetValue=" + targetValue);
                    logger.debug(METHODNAME + "referenceDTOAnnotation=" + referenceDTOAnnotation);
                }

//                if (!(targetValue instanceof BaseDTO)) {
//                    throw new MtsException("targetValue is NOT a BaseDTO, field.getName()=" + field.getName());
//                }
                if (referenceDTOAnnotation != null) {
                    if (referenceDTOAnnotation.isUpdateable()) {
                        if (logger.isDebugEnabled()) {
                            logger.debug(METHODNAME + "referenceDTO is updated, sourceValue.getClass().getSimpleName()=" + sourceValue.getClass().getSimpleName());
                        }
                        copyProperty((BaseDTO) sourceValue, (BaseDTO) targetValue);
                    } else {
                        //
                        // Handle case where Primary Key is null or empty string
                        // Unless its an instanceof the FacilityClassificationDTO where its allow come to through without its Primary Key
                        //
                        BaseDTO sourceValueDTO = (BaseDTO) sourceValue;
//                        if (!(sourceValueDTO instanceof FacilityClassificationDTO)) {
                            if (!DTOUtils.hasPrimaryKey(sourceValueDTO, true)) {
                                sourceValue = null;
                            }
//                        }
                        PropertyUtils.setProperty(targetDTO, field.getName(), sourceValue);
                        targetDTO.propertyChanged(field.getName(), sourceValue, true);
                    }
                }

            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(METHODNAME + "sourceValue=" + sourceValue + " field.getName()=" + field.getName());
                }

                // Handle empty String vs NULL case
                if (sourceValue != null && sourceValue instanceof String) {
                    String stringValue = ((String) sourceValue).trim();
                    if (stringValue.length() == 0) {
                        sourceValue = null;
                    }
                }
                PropertyUtils.setProperty(targetDTO, field.getName(), sourceValue);
                targetDTO.propertyChanged(field.getName(), sourceValue, true);
            }

        } catch (IllegalAccessException ex) {
            logger.error(METHODNAME + "An IllegalAccessException has occurred; Message: " + ex.getMessage());
            throw new MtsException(ex.getMessage(), ex);
        } catch (InvocationTargetException ex) {
            logger.error(METHODNAME + "An InvocationTargetException has occurred; Message: " + ex.getMessage());
            throw new MtsException(ex.getMessage(), ex);
        } catch (NoSuchMethodException ex) {
            logger.error(METHODNAME + "An NoSuchMethodException has occurred; Message: " + ex.getMessage());
            throw new MtsException(ex.getMessage(), ex);
        }

    }

}
