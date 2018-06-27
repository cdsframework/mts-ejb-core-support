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
package org.cdsframework.callback;

/**
 * QueueRunnerCallback is an interface for use with the generic CallbackTimer mechanism.
 *
 * @author sdn
 * @param <T>
 */
public interface QueueRunnerCallback<T extends Object> {

    /**
     * Contains the callback's execution code.
     *
     * @return whether the execution was deemed successful
     */
    public boolean execute();

    /**
     * Returns whether the callback is retryable.
     *
     * @return whether the callback is retryable
     */
    public boolean isRetryable();

    /**
     * Returns the retry counter.
     *
     * @return the retry counter
     */
    public int getRetryCount();

    /**
     * Sets the retry counter.
     *
     * @param retryCount the retry counter
     */
    public void setRetryCount(int retryCount);

    /**
     * Registers that the callback failed once. With a counter-based retry mechanism, this would result in some sort of decrementing
     * of the counter.
     */
    public void failOnce();

    /**
     * Tells the retry mechanism that the callback has failed and it should no longer attempt retries. With a counter-based retry
     * mechanism, this would result in setting the retry count to zero.
     */
    public void fail();
}
