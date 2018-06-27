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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.LocalBean;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.cdsframework.annotation.Permission;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.base.BasePropertyMGR;
import org.cdsframework.security.PermissionObject;
import org.cdsframework.util.ClassUtils;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.LogUtils;

/**
 * Provides a mechanism to access global variables retrieved from the ejb-jar.xml file. Also, initializes Log4J
 *
 * @author HLN Consulting, LLC
 */
@Singleton
@Startup
@LocalBean
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class PropertyMGRLocal extends BasePropertyMGR {
    
    private static final LogUtils logger = LogUtils.getLogger(PropertyMGRLocal.class);
    @Resource(name = "BUILD_NUMBER")
    private String BUILD_NUMBER;
    @Resource(name = "BUILD_TIMESTAMP")
    private String BUILD_TIMESTAMP;
    @Resource(name = "BUILD_VERSION")
    private String BUILD_VERSION;
    
    @Resource(name = "APP_LOG_HOUR_INTERVAL")
    private String APP_LOG_HOUR_INTERVAL;
    @Resource(name = "APP_LOG_MINUTE_INTERVAL")
    private String APP_LOG_MINUTE_INTERVAL;
    @Resource(name = "APP_LOG_SECOND_INTERVAL")
    private String APP_LOG_SECOND_INTERVAL;
    @Resource(name = "LOG_EXCEPTIONS")
    private Boolean LOG_EXCEPTIONS;

    
    @PostConstruct
    private void postConstructor() {
        final String METHODNAME = "postConstructor ";
        if (logger.isDebugEnabled()) {
            logger.debug(METHODNAME + "Begin ");
        }
        this.put("BUILD_NUMBER", BUILD_NUMBER);
        this.put("BUILD_TIMESTAMP", BUILD_TIMESTAMP);
        this.put("BUILD_VERSION", BUILD_VERSION);
        this.put("APP_LOG_HOUR_INTERVAL", APP_LOG_HOUR_INTERVAL);
        this.put("APP_LOG_MINUTE_INTERVAL", APP_LOG_MINUTE_INTERVAL);
        this.put("APP_LOG_SECOND_INTERVAL", APP_LOG_SECOND_INTERVAL);
        this.put("LOG_EXCEPTIONS", LOG_EXCEPTIONS);

        this.put("APPLICATION_PROXY_REGISTRATIONS", new HashMap<String, Class>()); 
        logger.info("Starting MTS EJB Core Support Services...");
        logger.info("Build Number: " + BUILD_NUMBER);
        logger.info("Build Timestamp: " + BUILD_TIMESTAMP);
        logger.info("Build Version: " + BUILD_VERSION);
        logger.info("APP_LOG_HOUR_INTERVAL: " + APP_LOG_HOUR_INTERVAL);
        logger.info("APP_LOG_MINUTE_INTERVAL: " + APP_LOG_MINUTE_INTERVAL);
        logger.info("APP_LOG_SECOND_INTERVAL: " + APP_LOG_SECOND_INTERVAL);
        logger.info("LOG_EXCEPTIONS: " + LOG_EXCEPTIONS);

        initializePermissions();
    }
    
    private void initializePermissions() {
        try {
            put("PERMISSION_MAP", new HashMap<String, PermissionObject>());
            addPermissionObject(BaseDTO.class);
            Class[] classes = ClassUtils.getClassesFromClasspath("org.cdsframework.dto");
            for (Class clazz : classes) {
                if (BaseDTO.class.isAssignableFrom(clazz)) {
                    addPermissionObject(clazz);
                } else {
                    logger.warn("Found a class in the org.cdsframework.dto package that does not extend BaseDTO: ", clazz.getSimpleName());
                }
            }
        } catch (Exception e) {
            logger.error(e);
        }
    }
    
    private <S extends BaseDTO> PermissionObject addPermissionObject(Class<S> dtoClass) {
        final String METHODNAME = "addPermissionObject ";
        PermissionObject result = null;
        try {
            Permission permission = DTOUtils.getPermission(dtoClass);
            if (permission != null) {
                Map<String, PermissionObject> permissionMap = (Map<String, PermissionObject>) get("PERMISSION_MAP");
                permissionMap.put(dtoClass.getCanonicalName(), new PermissionObject(permission.name(), dtoClass));
                logger.debug("Added PermissionObject for ", permission.name(), " (", dtoClass.getCanonicalName(), ")");
            } else {
                logger.error(METHODNAME, dtoClass.getCanonicalName(), " is not annotated with @Permission!");
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return result;
    }
    
    @Lock(LockType.READ)
    public Map<String, PermissionObject> getPermissionMap() {
        return (Map<String, PermissionObject>) get("PERMISSION_MAP");
    }
    
    /*
    @Lock(LockType.READ)
    public boolean isMciConnected() {
        boolean mciConnectedState = false;
        Object connected = this.get("MCI_CONNECTED_STATE");
        if (connected != null) {
            mciConnectedState = ((Boolean) connected).booleanValue();
        }
        return mciConnectedState;
        
    }
    
    @Lock(LockType.WRITE)
    public void setMciConnected(boolean mciConnectedState) {
        this.put("MCI_CONNECTED_STATE", mciConnectedState);
    }
    */
    
}
