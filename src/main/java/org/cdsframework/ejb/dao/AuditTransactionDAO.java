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

import java.util.Date;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import org.cdsframework.base.BaseDAO;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.callback.QueryCallback;
import org.cdsframework.dto.AuditTransactionDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.enumeration.AuditTransaction;
import org.cdsframework.enumeration.DTOState;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.enumeration.Operation;
import org.cdsframework.enumeration.Operator;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.group.Add;
import org.cdsframework.group.ByGeneralProperties;
import org.cdsframework.util.DateUtils;
import org.cdsframework.util.ObjectUtils;
import org.cdsframework.util.support.CoreConstants;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 *
 * @author HLN Consulting LLC
 */
@Stateless 
public class AuditTransactionDAO extends BaseDAO<AuditTransactionDTO> {

    @EJB
    private AuditLogDAO auditLogDAO;

    @Override
    protected void initialize() throws MtsException {

//        // Return all
//        this.registerDML(FindAll.class, new QueryCallback(getDtoTableName()) {
//            @Override
//            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
//                return getSelectDML();
//            }
//        }, false);
        this.registerDML(ByGeneralProperties.class, new QueryCallback(getDtoTableName()) {
            @Override
            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                final String METHODNAME = "getQueryDML ";
                String queryDML = null;
                setDetectWildcardPredicateValue(baseDTO, " lower(transaction_id) = :transaction_id ", "transactionId");
                setDetectWildcardPredicateValue(baseDTO, " lower(audit_id) = :audit_id ", "auditId");
                setDetectWildcardPredicateValue(baseDTO, " lower(transaction_type) = :transaction_type ", "transactionType");
                setDetectWildcardPredicateValue(baseDTO, " lower(class_name) = :class_name ", "className");
                setDetectWildcardPredicateValue(baseDTO, " lower(app_name) = :app_name ", "appName");
                setDetectWildcardPredicateValue(baseDTO, " lower(create_id) = :create_id ", "createId");
                Object oStartCreateDatetime = baseDTO.getQueryMap().get("startCreateDatetime");
                Object oStopCreateDatetime = baseDTO.getQueryMap().get("stopCreateDatetime");

                if (oStartCreateDatetime != null) {
                    baseDTO.getQueryMap().put("startCreateDatetime", DateUtils.getFormattedDate((Date) oStartCreateDatetime, DateUtils.DATEINMASK));
                    String operator = "=";
                    if (oStopCreateDatetime != null) {
                        operator = ">=";
                    }
                    setNonWildcardPredicateValue(baseDTO, " cast(create_datetime as date) " + operator + " to_date(:start_create_datetime, '" + DateUtils.DATEINMASK + "') ", "startCreateDatetime");
                }
                if (oStopCreateDatetime != null) {
                    baseDTO.getQueryMap().put("stopCreateDatetime", DateUtils.getFormattedDate((Date) oStopCreateDatetime, DateUtils.DATEINMASK));
                    String operator = "=";
                    if (oStartCreateDatetime != null) {
                        operator = "<=";
                    }
                    setNonWildcardPredicateValue(baseDTO, " cast(create_datetime as date) " + operator + " to_date(:stop_create_datetime, '" + DateUtils.DATEINMASK + "') ", "stopCreateDatetime");
                }

                queryDML = getSelectDML() + getAndClearPredicateMap("where", "", Operator.AND);

                logger.debug(METHODNAME, "queryDML=", queryDML);
                return queryDML;
            }

            @Override
            protected void getCallbackNamedParameters(MapSqlParameterSource namedParameters, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws MtsException {
                setLowerQueryMapValue(baseDTO, "transaction_id", "transactionId", namedParameters);
                setLowerQueryMapValue(baseDTO, "audit_id", "auditId", namedParameters);
                setLowerQueryMapValue(baseDTO, "transaction_Type", "transactionType", namedParameters);
                setLowerQueryMapValue(baseDTO, "app_name", "appName", namedParameters);
                setLowerQueryMapValue(baseDTO, "class_name", "className", namedParameters);
                setLowerQueryMapValue(baseDTO, "create_id", "createId", namedParameters);
                setExactQueryMapValue(baseDTO, "start_create_datetime", "startCreateDatetime", namedParameters);
                setExactQueryMapValue(baseDTO, "stop_create_datetime", "stopCreateDatetime", namedParameters);
            }

        }, false);

//        // Register Table Mapper
//        registerTableMapper(getDtoTableName(), new BaseRowMapper<AuditTransactionDTO>() {
//
//            @Override
//            public MapSqlParameterSource getNamedParameters(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
//                MapSqlParameterSource namedParameters = new MapSqlParameterSource();
//                if (baseDTO == null) {
//                    logger.warn("Null baseDTO submitted. Returning empty MapSqlParameterSource.");
//                    return namedParameters;
//                }
//                if (queryClass == ByGeneralProperties.class) {
//                    setExactQueryMapValue(baseDTO, "transaction_id", "transactionId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "audit_id", "auditId", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "transaction_Type", "transactionType", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "app_name", "appName", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "class_name", "className", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "create_id", "createId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "start_create_datetime", "startCreateDatetime", namedParameters);
//                    setExactQueryMapValue(baseDTO, "stop_create_datetime", "stopCreateDatetime", namedParameters);
//                } else {
//                    AuditTransactionDTO parameter = (AuditTransactionDTO) baseDTO;
//                    namedParameters.addValue("transaction_id", parameter.getTransactionId());
//                    namedParameters.addValue("audit_id", parameter.getAuditId());
//                    namedParameters.addValue("transaction_type", parameter.getTransactionType() != null ? parameter.getTransactionType().toString() : null);
//                    namedParameters.addValue("class_name", parameter.getClassName());
//                    namedParameters.addValue("app_name", parameter.getAppName());
//                    addStdCreateModParameters(namedParameters, parameter);
//                }
//                return namedParameters;
//            }
//
//            @Override
//            public AuditTransactionDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
//                AuditTransactionDTO row = new AuditTransactionDTO();
//                row.setAuditId(rs.getString("audit_id"));
//                row.setTransactionId(rs.getString("transaction_id"));
//                row.setTransactionType(rs.getString("transaction_type") != null ? AuditTransaction.valueOf(rs.getString("transaction_type")) : null);
//                row.setClassName(rs.getString("class_name"));
//                row.setAppName(rs.getString("app_name"));
//                mapStdCreateModProperties(rs, row);
//                return row;
//            }
//        });
    }

