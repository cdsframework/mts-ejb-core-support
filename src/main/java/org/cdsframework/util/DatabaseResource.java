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

import org.cdsframework.enumeration.DatabaseType;

/**
 *
 * @author HLN Consulting, LLC
 */
public class DatabaseResource {

    private String resourceId;
    private String jndiName;
    private DatabaseType resourceType;
    private String schemaOwner;

    public DatabaseResource(String resourceId, String jndiName, DatabaseType resourceType, String schemaOwner) {
        this.resourceId = resourceId;
        this.jndiName = jndiName;
        this.resourceType = resourceType;
        this.schemaOwner = schemaOwner;
    }

    /**
     * Get the value of schemaOwner
     *
     * @return the value of schemaOwner
     */
    public String getSchemaOwner() {
        return schemaOwner;
    }

    /**
     * Set the value of schemaOwner
     *
     * @param schemaOwner new value of schemaOwner
     */
    public void setSchemaOwner(String schemaOwner) {
        this.schemaOwner = schemaOwner;
    }

    /**
     * Get the value of resourceType
     *
     * @return the value of resourceType
     */
    public DatabaseType getResourceType() {
        return resourceType;
    }

    /**
     * Set the value of resourceType
     *
     * @param resourceType new value of resourceType
     */
    public void setResourceType(DatabaseType resourceType) {
        this.resourceType = resourceType;
    }

    /**
     * Get the value of jndiName
     *
     * @return the value of jndiName
     */
    public String getJndiName() {
        return jndiName;
    }

    /**
     * Set the value of jndiName
     *
     * @param jndiName new value of jndiName
     */
    public void setJndiName(String jndiName) {
        this.jndiName = jndiName;
    }

    /**
     * Get the value of resourceId
     *
     * @return the value of resourceId
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Set the value of resourceId
     *
     * @param resourceId new value of resourceId
     */
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    public String toString() {
        return "DatabaseResource{" + "resourceId=" + resourceId + ", jndiName=" + jndiName + ", resourceType=" + resourceType + ", schemaOwner=" + schemaOwner + '}';
    }

}
