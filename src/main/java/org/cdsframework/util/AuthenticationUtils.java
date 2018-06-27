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

import org.cdsframework.dto.AppDTO;
import org.cdsframework.dto.SessionDTO;
import org.cdsframework.dto.UserDTO;

/**
 *
 * @author HLN Consulting, LLC
 */
public class AuthenticationUtils {

    private final static String INTERNAL = "INTERNAL";
    private final static UserDTO userDTO = new UserDTO();
    private final static AppDTO appDTO = new AppDTO();
    private final static SessionDTO sessionDTO;
    private final static String internalSessionId;
    private final static String internalAppId;
    private final static String internalUserId;

    static {
        userDTO.setUserId(userDTO.getUuid().toString().substring(0, 32));
        userDTO.setUsername(INTERNAL);
        appDTO.setAppId(appDTO.getUuid().toString().substring(0, 32));
        appDTO.setAppName(INTERNAL);
        sessionDTO = new SessionDTO();
        sessionDTO.setUserDTO(userDTO);
        sessionDTO.setAppDTO(appDTO);
        sessionDTO.setSessionId(sessionDTO.getUuid().toString().substring(0, 32));
        internalSessionId = sessionDTO.getSessionId();
        internalAppId = appDTO.getAppId();
        internalUserId = userDTO.getUserId();
    }

    public static boolean isInternalSession(SessionDTO sessionDTO) {
        boolean result = false;
        if (sessionDTO != null && sessionDTO.getSessionId() != null) {
            result = sessionDTO.getSessionId().equals(internalSessionId);
        }
        return result;
    }

    public static boolean isInternalApp(AppDTO appDTO) {
        boolean result = false;
        if (appDTO != null && appDTO.getAppId() != null) {
            result = appDTO.getAppId().equals(internalAppId);
        }
        return result;
    }

    public static boolean isInternalUser(UserDTO userDTO) {
        boolean result = false;
        if (userDTO != null && userDTO.getUserId() != null) {
            result = userDTO.getUserId().equals(internalUserId);
        }
        return result;
    }

    public static UserDTO getInternalUserDTO() {
        return userDTO;
    }

    public static AppDTO getInternalAppDTO() {
        return appDTO;
    }

    public static SessionDTO getInternalSessionDTO() {
        return sessionDTO;
    }

}
