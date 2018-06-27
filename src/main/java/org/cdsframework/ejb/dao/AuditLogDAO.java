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
package org.cdsframework.ejb.dao;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.ejb.Stateless;
import org.apache.commons.beanutils.PropertyUtils;
import org.cdsframework.base.BaseDAO;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.callback.QueryCallback;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.dto.AuditLogDTO;
import org.cdsframework.enumeration.AuditStatus;
import org.cdsframework.enumeration.AuditTransaction;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.enumeration.Operation;
import org.cdsframework.enumeration.Operator;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.group.Add;
import org.cdsframework.group.ByGeneralProperties;
import org.cdsframework.util.JsonUtils;
import org.cdsframework.util.AuthenticationUtils;
import org.cdsframework.util.DateUtils;
import org.cdsframework.util.StringUtils;
import org.cdsframework.util.support.CorePropertyChangeEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 *
 * @author HLN Consulting LLC
 */
@Stateless
public class AuditLogDAO extends BaseDAO<AuditLogDTO> {

    @Override
    protected void initialize() throws MtsException {
//
//        // Return all
//        this.registerDML(FindAll.class, new QueryCallback(getDtoTableName()) {
//            @Override
//            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
//                return getSelectDML();
//            }
//        }, false);       

        // Return all
        registerDML(AuditLogDTO.ByTransactionId.class, new QueryCallback(getDtoTableName()) {
            @Override
            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                return getSelectDML() + " WHERE " + getTableAlias() + "transaction_id = :transaction_id";
            }
            
            @Override
            protected void getCallbackNamedParameters(MapSqlParameterSource namedParameters, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws MtsException {
                if (baseDTO instanceof AuditLogDTO) {
                    namedParameters.addValue("transaction_id", ((AuditLogDTO) baseDTO).getTransactionId());
                }
            }
        }, false);

        registerDML(ByGeneralProperties.class, new QueryCallback(getDtoTableName()) {
            @Override
            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                final String METHODNAME = "getQueryDML ";
                String queryDML = null;
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "audit_log_id) = :audit_log_id ", "auditLogId");
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "transaction_id) = :transaction_id ", "transactionId");
                setDetectWildcardPredicateValue(baseDTO, " (lower(" + getTableAlias() + "audit_id) = :audit_id or lower(a.audit_id) = :audit_id) ", "auditId");
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "transaction_type) = :transaction_type ", "transactionType");
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "status) = :status ", "status");
                setDetectWildcardPredicateValue(baseDTO, " (lower(" + getTableAlias() + "class_name) = :class_name or lower(a.class_name) = :class_name) ", "className");
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "property_name) = :property_name ", "propertyName");
                setDetectWildcardPredicateValue(baseDTO, " lower(a.app_name) = :app_name ", "appName");
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "old_value) = :old_value ", "oldValue");
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "new_value) = :new_value ", "newValue");
                setDetectWildcardPredicateValue(baseDTO, " lower(" + getTableAlias() + "last_mod_id) = :last_mod_id ", "lastModId");
                setDetectWildcardPredicateValue(baseDTO, " lower(a.create_id) = :create_id ", "createId");

                Object oStartCreateDatetime = baseDTO.getQueryMap().get("startCreateDatetime");
                Object oStopCreateDatetime = baseDTO.getQueryMap().get("stopCreateDatetime");

                if (oStartCreateDatetime != null) {
                    baseDTO.getQueryMap().put("startCreateDatetime", DateUtils.getFormattedDate((Date) oStartCreateDatetime, DateUtils.DATEINMASK));
                    String operator = "=";
                    if (oStopCreateDatetime != null) {
                        operator = ">=";
                    }
                    setNonWildcardPredicateValue(baseDTO, " date(a.create_datetime) " + operator + " date(:start_create_datetime) ", "startCreateDatetime");
                }
                if (oStopCreateDatetime != null) {
                    baseDTO.getQueryMap().put("stopCreateDatetime", DateUtils.getFormattedDate((Date) oStopCreateDatetime, DateUtils.DATEINMASK));
                    String operator = "=";
                    if (oStartCreateDatetime != null) {
                        operator = "<=";
                    }
                    setNonWildcardPredicateValue(baseDTO, " date(a.create_datetime) " + operator + " date(:stop_create_datetime) ", "stopCreateDatetime");
                }

                queryDML = getSelectDML().trim() + ", audit_transaction a WHERE " + getTableAlias() + "transaction_id = a.transaction_id " + getAndClearPredicateMap("AND", "", Operator.AND);
                logger.debug(METHODNAME, "queryDML=", queryDML);
                return queryDML;
            }

            @Override
            protected void getCallbackNamedParameters(MapSqlParameterSource namedParameters, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws MtsException {
                setLowerQueryMapValue(baseDTO, "audit_log_id", "auditLogId", namedParameters);
                setLowerQueryMapValue(baseDTO, "transaction_id", "transactionId", namedParameters);
                setLowerQueryMapValue(baseDTO, "audit_id", "auditId", namedParameters);
                setLowerQueryMapValue(baseDTO, "transaction_type", "transactionType", namedParameters);
                setLowerQueryMapValue(baseDTO, "status", "status", namedParameters);
                setLowerQueryMapValue(baseDTO, "app_name", "appName", namedParameters);
                setLowerQueryMapValue(baseDTO, "class_name", "className", namedParameters);
                setLowerQueryMapValue(baseDTO, "property_name", "propertyName", namedParameters);
                setLowerQueryMapValue(baseDTO, "old_value", "oldValue", namedParameters);
                setLowerQueryMapValue(baseDTO, "new_value", "newValue", namedParameters);
                setLowerQueryMapValue(baseDTO, "last_mod_id", "lastModId", namedParameters);
                setLowerQueryMapValue(baseDTO, "create_id", "createId", namedParameters);
                setExactQueryMapValue(baseDTO, "start_create_datetime", "startCreateDatetime", namedParameters);
                setExactQueryMapValue(baseDTO, "stop_create_datetime", "stopCreateDatetime", namedParameters);
            }

        }, false);

