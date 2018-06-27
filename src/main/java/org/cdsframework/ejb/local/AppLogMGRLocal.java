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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.ScheduleExpression;
import javax.ejb.SessionContext;
import javax.ejb.Singleton;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.cdsframework.base.BaseAppLogMGR;
import org.cdsframework.dto.AppLogDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.ejb.bo.AppLogBO;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.group.Add;
import org.cdsframework.util.AuthenticationUtils;
import org.cdsframework.util.LogUtils;

/**
 *
 * @author HLN Consulting LLC
 */
//@Startup
@Singleton
@LocalBean
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AppLogMGRLocal extends BaseAppLogMGR {

    @EJB
    private AppLogBO appLogBO;
    
    @EJB
    private PropertyMGRLocal propertyMGRLocal;
    
    private final static LogUtils logger = LogUtils.getLogger(AppLogMGRLocal.class);
    private final int PROCESS_LIMIT = 500;
    
    @Resource
    private SessionContext sessionCtx;

    @PostConstruct
    public void postConstructor() {
        final String METHODNAME = "postConstructor ";
        String hour = propertyMGRLocal.get("APP_LOG_HOUR_INTERVAL", String.class);
        if (hour == null) {
            hour = "*";
        }
        String minute = propertyMGRLocal.get("APP_LOG_MINUTE_INTERVAL", String.class);
        if (minute == null) {
            minute = "*";
        }
        String second = propertyMGRLocal.get("APP_LOG_SECOND_INTERVAL", String.class);
        if (second == null) {
            second = "*";
        }
        boolean configureTimer = ! ( hour.equals("*") && 
                                     minute.equals("*") && 
                                     second.equals("*") );
        logger.debug(METHODNAME, "configureTimer=", configureTimer);

        // Configure timer
        if (configureTimer) {
            TimerConfig timerConfig = new TimerConfig();
            timerConfig.setPersistent(false);
            timerConfig.setInfo("Session update timer");
            ScheduleExpression scheduleExpression = new ScheduleExpression();
            scheduleExpression.hour(hour);
            scheduleExpression.minute(minute);
            scheduleExpression.second(second);

            sessionCtx.getTimerService().createCalendarTimer(scheduleExpression, timerConfig);
            logger.info(this.getClass().getSimpleName(), " initiated @ hour: " + hour + " minute: " + minute + " second: " + second);

        }
    }    

    @Timeout
    @Lock(LockType.READ)
    public void updateAppLogs() {
        final String METHODNAME = "updateAppLogs ";
        long start = System.nanoTime();
        logger.debug(METHODNAME, "Starting, appLogQueue.size()=", getAppLogQueue().size());
        int count = 0;
        while (getAppLogQueue().size() > 0 && count != PROCESS_LIMIT) {
            try {
                AppLogDTO appLogDTO = getAppLogQueue().poll();
//                logger.debug(METHODNAME, "appLogDTO=", appLogDTO);
                if (appLogDTO != null) {
                    appLogBO.addMainNew(appLogDTO, Add.class, AuthenticationUtils.getInternalSessionDTO(), new PropertyBagDTO());
                }
            } catch (Exception ex) {
                logger.error(METHODNAME, "An Exception has ocurred calling appLogBO.addMain; Message; " + ex.getMessage(), ex);
            }
            count++;
        }
        logger.logDuration(LogLevel.DEBUG, METHODNAME, start);
    }
    
}
