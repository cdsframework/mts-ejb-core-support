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
package org.cdsframework.mybatis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.migration.Change;
import org.apache.ibatis.migration.MigrationException;
import org.apache.ibatis.migration.MigrationLoader;
import org.apache.ibatis.migration.MigrationReader;
import org.cdsframework.util.LogUtils;

/**
 * MigrationLoader for retrieving scripts from classpath jar.
 *
 * @author HLN Consulting, LLC
 */
public class ClassloaderFileMigrationLoader implements MigrationLoader {

    private final ClassLoader classLoader;
    private final String scriptPath;
    private final String charset;
    private final Properties properties;
    private static final LogUtils logger = LogUtils.getLogger(ClassloaderFileMigrationLoader.class);

    public ClassloaderFileMigrationLoader(ClassLoader classLoader, String scriptPath, String charset, Properties properties) {
        super();
        this.classLoader = classLoader;
        this.scriptPath = scriptPath;
        this.charset = charset;
        this.properties = properties;
    }

    public boolean scriptExists() {
        return getMigrations().size() > 0;
    }
    /**
     * Retrieve the migration Change objects from the classloader.
     * 
     * @return 
     */
    @Override
    public List<Change> getMigrations() {
        final String METHODNAME = "getMigrations ";
        List<Change> migrations = new ArrayList<Change>();
        InputStream inputStream = null;
        try {
            logger.debug(METHODNAME, "scriptPath: ", scriptPath);
            inputStream = classLoader.getResourceAsStream(scriptPath);
            if (inputStream != null) {
                List<String> filenames = IOUtils.readLines(inputStream, Charsets.UTF_8);
                logger.debug(METHODNAME, "filenames: ", filenames);

                Collections.sort(filenames);
                for (String filename : filenames) {
                    if (filename.endsWith(".sql") && !"bootstrap.sql".equals(filename)) {
                        Change change = parseChangeFromFilename(filename);
                        migrations.add(change);
                        logger.debug(METHODNAME, "processed: ", filename);
                    }
                }
            } else {
                logger.warn(METHODNAME, scriptPath + " does not exist.");
            }
        } catch (IOException e) {
            throw new MigrationException("Error parsing change files.", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    logger.error(e);
                }
            }
        }
        return migrations;
    }

    /**
     * Parse a Change object from the script filename.
     * 
     * @param filename
     * @return 
     */
    private Change parseChangeFromFilename(String filename) {
        try {
            Change change = new Change();
            int lastIndexOfDot = filename.lastIndexOf(".");
            String[] parts = filename.substring(0, lastIndexOfDot).split("_");
            change.setId(new BigDecimal(parts[0]));
            StringBuilder builder = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    builder.append(" ");
                }
                builder.append(parts[i]);
            }
            change.setDescription(builder.toString());
            change.setFilename(filename);
            return change;
        } catch (Exception e) {
            throw new MigrationException("Error parsing change from file.  Cause: " + e, e);
        }
    }

    /**
     * Retrieve a Reader object for a Change object.
     * 
     * @param change
     * @param undo
     * @return 
     */
    @Override
    public Reader getScriptReader(Change change, boolean undo) {
        final String METHODNAME = "getScriptReader ";
        try {
            logger.info(METHODNAME, "applying script: ", scriptPath, change.getFilename());
            URL resource = classLoader.getResource(scriptPath + change.getFilename());
            if (resource == null) {
                throw new MigrationException(scriptPath + change.getFilename() + " was not found!");
            }
            return new MigrationReader(new File(resource.toURI()), charset, undo, properties);
        } catch (IOException e) {
            throw new MigrationException("Error reading " + change.getFilename(), e);
        } catch (URISyntaxException e) {
            throw new MigrationException("Error reading " + change.getFilename(), e);
        }
    }

    /**
     * Retrieve a bootstrap Reader object.
     * 
     * @return 
     */
    @Override
    public Reader getBootstrapReader() {
        final String METHODNAME = "getBootstrapReader ";
        try {
            logger.info(METHODNAME, "applying bootstrap: ", scriptPath, "bootstrap.sql");
            URL resource = classLoader.getResource(scriptPath + "bootstrap.sql");
            if (resource == null) {
                throw new MigrationException(scriptPath + "bootstrap.sql was not found!");
            }
            File bootstrap = new File(resource.toURI());
            if (bootstrap.exists()) {
                return new MigrationReader(bootstrap, charset, false, properties);
            }
            return null;
        } catch (IOException e) {
            throw new MigrationException("Error reading bootstrap.sql", e);
        } catch (URISyntaxException e) {
            throw new MigrationException("Error reading bootstrap.sql", e);
        }
    }

}