    public void audit(BaseDTO baseDTO, Operation operation, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, MtsException, NotFoundException {
        final String METHODNAME = "audit ";
        logger.debug(METHODNAME, "dtoClass=", getDtoClass(), " operation=", operation);
        long startTime = System.nanoTime();
        try {
            //
            // Any DTO that is annotated with the Audit annotation will get audit and will flow through this method
            // The DTO MUST have a auditId and MUST have an PropertyName Enum defined and must configure the PropertyChanged Listener 
            // on every property that will be audited.
            // For Audit control of adds/updates/deletes see the Audit annotation
            //
            // TO DO: Handle case where the DTO should be audited only if its Parent is some class
            // Possibly add a flag in the Parent Child RelationShip annotation
            //
            // TO DO: Foreign keys that are set via childDTO.setForeignKey(parentDTO.getClass(), parentPrimaryKey) are not triggering the propertyEventListener
            //        Fix to call propertyUtils or work around, on insert get the ForeignKey and add it to the audit log
            // TO DO: Same problem with Primary Keys
            //
            // Note: If you are using rs.getLong or getInt and the data in the database can be null, you will be storing a Zero 0 in the DTO which will 
            //       trigger a change so if you see data in the audit_log that doesnt make sense you should check the mapper.
            // Change to      Object oObject = rs.getObject("long_field_name");
            //                if (oObject != null) {
            //                    providerDTO.setLongFieldName(((BigDecimal) oObject).longValue());
            //                }
            // Allows calls to skip auditing
            boolean auditBypass = ObjectUtils.objectToBoolean(propertyBagDTO.get(CoreConstants.AUDIT_BYPASS));
            logger.debug(METHODNAME + "propertyBagDTO.isAuditTransactionIdExist()=", propertyBagDTO.isAuditTransactionIdExist(), " auditBypass=", auditBypass);
            if (auditBypass) {
                return;
            }
            
            if (!propertyBagDTO.isAuditTransactionIdExist()) {

                DTOState operationDTOState = baseDTO.getOperationDTOState();
                String className = baseDTO.getClass().getCanonicalName();
                String auditId = baseDTO.getAuditId();
                BaseDTO parentDTO = propertyBagDTO.getParentDTO(PropertyBagDTO.Position.First);
                logger.debug(METHODNAME, "baseDTO.className=", className, " baseDTO.auditId=", auditId, " baseDTO.operationDTOState=", operationDTOState);
                if (parentDTO != null) {
                    className = parentDTO.getClass().getCanonicalName();
                    auditId = parentDTO.getAuditId();
                    operationDTOState = parentDTO.getOperationDTOState();
                    logger.debug(METHODNAME, "parentDTO.className=", className, " parentDTO.auditId=", auditId, " parentDTO.operationDTOState=", operationDTOState);
                }

                // Under cirtain race conditions the DTO maybe UNSET, in this case do not audit
                if (operationDTOState != DTOState.UNSET) {
                    String auditTransactionId = propertyBagDTO.getAuditTransactionId();
                    AuditTransactionDTO auditTransactionDTO = new AuditTransactionDTO();
                    auditTransactionDTO.setTransactionId(auditTransactionId);
                    auditTransactionDTO.setClassName(className);
                    auditTransactionDTO.setAuditId(auditId);
                    auditTransactionDTO.setAppName(sessionDTO.getAppDTO().getAppName());
                    auditTransactionDTO.setCreateId(sessionDTO.getUserDTO().getUsername());
                    if (operationDTOState == DTOState.NEW || operationDTOState == DTOState.NEWMODIFIED) {
                        auditTransactionDTO.setTransactionType(AuditTransaction.INSERT);
                    } else if (operationDTOState == DTOState.UPDATED) {
                        auditTransactionDTO.setTransactionType(AuditTransaction.UPDATE);
                    } else if (operationDTOState == DTOState.DELETED) {
                        auditTransactionDTO.setTransactionType(AuditTransaction.DELETE);
                    } else if (operationDTOState == DTOState.UNSET) {
                        logger.error(METHODNAME, "baseDTO.getDTOStates()", baseDTO.getDTOStates(), " className=", className, " auditId=", auditId, " operationDTOState=", operationDTOState);
                        // Code that follows will get an error since TransactionType is NULL
                    }
                    auditTransactionDTO.setCreateDatetime(new Date());
                    add(auditTransactionDTO, Add.class, sessionDTO, propertyBagDTO);
                }
            }
            logger.debug(METHODNAME, "propertyBagDTO.getAuditTransactionId()=", propertyBagDTO.getAuditTransactionId());

            // Call AuditLog
            auditLogDAO.auditLog(baseDTO, operation, queryClass, sessionDTO, propertyBagDTO);
        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, startTime);
        }
    }
}
