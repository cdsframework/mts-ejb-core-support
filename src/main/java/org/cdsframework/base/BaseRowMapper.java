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

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import org.cdsframework.annotation.Column;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.enumeration.FieldType;
import org.cdsframework.enumeration.DatabaseType;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.group.DateTime;
import org.cdsframework.group.None;
import org.cdsframework.util.ClassUtils;
import org.cdsframework.util.DTOProperty;
import org.cdsframework.util.DTOTable;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.DateUtils;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.support.CorePropertyChangeEvent;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 *
 * @author HLN Consulting, LLC
 * @param <T>
 */
public abstract class BaseRowMapper<T extends BaseDTO> implements RowMapper<T> {

    private final static LogUtils logger = LogUtils.getLogger(BaseRowMapper.class);
    private DTOTable dtoTable;
    private final Class<T> dtoClass;

    public BaseRowMapper() {
        dtoClass = ClassUtils.getTypeArgument(BaseRowMapper.class, getClass());
        setDtoTable();
    }

    public BaseRowMapper(Class<T> dtoClass) {
        this.dtoClass = dtoClass;
        setDtoTable();
    }

    @Override
    public abstract T mapRow(ResultSet rs, int rowNum) throws SQLException;

    public MapSqlParameterSource getNamedParametersMain(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO) throws MtsException {
        return getNamedParameters(baseDTO, queryClass, sessionDTO, propertyBagDTO);
    }

    protected abstract MapSqlParameterSource getNamedParameters(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO,
            PropertyBagDTO propertyBagDTO) throws MtsException;

    protected void addStdCreateModParameters(MapSqlParameterSource namedParameters, T dto) {
        final String METHODNAME = "addStdCreateModParameters ";

        // Note create_id, create_datetime are default columns and create_datetime is used as a default sort, 
        // new tables should have create_id, and create_datetime.
        namedParameters.addValue("created_id", dto.getCreateId());
        namedParameters.addValue("create_id", dto.getCreateId());
        namedParameters.addValue("created_datetime", dto.getCreateDatetime());
        namedParameters.addValue("create_datetime", dto.getCreateDatetime());
        namedParameters.addValue("last_mod_id", dto.getLastModId());
        namedParameters.addValue("last_mod_datetime", dto.getLastModDatetime());

        logger.debug(METHODNAME, "dto.isAudit(): ", dto.isAudit());
        if (dto.isAudit()) {
            namedParameters.addValue("audit_id", dto.getAuditId());
        }
        logger.debug(METHODNAME, "dto.getAuditId(): ", dto.getAuditId());
    }

    /**
     * Add all the standard property parameters to the supplied namedParameters object.
     *
     * @param databaseType
     * @param namedParameters
     * @param dto
     */
    protected void addStdParameters(DatabaseType databaseType, MapSqlParameterSource namedParameters, BaseDTO dto) {
        addStdParameters(null, databaseType, namedParameters, dto);
    }

