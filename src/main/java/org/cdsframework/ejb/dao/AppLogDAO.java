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
import javax.ejb.Stateless;
import org.cdsframework.base.BaseDAO;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.callback.QueryCallback;
import org.cdsframework.dto.AppLogDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.enumeration.Operator;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.group.ByGeneralProperties;
import org.cdsframework.util.DateUtils;
import org.cdsframework.util.ObjectUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 *
 * @author HLN Consulting LLC
 */
@Stateless
public class AppLogDAO extends BaseDAO<AppLogDTO> {

    @Override
    protected void initialize() throws MtsException {
//
//        this.registerDML(FindAll.class, new QueryCallback(getDtoTableName()) {
//            @Override
//            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
//                return getSelectDML();
//            }
//        }, false);

        registerDML(AppLogDTO.ByPruner.class, new QueryCallback(getDtoTableName()) {
            @Override
            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                final String METHODNAME = "getQueryDML ";
                String sql = getSelectDML() ;
                setNonWildcardPredicateValue(baseDTO, " create_datetime <= (CURRENT_DATE - :sysdate_age) ", "sysdateAge");
                sql += " " + getAndClearPredicateMap(" WHERE ", "", Operator.AND) + " order by create_datetime";
                logger.debug(METHODNAME, "sql=", sql);
                return sql;
            }
            
            @Override
            protected void getCallbackNamedParameters(MapSqlParameterSource namedParameters, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws MtsException {
                final String METHODNAME = "getCallbackNamedParameters ";
                Integer sysdateAge = ObjectUtils.objectToInteger(baseDTO.getQueryMap().get("sysdateAge"));
                if (sysdateAge != null) {
                    namedParameters.addValue("sysdate_age", sysdateAge);
                }
                else {
                    throw new MtsException(METHODNAME + "was called, sysdate_age is required");
                }
            }
            
        }, false);
        
        
        registerDML(ByGeneralProperties.class, new QueryCallback(getDtoTableName()) {
            @Override
            public String getQueryDML(BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
                final String METHODNAME = "getQueryDML ";
                logger.logBegin(METHODNAME);
                String queryDML = null;
                try {
                    setDetectWildcardPredicateValue(baseDTO, " app_log_id = :app_log_id ", "appLogId");
                    setDetectWildcardPredicateValue(baseDTO, " lower(session_id) = :session_id ", "sessionId");
                    setDetectWildcardPredicateValue(baseDTO, " lower(log_level) = :log_level ", "logLevel");
                    setDetectWildcardPredicateValue(baseDTO, " lower(other_info) = :other_info ", "otherInfo");
                    setDetectWildcardPredicateValue(baseDTO, " lower(app_name) = :app_name ", "appName");
                    setDetectWildcardPredicateValue(baseDTO, " lower(stack_trace) = :stack_trace ", "stackTrace");
                    setDetectWildcardPredicateValue(baseDTO, " lower(message) = :message ", "message");
                    setDetectWildcardPredicateValue(baseDTO, " lower(object_name) = :object_name ", "objectName");
                    setDetectWildcardPredicateValue(baseDTO, " lower(create_id) = :create_id ", "createId");
                    Object oStartCreateDatetime = baseDTO.getQueryMap().get("startCreateDatetime");
                    Object oStopCreateDatetime = baseDTO.getQueryMap().get("stopCreateDatetime");

                    if (oStartCreateDatetime != null) {
                        baseDTO.getQueryMap().put("startCreateDatetime", DateUtils.getFormattedDate((Date) oStartCreateDatetime, DateUtils.DATEINMASK));
                        String operator = "=";
                        if (oStopCreateDatetime != null) {
                            operator = ">=";
                        }
                        setNonWildcardPredicateValue(baseDTO, " date(create_datetime) " + operator + " date(:start_create_datetime) ", "startCreateDatetime");

                    }
                    if (oStopCreateDatetime != null) {
                        baseDTO.getQueryMap().put("stopCreateDatetime", DateUtils.getFormattedDate((Date) oStopCreateDatetime, DateUtils.DATEINMASK));
                        String operator = "=";
                        if (oStartCreateDatetime != null) {
                            operator = "<=";
                        }
                        setNonWildcardPredicateValue(baseDTO, " date(create_datetime) " + operator + " date(:stop_create_datetime) ", "stopCreateDatetime");
                    }

                    queryDML = getSelectDML() + getAndClearPredicateMap("where", "", Operator.AND);
                } finally {
                    logger.debug(METHODNAME + "queryDML=" + queryDML);
                    logger.logEnd(METHODNAME);
                }
                return queryDML;
            }

            @Override
            protected void getCallbackNamedParameters(MapSqlParameterSource namedParameters, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) throws MtsException {
                setLowerQueryMapValue(baseDTO, "app_log_id", "appLogId", namedParameters);
                setLowerQueryMapValue(baseDTO, "session_id", "sessionId", namedParameters);
                setLowerQueryMapValue(baseDTO, "log_level", "logLevel", namedParameters);
                setLowerQueryMapValue(baseDTO, "other_info", "otherInfo", namedParameters);
                setLowerQueryMapValue(baseDTO, "app_name", "appName", namedParameters);
                setLowerQueryMapValue(baseDTO, "message", "message", namedParameters);
                setLowerQueryMapValue(baseDTO, "stack_trace", "stackTrace", namedParameters);
                setLowerQueryMapValue(baseDTO, "object_name", "objectName", namedParameters);
                setLowerQueryMapValue(baseDTO, "create_id", "createId", namedParameters);
                setExactQueryMapValue(baseDTO, "start_create_datetime", "startCreateDatetime", namedParameters);
                setExactQueryMapValue(baseDTO, "stop_create_datetime", "stopCreateDatetime", namedParameters);
            }

        }, false);
//
//        registerTableMapper(getDtoTableName(), new BaseRowMapper<AppLogDTO>() {
//
//            @Override
//            public MapSqlParameterSource getNamedParameters(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
//                final String METHODNAME = "getNamedParameters ";
//
//                MapSqlParameterSource namedParameters = new MapSqlParameterSource();
//                if (baseDTO == null) {
//                    logger.warn("Null baseDTO submitted. Returning empty MapSqlParameterSource.");
//                    return namedParameters;
//                }
//                AppLogDTO parameter = (AppLogDTO) baseDTO;
//                if (queryClass == ByGeneralProperties.class) {
//                    setExactQueryMapValue(baseDTO, "app_log_id", "appLogId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "session_id", "sessionId", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "log_level", "logLevel", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "other_info", "otherInfo", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "app_name", "app_name", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "message", "message", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "stack_trace", "stackTrace", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "object_name", "objectName", namedParameters);
//                    setLowerQueryMapValue(baseDTO, "create_id", "createId", namedParameters);
//                    setExactQueryMapValue(baseDTO, "start_create_datetime", "startCreateDatetime", namedParameters);
//                    setExactQueryMapValue(baseDTO, "stop_create_datetime", "stopCreateDatetime", namedParameters);
//                } else {
//                    namedParameters.addValue("app_log_id", parameter.getAppLogId());
//                    namedParameters.addValue("session_id", parameter.getSessionId());
//                    namedParameters.addValue("log_level", parameter.getLogLevel() != null ? parameter.getLogLevel().toString() : null);
//                    namedParameters.addValue("other_info", parameter.getOtherInfo());
//                    namedParameters.addValue("app_name", parameter.getAppName());
//                    namedParameters.addValue("message", parameter.getMessage());
//                    namedParameters.addValue("stack_trace", parameter.getStackTrace());
//                    namedParameters.addValue("object_name", parameter.getObjectName());
//                    namedParameters.addValue("object_data", parameter.getObjectData());
//                    addStdCreateModParameters(namedParameters, parameter);
//                }
//                return namedParameters;
//            }
//
//            @Override
//            public AppLogDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
//                final String METHODNAME = "mapRow ";
//                AppLogDTO row = new AppLogDTO();
//                row.setAppLogId(rs.getString("app_log_id"));
//                row.setSessionId(rs.getString("session_id"));
//                row.setAppName(rs.getString("app_name"));
//                row.setOtherInfo(rs.getString("other_info"));
//                row.setLogLevel(rs.getString("log_level") != null ? LogLevel.valueOf(rs.getString("log_level")) : null);
//                row.setMessage(rs.getString("message"));
//                row.setStackTrace(rs.getString("stack_trace"));
//                row.setObjectName(rs.getString("object_name"));
//                try {
//                    row.setObjectData(rs.getBytes("object_data"));
//                } catch (SQLException e) {
//                    String message = e.getMessage();
//                    if (!message.contains("There is no column named: object_data")) {
//                        throw e;
//                    }
//                }
//                mapStdCreateModProperties(rs, row);
//                return row;
//            }
//        });
    }

}
