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
package org.cdsframework.util;

import java.io.InputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.cdsframework.exceptions.MtsException;

/**
 *
 * @author HLN Consulting, LLC
 */
public class XmlTableResourceUtils {

    protected final static LogUtils logger = LogUtils.getLogger(XmlTableResourceUtils.class);

    /**
     * unmarshal an object of the specified type from the provided InputStream
     *
     * @param <S> generic type argument for return value
     * @param inputStream the source of the marshaled data
     * @param cdsObjectClass the type of object to cast the result to
     * @return
     * @throws MtsException on JAXBExceptions
     */
    public static <S> S objectFromStream(InputStream inputStream, Class<S> cdsObjectClass)
            throws MtsException {
        S object = null;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("org.cdsframework.util.table");
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            object = (S) unmarshaller.unmarshal(inputStream);
        } catch (JAXBException e) {
            logger.error(e);
            throw new MtsException(e.getMessage(), e);
        }
        return object;
    }
}