    /**
     * Add all the standard property parameters to the supplied namedParameters object.
     *
     * @param queryClass
     * @param databaseType
     * @param namedParameters
     * @param dto
     */
    protected void addStdParameters(Class queryClass, DatabaseType databaseType, MapSqlParameterSource namedParameters, BaseDTO dto) {
        final String METHODNAME = "addStdParameters ";
//        long startTime = System.nanoTime();
        if (dtoTable == null) {
            throw new IllegalStateException(METHODNAME + "called but @Entity not present! " + dtoClass.getCanonicalName());
        }

        logger.debug(METHODNAME, "dtoClass: ", dtoClass.getCanonicalName());

        Collection<DTOProperty> dtoProperties;
        // if the queryClass is not null and it is in the parent foreign key map then use the parent foreign key map properties
        if (queryClass != null && dtoTable.getParentForeignKeyMap().containsKey(queryClass)) {
            logger.debug(METHODNAME, "queryClass: ", queryClass.getCanonicalName());
            dtoProperties = dtoTable.getParentForeignKeyMap().get(queryClass);
        } else {
            // otherwise - use all properties
            dtoProperties = dtoTable.getDtoPropertyMap().values();
        }

        // iterate over the properties and add them and their values to the namedParameters object
        for (DTOProperty dtoProperty : dtoProperties) {
            Field field;

            // if there is a parent field and the parent field class equals the incoming DTO then use that field value
            if (dtoProperty.getParentField() != null
                    &&  dtoProperty.getParentField().getDeclaringClass().isAssignableFrom(dto.getClass())) {
                field = dtoProperty.getParentField();
            } else {
                // otherwise use the property's field value
                field = dtoProperty.getField();
            }
            logger.debug(METHODNAME, "field class: ", field.getDeclaringClass().getCanonicalName(), "; DTO class: ", dto.getClass().getCanonicalName());
            logger.debug(METHODNAME, "field name: ", field.getName());

            Column[] columns = dtoProperty.getColumns();
            // add the value to each of the configured column names
            for (Column column : columns) {
                logger.debug(METHODNAME, "column: ", column.name());
                if (column.insertable() || column.updateable()) {
                    Object value;
                    try {
                        String columnName = column.name();
                        value = field.get(dto);
                        logger.debug(METHODNAME, "value: ", value);
                        if (value != null) {
                            // Convert it
                            value = dtoProperty.getDataValue(column, databaseType, value);
                        }
//                        logger.debug(METHODNAME, "columnName=", columnName, " dtoProperty.getFieldType()=", dtoProperty.getFieldType(), " value=", value);
                        namedParameters.addValue(columnName, value);
                    } catch (Exception e) {
                        throw new IllegalStateException("An Exception occurred convertDTOValueToDatabaseValue on " + field.getName() + "; Message: " + e.getMessage(), e);
                    }

                    // Add the original parameter for the where clause
                    if (column.addToWhereUpdate() || column.addToWhereDelete()) {
//                        logger.debug(METHODNAME, "column.name()=", column.name(), 
//                                " dtoProperty.getFieldType()=", dtoProperty.getFieldType(), 
//                                " field.getName()=", field.getName(), " value=", value);
                        if (dto.isPropertyChanged(field.getName())) {
                            logger.debug(METHODNAME, " field.getName()=", field.getName(), " isPropertyChanged=true");
                            CorePropertyChangeEvent propertyChangeEvent = dto.getPropertyChangeEvent(field.getName());
                            value = propertyChangeEvent.getOldValue();
                        }
//                        logger.debug(METHODNAME, " value=", value, 
//                                " ORIGINAL COLUMN=", DTOTable.ORIGINAL_PREFIX + column.name());
                        namedParameters.addValue(DTOTable.ORIGINAL_PREFIX + column.name(), value);

                    }
                }
            }
        }
//        logger.logDuration(LogLevel.DEBUG, METHODNAME, startTime);                                                
    }

    protected void mapStdCreateModProperties(ResultSet rs, T dto) throws SQLException {
        final String METHODNAME = "mapStdCreateModProperties ";
        try {
            dto.setCreateId(rs.getString("created_id"));
        } catch (SQLException e) {
            try {
                dto.setCreateId(rs.getString("create_id"));
            } catch (SQLException s) {
                logger.debug(METHODNAME, dto.getClass().getSimpleName(), ": mapStdCreateModProperties SQLException on setting created_id: ", s.getMessage());
            }
        }
        try {
            dto.setCreateDatetime(rs.getTimestamp("created_datetime"));
        } catch (SQLException e) {
            try {
                dto.setCreateDatetime(rs.getTimestamp("create_datetime"));
            } catch (SQLException s) {
                logger.debug(METHODNAME, dto.getClass().getSimpleName(), ": mapStdCreateModProperties SQLException on setting created_datetime: ", s.getMessage());
            }
        }
        try {
            dto.setLastModId(rs.getString("last_mod_id"));
        } catch (SQLException e) {
            logger.debug(METHODNAME, dto.getClass().getSimpleName(), ": mapStdCreateModProperties SQLException on setting last_mod_id: ", e.getMessage());
        }
        try {
            dto.setLastModDatetime(rs.getTimestamp("last_mod_datetime"));
        } catch (SQLException e) {
            logger.debug(METHODNAME, dto.getClass().getSimpleName(), ": mapStdCreateModProperties SQLException on setting last_mod_datetime: ", e.getMessage());
        }
        logger.debug(METHODNAME, "dto.isAudit(): ", dto.isAudit());
        if (dto.isAudit()) {
            try {
                dto.setAuditId(rs.getString("audit_id"));
            } catch (SQLException e) {
                logger.debug(dto.getClass().getSimpleName(), ": mapStdCreateModProperties SQLException on setting audit_id: ", e.getMessage());
            }
        }
        logger.debug(METHODNAME, "dto.getAuditId(): ", dto.getAuditId());
    }

