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
package org.cdsframework.util.comparator;

import java.util.Arrays;
import java.util.Comparator;
import org.cdsframework.util.table.AlterOperation;

/**
 *
 * @author HLN Consulting, LLC
 */
public class AlterOperationComparator implements Comparator<AlterOperation> {

    @Override
    public int compare(AlterOperation o1, AlterOperation o2) {
        Integer versionOne = 0;
        Integer versionTwo = 0;
        if (o1 != null && o1.getAlterDmlFromVersion() != null) {
            versionOne = o1.getAlterDmlFromVersion().intValue();
        }
        if (o2 != null && o2.getAlterDmlFromVersion() != null) {
            versionTwo = o2.getAlterDmlFromVersion().intValue();
        }
        if (versionOne.equals(versionTwo)) {
            return 0;
        }
        Integer[] rep = new Integer[]{versionOne, versionTwo};
        Arrays.sort(rep);
        if (rep[0].equals(versionOne)) {
            return -1;
        } else {
            return 1;
        }
    }
}
