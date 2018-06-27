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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.SessionContext;
import javax.ejb.Timeout;
import org.cdsframework.ejb.local.PropertyMGRLocal;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.util.LogUtils;

/**
 *
 * @author HLN Consulting, LLC
 */
public abstract class BaseTimer {

    protected LogUtils logger;
    @EJB
    private PropertyMGRLocal propertyMGRLocal;
    @Resource
    private SessionContext sessionCtx;

    @PostConstruct
    private void postConstructor() {
        logger = LogUtils.getLogger(getClass());
        try {
            initialize();
        } catch (MtsException e) {
            logger.error(e);
        }
    }

    // initialize in descendant, initialize variables, create schedule
    abstract protected void initialize() throws MtsException;

    @Timeout
    private void timeOutMain() {
        final String METHODNAME = "timeOutMain ";
        logger.logBegin(METHODNAME);
        try {
            timeOut();
        } catch (Exception ex) {
            logger.error(METHODNAME, "An Exception has occurred; Message: ", ex.getMessage(), ex);
        } finally {
            logger.logEnd(METHODNAME);
        }
    }

    // timeOut in descendant
    abstract protected void timeOut() throws MtsException;

    public PropertyMGRLocal getPropertyMGRLocal() {
        return propertyMGRLocal;
    }

    public SessionContext getSessionCtx() {
        return sessionCtx;
    }
}