//        // Register Table Mapper
//        registerTableMapper(getDtoTableName(), new BaseRowMapper<AuditLogDTO>() {
//
//            @Override
//            public MapSqlParameterSource getNamedParameters(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
//                final String METHODNAME = "getNamedParameters ";
//                MapSqlParameterSource namedParameters = new MapSqlParameterSource();
//                if (baseDTO == null) {
//                    logger.warn("Null baseDTO submitted. Returning empty MapSqlParameterSource.");
//                    return namedParameters;
//                }
//                if (queryClass == ByGeneralProperties.class) {
//                    setExactQueryMapValue(baseDTO, "audit_log_id", "auditLogId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "transaction_id", "transactionId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "audit_id", "auditId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "transaction_type", "transactionType", namedParameters);
//                    setExactQueryMapValue(baseDTO, "status", "status", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "class_name", "className", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "property_name", "propertyName", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "old_value", "oldValue", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "new_value", "newValue", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "last_mod_id", "lastModId", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "create_id", "createId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "start_create_datetime", "startCreateDatetime", namedParameters);
//                    setExactQueryMapValue(baseDTO, "stop_create_datetime", "stopCreateDatetime", namedParameters);
//                } else {
//                    AuditLogDTO parameter = (AuditLogDTO) baseDTO;
//                    if (queryClass == AuditLogDTO.ByTransactionId.class) {
//                        namedParameters.addValue("transaction_id", parameter.getTransactionId());
//                    } else {
//                        namedParameters.addValue("audit_log_id", parameter.getAuditLogId());
//                        namedParameters.addValue("transaction_id", parameter.getTransactionId());
//                        namedParameters.addValue("audit_id", parameter.getAuditId());
//                        namedParameters.addValue("status", parameter.getStatus() != null ? parameter.getStatus().toString() : null);
//                        namedParameters.addValue("transaction_type", parameter.getTransactionType() != null ? parameter.getTransactionType().toString() : null);
//                        namedParameters.addValue("class_name", parameter.getClassName());
//                        namedParameters.addValue("property_name", parameter.getPropertyName());
//                        String oldValue = parameter.getOldValue();
//                        if (oldValue != null && oldValue.length() > 1024) {
//                            logger.warn(METHODNAME, "old value is larger than 1024 for property: ", parameter.getPropertyName());
//                            oldValue = oldValue.substring(0, 1020) + "...";
//                            logger.warn(METHODNAME, "truncating old value to: ", oldValue);
//                        }
//                        String newValue = parameter.getNewValue();
//                        if (newValue != null && newValue.length() > 1024) {
//                            logger.warn(METHODNAME, "new value is larger than 1024 for property: ", parameter.getPropertyName());
//                            newValue = newValue.substring(0, 1020) + "...";
//                            logger.warn(METHODNAME, "truncating new value to: ", oldValue);
//                        }
//                        namedParameters.addValue("old_value", oldValue);
//                        namedParameters.addValue("new_value", newValue);
//                        addStdCreateModParameters(namedParameters, parameter);
//                    }
//                }
//                return namedParameters;
//            }
//
//            @Override
//            public AuditLogDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
//                AuditLogDTO row = new AuditLogDTO();
//                row.setAuditLogId(rs.getString("audit_log_id"));
//                row.setTransactionId(rs.getString("transaction_id"));
//                row.setAuditId(rs.getString("audit_id"));
//                row.setStatus(rs.getString("status") != null ? AuditStatus.valueOf(rs.getString("status")) : null);
//                row.setTransactionType(rs.getString("transaction_type") != null ? AuditTransaction.valueOf(rs.getString("transaction_type")) : null);
//                row.setClassName(rs.getString("class_name"));
//                row.setPropertyName(rs.getString("property_name"));
//                row.setOldValue(rs.getString("old_value"));
//                row.setNewValue(rs.getString("new_value"));
//                mapStdCreateModProperties(rs, row);
//                return row;
//            }
//        });
    }

    public void auditLog(BaseDTO baseDTO, Operation operation, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, MtsException, NotFoundException {
        final String METHODNAME = "auditLog ";
        logger.debug(METHODNAME, "dtoClass=", getDtoClass(), " operation=", operation);
        long startTime = System.nanoTime();
        try {
            List<AuditLogDTO> auditLogDTOs = new ArrayList<AuditLogDTO>();

            // Get an Audit Transaction Id to start the grouping, as many DTO's may be added or updated or deleted
            logger.debug(METHODNAME, "propertyBagDTO.getAuditTransactionId()=", propertyBagDTO.getAuditTransactionId());
            logger.debug(METHODNAME, "baseDTO.getClass().getCanonicalName()=", baseDTO.getClass().getCanonicalName());
            Map<String, CorePropertyChangeEvent> propertyChangeEventMap = baseDTO.getPropertyChangeEventMap();

            // NOT deleting
            if (operation == Operation.UPDATE) {
                // lastModDatetime and lastModId
                for (Map.Entry<String, CorePropertyChangeEvent> propertyChangeEventEntry : propertyChangeEventMap.entrySet()) {
                    logger.debug(METHODNAME, "propertyChangeEventEntry.getKey()=", propertyChangeEventEntry.getKey());
                    String propertyName = propertyChangeEventEntry.getValue().getPropertyName();
                    if (propertyName.equalsIgnoreCase("lastModDatetime") || propertyName.equalsIgnoreCase("lastModId") || propertyName.equalsIgnoreCase("auditId")) {
                        // Skip it
                        continue;
                    }
                    Object oOldValue = propertyChangeEventEntry.getValue().getOldValue();
                    Object oNewValue = propertyChangeEventEntry.getValue().getNewValue();
                    boolean valueChanged = false;
                    logger.debug(METHODNAME, "valueChanged=", valueChanged);

                    // Instance of BaseDTO
                    if ((oOldValue != null && oOldValue instanceof BaseDTO)
                            || (oNewValue != null && oNewValue instanceof BaseDTO)) {
                        Object oOldValuePrimaryKey = null;
                        Object oNewValuePrimaryKey = null;
                        if (oOldValue != null) {
                            oOldValuePrimaryKey = ((BaseDTO) oOldValue).getPrimaryKey();
                        }
                        if (oNewValue != null) {
                            oNewValuePrimaryKey = ((BaseDTO) oNewValue).getPrimaryKey();
                        }
                        logger.debug(METHODNAME, "oOldValuePrimaryKey=", oOldValuePrimaryKey);
                        logger.debug(METHODNAME, "oNewValuePrimaryKey=", oNewValuePrimaryKey);

                        if (oOldValuePrimaryKey != null || oNewValuePrimaryKey != null) {
                            valueChanged = (oOldValuePrimaryKey != null && !oOldValuePrimaryKey.equals(oNewValuePrimaryKey))
                                    || (oNewValuePrimaryKey != null && !oNewValuePrimaryKey.equals(oOldValuePrimaryKey));
                        }
                    } else {
                        // Instance of java.lang
                        valueChanged = (oOldValue != null && !oOldValue.equals(oNewValue))
                                || (oNewValue != null && !oNewValue.equals(oOldValue));
                    }

                    if (valueChanged) {
                        logger.debug(METHODNAME, "valueChanged=", valueChanged, " oOldValue=", oOldValue, " oNewValue=", oNewValue);
                        auditLogDTOs.addAll(getAuditLogDTOs(baseDTO, propertyName, oOldValue, oNewValue, sessionDTO, propertyBagDTO));
                    }
                }
            } // Handle Add/Deletes
            else {
                String canonicalName = baseDTO.getClass().getCanonicalName();
                try {
                    // Get the PropertyNames that are being tracked
                    Object[] propertyNames = Class.forName(canonicalName + "$PropertyName").getEnumConstants();

                    // Navigate the PropertyNames
                    for (Object propertyName : propertyNames) {
                        try {
                            Object oOldValue = null;
                            Object oNewValue = null;

                            //
                            // Check if the user changed a value before deleting the record
                            // If the user changed a value before the delete, the property on the DTO will have the new value
                            // But we want the old value
                            //
                            if (operation == Operation.DELETE) {
                                CorePropertyChangeEvent propertyChangeEvent = propertyChangeEventMap.get(propertyName.toString());
                                if (propertyChangeEvent != null) {
                                    oOldValue = propertyChangeEvent.getOldValue();
                                } else {
                                    // Get it from the property
                                    oOldValue = PropertyUtils.getProperty(baseDTO, propertyName.toString());
                                }
                            } else if (operation == Operation.ADD) {
                                // Get it from the property
                                oNewValue = PropertyUtils.getProperty(baseDTO, propertyName.toString());
                            }

                            logger.debug(METHODNAME, "propertyName=", propertyName, " oOldValue=", oOldValue, " oNewValue=", oNewValue);
                            if (oOldValue != oNewValue) {
                                auditLogDTOs.addAll(getAuditLogDTOs(baseDTO, propertyName.toString(), oOldValue, oNewValue, sessionDTO, propertyBagDTO));
                            }

                        } catch (IllegalAccessException ex) {
                            logger.error(METHODNAME, "An IllegalAccessException has occurred; Message: ", ex.getMessage(), ex);
                            throw new MtsException(ex.getMessage(), ex);
                        } catch (InvocationTargetException ex) {
                            logger.error(METHODNAME, "An InvocationTargetException has occurred; Message: ", ex.getMessage(), ex);
                            throw new MtsException(ex.getMessage(), ex);
                        } catch (NoSuchMethodException ex) {
                            logger.error(METHODNAME, "An NoSuchMethodException has occurred; Message: ", ex.getMessage(), ex);
                            throw new MtsException(ex.getMessage(), ex);
                        }

                    }
                } catch (ClassNotFoundException ex) {
                    logger.error(METHODNAME, "An ClassNotFoundException has occurred; Message: ", ex.getMessage(), ex);
                    throw new MtsException(ex.getMessage(), ex);
                }
            }

            // Add the Audit Logs
            logger.debug(METHODNAME, "auditLogDTOs.size()=", auditLogDTOs.size());
            for (AuditLogDTO auditLogDTO : auditLogDTOs) {
                add(auditLogDTO, Add.class, AuthenticationUtils.getInternalSessionDTO(), propertyBagDTO);
            }
        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, startTime);
        }

    }

    private List<AuditLogDTO> getAuditLogDTOs(BaseDTO baseDTO, String propertyName, Object oldValue, Object newValue, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws MtsException {
        final String METHODNAME = "getAuditLogDTOs ";
        List<AuditLogDTO> auditLogDTOs = new ArrayList<AuditLogDTO>();

        // Handle the List
        if ((oldValue != null && oldValue instanceof List) || (newValue != null && newValue instanceof List)) {

            //
            // Change to deduce what was added versus what was deleted
            // Current audit shows complete list, can deduce what was added/deleted with whats there
            //
            boolean compared = false;
            if (oldValue != null && newValue != null) {
                // FUTURE if necessary, perform list compare, which change TransactionType to INSERT/DELETE
                // compared = true;
            }

            if (!compared && oldValue != null) {
                List<Object> oOldValues = (List<Object>) oldValue;
                for (Object oOldValue : oOldValues) {
                    auditLogDTOs.add(getAuditLogDTO(baseDTO, propertyName, oOldValue, null, sessionDTO, propertyBagDTO));
                }
            }
            if (!compared && newValue != null) {
                List<Object> oNewValues = (List<Object>) newValue;
                for (Object oNewValue : oNewValues) {
                    auditLogDTOs.add(getAuditLogDTO(baseDTO, propertyName, null, oNewValue, sessionDTO, propertyBagDTO));
                }
            }
        } else {
            // Handle non list types
            auditLogDTOs.add(getAuditLogDTO(baseDTO, propertyName, oldValue, newValue, sessionDTO, propertyBagDTO));
        }

        return auditLogDTOs;
    }

    private Object getPrimaryKey(BaseDTO baseDTO) throws MtsException {
        Object primaryKey = baseDTO.getPrimaryKey();
        Object value = null;
        if (primaryKey instanceof Map) {
            try {
                value = JsonUtils.getJsonFromPrimaryKey(baseDTO);
            } catch (IOException ex) {
                throw new MtsException("An IOException has occurred; Message: " + ex.getMessage(), ex);
            }
        } else {
            value = primaryKey.toString();
        }

        return value;
    }

    private AuditLogDTO getAuditLogDTO(BaseDTO baseDTO, String propertyName, Object oldValue, Object newValue, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws MtsException {
        final String METHODNAME = "getAuditLogDTO ";
        AuditLogDTO auditLogDTO = getAuditLogDTO();
        auditLogDTO.setAuditLogId(StringUtils.getHashId());
        auditLogDTO.setAuditId(baseDTO.getAuditId());
        auditLogDTO.setTransactionId(propertyBagDTO.getAuditTransactionId());
        auditLogDTO.setClassName(baseDTO.getClass().getCanonicalName());
        auditLogDTO.setPropertyName(propertyName);
        Date now = new Date();
        String username = sessionDTO.getUserDTO().getUsername();
        auditLogDTO.setCreateId(username);
        auditLogDTO.setCreateDatetime(now);

        auditLogDTO.setStatus(AuditStatus.APPLIED);

        // Using the propertyName test whether this is a known data type or DTO
        // For an Add Old Value will be null and New Value will be valued
        // For an Update Old Value will be original value and New Value will be the changed value
        if (baseDTO.isNew()) {
            auditLogDTO.setTransactionType(AuditTransaction.INSERT);
        } else if (baseDTO.isUpdated()) {
            auditLogDTO.setTransactionType(AuditTransaction.UPDATE);
        } else if (baseDTO.isDeleted()) {
            auditLogDTO.setTransactionType(AuditTransaction.DELETE);
        }

        // Handle case where oldValue || newValue is a BaseDTO and PrimaryKey is null
        if ((oldValue != null && oldValue instanceof BaseDTO)
                || (newValue != null && newValue instanceof BaseDTO)) {
            if (oldValue != null && (oldValue instanceof BaseDTO) && ((BaseDTO) oldValue).getPrimaryKey() != null) {
                oldValue = getPrimaryKey((BaseDTO) oldValue);
            } else {
                oldValue = null;
            }
            if (newValue != null && (newValue instanceof BaseDTO) && ((BaseDTO) newValue).getPrimaryKey() != null) {
                newValue = getPrimaryKey((BaseDTO) newValue);
            } else {
                newValue = null;
            }
        }

        // Handle Dates
        if (oldValue != null) {
            if (oldValue instanceof Date) {
                oldValue = DateUtils.getFormattedDate((Date) oldValue, DateUtils.ISO8601_DATETIME_FORMAT);
            }
        }
        if (newValue != null) {
            if (newValue instanceof Date) {
                newValue = DateUtils.getFormattedDate((Date) newValue, DateUtils.ISO8601_DATETIME_FORMAT);
            }
        }

        // Set Old Value
        if (oldValue != null) {
            auditLogDTO.setOldValue(oldValue.toString());
        } else {
            auditLogDTO.setOldValue(null);
        }
        // Set New Value
        if (newValue != null) {
            auditLogDTO.setNewValue(newValue.toString());
        } else {
            auditLogDTO.setNewValue(null);
        }
        return auditLogDTO;
    }

    private AuditLogDTO getAuditLogDTO() throws MtsException {
        final String METHODNAME = "getAuditLogDTO ";

        AuditLogDTO auditLogDTO = null;
        try {
            auditLogDTO = (AuditLogDTO) getDtoClass().newInstance();
        } catch (InstantiationException ex) {
            logger.error(METHODNAME + "An InstantiationException has occurred; Message: " + ex.getMessage(), ex);
            throw new MtsException(ex.getMessage(), ex);
        } catch (IllegalAccessException ex) {
            logger.error(METHODNAME + "An IllegalAccessException has occurred; Message: " + ex.getMessage(), ex);
            throw new MtsException(ex.getMessage(), ex);
        }
        return auditLogDTO;
    }
}
