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

import java.util.HashMap;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.ValidationException;
import org.cdsframework.util.BrokenRule;
import org.cdsframework.util.LogUtils;

/**
 * Provides a mechanism to access global variables retrieved from the ejb-jar.xml file. Also, initializes Log4J
 *
 * @author HLN Consulting, LLC
 */
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public abstract class BasePropertyMGR extends HashMap<String, Object> {

    private final static LogUtils logger = LogUtils.getLogger(BasePropertyMGR.class);

    /**
     * Converts and registers a supplied string property in the property managers map
     *
     * @param propertyName the property name in the property map
     * @param propertyValue the property value
     * @param type the type to cast the value to
     * @param obscureLogValue in case you don't want the value to show up in the log (i.e. passwords).
     * @throws MtsException if an unsupported type is passed in
     * @throws org.cdsframework.exceptions.ValidationException
     */
    @Lock(LockType.READ)
    public void registerPropertyWithConversion(String propertyName, String propertyValue, Class type, boolean obscureLogValue)
            throws MtsException, ValidationException {
        final String METHODNAME = "registerPropertyWithConversion ";
        if (type == String.class) {
            registerProperty(propertyName, propertyValue, obscureLogValue);
        } else if (type == Integer.class) {
            try {
                registerProperty(propertyName, Integer.valueOf(propertyValue), obscureLogValue);
            } catch (NumberFormatException e) {
                throw new ValidationException(new BrokenRule("integerConversionError", "The value supplied was not a valid integer", "value"));
            }
        } else if (type == Long.class) {
            try {
                registerProperty(propertyName, Long.valueOf(propertyValue), obscureLogValue);
            } catch (NumberFormatException e) {
                throw new ValidationException(new BrokenRule("longConversionError", "The value supplied was not a valid long", "value"));
            }
        } else if (type == Boolean.class) {
            if (propertyValue == null || (!"true".equals(propertyValue) && !"false".equals(propertyValue))) {
                throw new ValidationException(new BrokenRule("booleanConversionError", "The value supplied was not a valid boolean", "value"));
            }
            registerProperty(propertyName, Boolean.valueOf(propertyValue), obscureLogValue);
        } else if (type == Float.class) {
            try {
                registerProperty(propertyName, Float.valueOf(propertyValue), obscureLogValue);
            } catch (NumberFormatException e) {
                throw new ValidationException(new BrokenRule("floatConversionError", "The value supplied was not a valid float", "value"));
            }
        } else {
            throw new MtsException(METHODNAME + "type not supported: " + type.getCanonicalName());
        }
    }

    /**
     * Registers a property in the property managers map
     *
     * @param propertyName the property name in the property map
     * @param propertyValue the property value
     */
    @Lock(LockType.READ)
    public void registerProperty(String propertyName, Object propertyValue) {
        registerProperty(propertyName, propertyValue, false);
    }

    /**
     * Registers a property in the property managers map
     *
     * @param propertyName the property name in the property map
     * @param propertyValue the property value
     * @param obscureLogValue in case you don't want the value to show up in the log (i.e. passwords).
     */
    @Lock(LockType.WRITE)
    public void registerProperty(String propertyName, Object propertyValue, boolean obscureLogValue) {
        this.put(propertyName, propertyValue);
        logger.debug("Set " + propertyName + " to: ", obscureLogValue ? "********" : this.get(propertyName));
    }

    @Lock(LockType.READ)
    public <S> S get(String key, Class<S> type) {
        final String METHODNAME = "get ";
        S result;
        Object value = get(key);
        if (type.isEnum() && value != null && value instanceof String) {
            try {
                result = (S) Enum.valueOf((Class<? extends Enum>) type, (String) value);
            } catch (IllegalArgumentException e) {
                logger.error(METHODNAME, "There was an issue deriving the enum value from the string: ", value, " - ", type);
                logger.error(e);
                result = null;
            }
        } else {
            result = (S) value;
        }
        return result;
    }
}
