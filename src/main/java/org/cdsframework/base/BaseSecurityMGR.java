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

import org.cdsframework.dto.AppDTO;
import org.cdsframework.dto.PropertyBagDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.dto.UserDTO;
import org.cdsframework.enumeration.PermissionType;
import org.cdsframework.exceptions.AuthenticationException;
import org.cdsframework.exceptions.AuthorizationException;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.NotFoundException;

/**
 * Provides local security manger functions.
 *
 * @author HLN Consulting, LLC
 */ 

public interface BaseSecurityMGR {

    public abstract void registerApplicationProxy(Class proxyClass) throws MtsException;

    /**
     * Evaluates whether a cachedUserDTO has the authority to make a particular request. Returns whether result should cascade.
     *
     * @param permissionType
     * @param dtoClass
     * @param sessionDTO
     * @param propertyBagDTO
     * @throws AuthenticationException
     * @throws AuthorizationException
     * @throws MtsException
     * @throws NotFoundException
     */
    public abstract void checkAuthority(PermissionType permissionType, Class<? extends BaseDTO> dtoClass, SessionDTO sessionDTO, PropertyBagDTO propertyBagDTO)
            throws AuthenticationException, AuthorizationException, MtsException, NotFoundException ;

    /**
     * Evaluates a sessionDTO for validity.
     *
     * @param sessionDTO
     * @return
     * @throws AuthenticationException
     * @throws AuthorizationException
     * @throws MtsException
     * @throws NotFoundException
     */
    public abstract boolean isSessionValid(SessionDTO sessionDTO)
            throws AuthenticationException, AuthorizationException, MtsException, NotFoundException;

    public abstract UserDTO getUserDTO(String username)
            throws MtsException, NotFoundException, AuthenticationException, AuthorizationException;

    public abstract AppDTO getAppDTO(String appName)
            throws MtsException, NotFoundException, AuthenticationException, AuthorizationException;

    /**
     * Check a users credentials against those in the database. Returns a UserDTO instance if successful.
     *
     * @param userDTO
     * @param password
     * @return {@link org.cdsframework.dto.UserDTO [UserDTO]} class instance.
     * @throws NotFoundException if cachedUserDTO is not found.
     * @throws AuthenticationException if session is bad.
     * @throws MtsException
     * @throws AuthorizationException
     */
    public abstract boolean authenticate(UserDTO userDTO, String password)
            throws NotFoundException, AuthenticationException, MtsException, AuthorizationException;

    /**
     * Create a new session based on the UserDTO and AppDTO object instances.
     *
     * @param userDTO
     * @param proxyingUserDTO
     * @param appDTO
     * @return {@link org.cdsframework.dto.SessionDTO [SessionDTO]} class instance.
     * @throws MtsException
     * @throws NotFoundException
     * @throws AuthenticationException
     * @throws AuthorizationException
     */
    public abstract SessionDTO createSession(UserDTO userDTO, UserDTO proxyingUserDTO, AppDTO appDTO)
            throws MtsException, NotFoundException, AuthenticationException, AuthorizationException ;

    /**
     * Checks whether a session is expired.
     *
     * @param session
     * @return
     */
    public abstract boolean isSessionExpired(SessionDTO session);

    /**
     * Removes a session from the cache and database.
     *
     * @param sessionDTO
     * @throws NotFoundException
     * @throws MtsException
     */
    public abstract void removeSession(SessionDTO sessionDTO) throws NotFoundException, MtsException;

    public abstract SessionDTO getProxiedUserSession(String userId, SessionDTO sessionDTO)
            throws MtsException, NotFoundException, AuthorizationException, AuthenticationException ;

    public abstract UserDTO getProxiedUserDTO(String userId, AppDTO proxyingUserAppDTO, UserDTO proxyingUserDTO)
            throws AuthorizationException, MtsException, NotFoundException, AuthenticationException ;
}
