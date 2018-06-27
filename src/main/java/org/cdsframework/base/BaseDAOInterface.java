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

import java.util.List;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.ConstraintViolationException;
import org.cdsframework.exceptions.NotFoundException;

/**
 *
 * @param <T>
 * @author HLN Consulting, LLC
 */
public interface BaseDAOInterface<T extends BaseDTO> {

    /**
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws ConstraintViolationException
     * @throws MtsException
     * @throws NotFoundException
     */
    public int add(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, MtsException, NotFoundException;

    /**
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws ConstraintViolationException
     * @throws NotFoundException
     * @throws MtsException
     */
    public int update(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws ConstraintViolationException, NotFoundException, MtsException;

    /**
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws NotFoundException
     * @throws ConstraintViolationException
     * @throws MtsException
     */
    public int delete(T baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, ConstraintViolationException, MtsException;

    /**
     *
     * @param baseDTO
     * @param queryClass
     * @param childBOQueryClass
     * @param rollbackOnNotFound
     * @param sessionDTO
     * @param propertyBagDTO
     * @return 
     * @throws NotFoundException
     * @throws MtsException
     */
    public int setParentsChildren(BaseDTO baseDTO, Class queryClass, Class childBOQueryClass, boolean rollbackOnNotFound, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException;

    /**
     *
     * @param baseDTO
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws NotFoundException
     * @throws MtsException
     */
    public T findByPrimaryKey(T baseDTO, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException;

    /**
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws NotFoundException
     * @throws MtsException
     */
    public List<T> findByQueryList(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException;

    /**
     *
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @return
     * @throws NotFoundException
     * @throws MtsException
     */
    public T findByQuery(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws NotFoundException, MtsException;

    /**
     *
     * @param <S>
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param requiredType
     * @param propertyBagDTO
     * @return
     * @throws MtsException
     * @throws NotFoundException
     */
    public <S> S findObjectByQuery(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws MtsException, NotFoundException;

    /**
     *
     * @param <S>
     * @param baseDTO
     * @param queryClass
     * @param sessionDTO
     * @param requiredType
     * @param propertyBagDTO
     * @return
     * @throws MtsException
     * @throws NotFoundException
     */
    public <S> List<S> findObjectByQueryList(BaseDTO baseDTO, Class queryClass, SessionDTO sessionDTO, Class<S> requiredType, PropertyBagDTO propertyBagDTO)
            throws MtsException, NotFoundException;

    /**
     *
     * @param parentDTO
     * @param childRegistrationClass
     * @throws MtsException
     */
    public void registerParentSetter(Class<? extends BaseDTO> parentDTO, final Class childRegistrationClass)
            throws MtsException;

    /**
     *
     * @param <S>
     * @param autoKeySequence
     * @param sessionDTO
     * @param primaryKeyClass
     * @return
     * @throws MtsException
     */
    public <S> S getNewPrimaryKey(String autoKeySequence, SessionDTO sessionDTO, Class<S> primaryKeyClass)
            throws MtsException;
}
