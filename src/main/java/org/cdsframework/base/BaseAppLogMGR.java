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

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.cdsframework.client.support.AppLogInterface;
import org.cdsframework.dto.AppLogDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.util.LogUtils;
import org.cdsframework.util.ObjectUtils;
import org.cdsframework.util.StringUtils;

/**
 *
 * @author HLN Consulting LLC
 */
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public abstract class BaseAppLogMGR implements AppLogInterface {

    private final static LogUtils logger = LogUtils.getLogger(BaseAppLogMGR.class);
    private ConcurrentLinkedQueue<AppLogDTO> appLogQueue = new ConcurrentLinkedQueue<AppLogDTO>();

    @Override    
    @Lock(LockType.READ)    
    public void queueAppInfoLog(String message, SessionDTO sessionDTO) {
        queueAppLog(LogLevel.INFO, message, sessionDTO, null);
    }
    
    @Override    
    @Lock(LockType.READ)    
    public void queueAppErrorLog(Exception exception, SessionDTO sessionDTO) {
        queueAppLog(LogLevel.ERROR, exception, null, sessionDTO, null);
    }
    
    @Override    
    @Lock(LockType.READ)
    public void queueAppLog(LogLevel logLevel, Exception exception, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        queueAppLog(logLevel, exception, baseDTO, null, null, null, sessionDTO, propertyBagDTO);
    }    

    @Lock(LockType.READ)    
    public void queueAppLog(LogLevel logLevel, Exception exception, BaseDTO baseDTO, Class callingClass, String callingMethod, String otherInfo, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        final String METHODNAME = "queueAppLog ";
        String objectName = null;
        byte[] objectBytes = null;
        String message = null;
        String stackTrace = null;
        if (baseDTO != null) {
            objectName = baseDTO.getClass().getCanonicalName();
            try {
                objectBytes = ObjectUtils.serializeObject(baseDTO);
            } catch (IOException ex) {
                logger.error(METHODNAME, "An IOException has occurred; Message: ", ex.getMessage(),ex);
            }
        }
        if (exception != null) {
            stackTrace = StringUtils.getStackTrace(exception);
            message = getExceptionMessage(exception);
            //message = exception.getMessage();
            if (objectName == null) {
                objectName = exception.getClass().getCanonicalName();
            }
        }
        queueAppLog(logLevel, message, stackTrace, objectName, objectBytes, callingClass, callingMethod, otherInfo, sessionDTO, propertyBagDTO);
    }    

    @Override    
    @Lock(LockType.READ)    
    public void queueAppLog(LogLevel logLevel, String message, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        queueAppLog(logLevel, message, null, null, null, null, null, null, sessionDTO, propertyBagDTO);
    }

    @Lock(LockType.READ)    
    public void queueAppLog(LogLevel logLevel, String message, Class callingClass, String callingMethod, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        queueAppLog(logLevel, message, null, null, null, callingClass, callingMethod, null, sessionDTO, propertyBagDTO);
    }
    
    @Lock(LockType.READ)    
    public void queueAppLog(LogLevel logLevel, String message, Class callingClass, String callingMethod, String otherInfo, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        queueAppLog(logLevel, message, null, null, null, callingClass, callingMethod, otherInfo, sessionDTO, propertyBagDTO);
    }
    
    @Override    
    @Lock(LockType.READ)
    public void queueAppLog(LogLevel logLevel, BaseDTO baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        queueAppLog(logLevel, null, baseDTO, sessionDTO, propertyBagDTO);
    }    
    
    @Override    
    @Lock(LockType.READ)
    public void queueAppLog(LogLevel logLevel, Exception exception, String objectName, byte[] objectBytes, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        String message = null;
        String stackTrace = null;
        if (exception != null) {
            stackTrace = StringUtils.getStackTrace(exception);
            message = getExceptionMessage(exception);
        }
        queueAppLog(logLevel, message, stackTrace, objectName, objectBytes, sessionDTO, propertyBagDTO);
    }    

    @Override    
    @Lock(LockType.READ)
    public void queueAppLog(LogLevel logLevel, String message, String stackTrace, String objectName, byte[] objectBytes, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        queueAppLog(logLevel, message, stackTrace, objectName, objectBytes, null, null, null, sessionDTO, propertyBagDTO);
    }

    @Lock(LockType.READ)
    public ConcurrentLinkedQueue<AppLogDTO> getAppLogQueue() {
        return appLogQueue;
    }

    @Lock(LockType.WRITE)
    @Override
    public void queueAppLog(AppLogDTO appLogDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        appLogQueue.add(appLogDTO);        
    }

    @Lock(LockType.READ)
    public void queueAppLog(LogLevel logLevel, String message, String stackTrace, String objectName, byte[] objectBytes, Class callingClass, String callingMethod, String otherInfo, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO) {
        final String METHODNAME = "queueAppLog ";
        AppLogDTO appLogDTO = new AppLogDTO();
        if (callingClass != null) {
            appLogDTO.setCallingClass(callingClass.getCanonicalName());
        }
        appLogDTO.setCallingMethod(callingMethod);

        // If otherInfo is null, look for it in property bag
        if (otherInfo == null && propertyBagDTO != null) {
            Object oOtherInfo = ObjectUtils.objectToString(propertyBagDTO.get("otherInfo"));
            if (oOtherInfo != null) {
                otherInfo = oOtherInfo.toString();
            }
        }
        appLogDTO.setOtherInfo(otherInfo);
        appLogDTO.setLogLevel(logLevel);
        appLogDTO.setMessage(message);
        appLogDTO.setStackTrace(stackTrace);
        appLogDTO.setObjectName(objectName);
        appLogDTO.setObjectData(objectBytes);
        appLogDTO.setCreateDatetime(new Date());
        if (sessionDTO != null) {
            appLogDTO.setSessionId(sessionDTO.getSessionId());
            if (sessionDTO.getUserDTO() != null) {
                appLogDTO.setCreateId(sessionDTO.getUserDTO().getUsername());
            }
            if (sessionDTO.getAppDTO() != null) {
                appLogDTO.setAppName(sessionDTO.getAppDTO().getAppName());
            }
        }        
        appLogQueue.add(appLogDTO);
    
    }
    
    private String getExceptionMessage(Exception exception) {
        String message = ExceptionUtils.getMessage(exception);
        if (StringUtils.isEmpty(message)) {
            message = "RootCause: " + ExceptionUtils.getRootCauseMessage(exception);
        } 
        else {
            message += ", RootCause: " + ExceptionUtils.getRootCauseMessage(exception);
        }
        if (!StringUtils.isEmpty(message)) {
            if (message.length() > 1024) {
                message = message.substring(0, 1000) + " TRUNCATED...";
            }
        }
        return message;
    }

}
