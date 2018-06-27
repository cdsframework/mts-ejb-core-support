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
package org.cdsframework.ejb.timer;

import org.cdsframework.callback.QueueRunnerCallback;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import org.cdsframework.util.LogUtils;

/**
 *
 * @author sdn
 */
@Singleton
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class CallbackTimer {

    final static private LogUtils logger = LogUtils.getLogger(CallbackTimer.class);
    final private List<QueueRunnerCallback> queue = Collections.synchronizedList(new LinkedList<QueueRunnerCallback>());

    /**
     * Main Timeout method - process queue.
     */
    @Schedule(minute = "*", hour = "*", persistent = false)
    private void processQueue() {
        final String METHODNAME = "processQueue ";
        logger.debug(METHODNAME, "checking queue...");
        try {
            if (queue.size() > 0) {
                Iterator<QueueRunnerCallback> iterator = queue.iterator();
                while (iterator.hasNext()) {
                    QueueRunnerCallback queueRunnerCallback = iterator.next();
                    boolean result = queueRunnerCallback.execute();
                    logger.info(
                            METHODNAME,
                            "processed queued callback ", queueRunnerCallback,
                            ". Result: ", result,
                            "; retryable: ", queueRunnerCallback.isRetryable());
                    if (result || !queueRunnerCallback.isRetryable()) {
                        iterator.remove();
                    }
                }
            }
        } catch (Exception e) {
            logger.error(METHODNAME, e);
        }
    }

    public void queueCallback(QueueRunnerCallback queueRunnerCallback) {
        queue.add(queueRunnerCallback);
    }
}