    protected void mapStdProperties(DatabaseType databaseType, ResultSet rs, T dto) throws SQLException {
        final String METHODNAME = "mapStdProperties ";
//        long startTime = System.nanoTime();
        if (dtoTable == null) {
            throw new IllegalStateException(METHODNAME + "called but @Entity not present! " + dtoClass.getCanonicalName());
        }

        Map<Field, DTOProperty> dtoPropertyMap = dtoTable.getDtoPropertyMap();
        for (Map.Entry<Field, DTOProperty> dtoPropertyEntry : dtoPropertyMap.entrySet()) {
            Field field = dtoPropertyEntry.getKey();
            DTOProperty dtoProperty = dtoPropertyEntry.getValue();
            Column[] columns = dtoProperty.getColumns();
            for (Column column : columns) {
//                if (column.insertable() || column.updateable()) {
                if (column.selectable()) {
                    try {
                        String columnName = column.name();
//                        logger.debug(METHODNAME, "columnName=", columnName);
                        Object value = null;
                        if (dtoProperty.getFieldType() != FieldType.Date) {
                            value = rs.getObject(columnName);

                        // Handle timestamp
                        } else if (column.resultSetClass() == None.class) {
                            // All dates use timestamp by default, unless annotated (see below)
                            value = rs.getTimestamp(columnName);
                            
                        // Handle date
                        } else if (column.resultSetClass() == Date.class) {
                            value = DateUtils.getTruncatedDate(rs.getDate(columnName));
                        }
                        
                        // Handle datetime
                        else if (column.resultSetClass() == DateTime.class) {
                            // Strips the milliseconds
                            value = DateUtils.getTruncatedDateTime(rs.getTimestamp(columnName));
                        }
                        dtoProperty.setDataValue(column, value, databaseType, dto);
                    } catch (Exception e) {
                        throw new IllegalStateException("An Exception occurred on column " + column.name() + " field " + field.getName() + "; Message: " + e.getMessage(), e);
                    }
                }
            }
        }
//        logger.logDuration(LogLevel.DEBUG, METHODNAME, startTime);                                                        
    }

    /**
     * Future?
     *
     * @param databaseType
     * @param dao
     * @param dto
     * @param wildCardOption
     * @return
     */
    private String getGeneralPropertiesSQL(DatabaseType databaseType, BaseDAO dao, T dto, BaseDAO.WildCardOption wildCardOption) {
        final String METHODNAME = "getGeneralPropertiesSQL ";
        if (dtoTable == null) {
            throw new IllegalStateException(METHODNAME + "called but @Entity not present! " + dtoClass.getCanonicalName());
        }

        String generalPropertiesSQL = null;
        Map<Field, DTOProperty> dtoPropertyMap = dtoTable.getDtoPropertyMap();
        for (Map.Entry<Field, DTOProperty> dtoPropertyEntry : dtoPropertyMap.entrySet()) {
            DTOProperty dtoProperty = dtoPropertyEntry.getValue();
            Column[] columns = dtoProperty.getColumns();
            for (Column column : columns) {
                String columnName = column.name();
                FieldType fieldType = dtoProperty.getFieldType();
                switch (fieldType) {
                    case String:
                        dao.setPredicateValue(dto, " lower(" + columnName + ") = :" + columnName, dtoProperty.getField().getName(), wildCardOption, '%');
                        break;
                    case Long:
                    case BigDecimal:
                    case Double:
                    case Float:
                    case Integer:
                        dao.setPredicateValue(dto, " " + columnName + " = :" + columnName, dtoProperty.getField().getName(), wildCardOption, '%');
                        break;

                }
            }
        }
        return generalPropertiesSQL;
    }

    private void setDtoTable() {
        if (DTOUtils.isEntity(dtoClass)) {
            dtoTable = DTOUtils.getDTOTable(dtoClass);
        } else {
            dtoTable = null;
        }
    }

}
