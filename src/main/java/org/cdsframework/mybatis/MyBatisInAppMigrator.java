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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;
import javax.ejb.Singleton;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.sql.DataSource;
import org.apache.ibatis.migration.Change;
import org.apache.ibatis.migration.DataSourceConnectionProvider;
import org.apache.ibatis.migration.MigrationLoader;
import org.apache.ibatis.migration.operations.BootstrapOperation;
import org.apache.ibatis.migration.operations.UpOperation;
import org.apache.ibatis.migration.options.DatabaseOperationOption;
import org.cdsframework.enumeration.DatabaseType;
import org.cdsframework.util.LogUtils;

/**
 * MyBatis Migrations in-app bootstrapping an migration implementation.
 *
 * @author HLN Consulting, LLC
 */
@Singleton
@TransactionManagement(TransactionManagementType.BEAN)
public class MyBatisInAppMigrator {

    private static final LogUtils logger = LogUtils.getLogger(MyBatisInAppMigrator.class);

    /**
     * Returns the MyBatis Migrations properties.
     *
     * @param databaseType
     * @return
     */
    public Properties getMyBatisProperties(DatabaseType databaseType) {
        final String METHODNAME = "getMyBatisProperties ";
        long start = System.nanoTime();
        InputStream inputStream = null;
        Properties myBatisProperties = new Properties();
        try {
            inputStream = getClass().getClassLoader().getResourceAsStream(String.format("mybatis-%s.properties", databaseType.toString().toLowerCase()));
            myBatisProperties.load(inputStream);
            logger.info(METHODNAME, "getting properties for database type: ", databaseType);
            for (String propertyName : myBatisProperties.stringPropertyNames()) {
                if (!propertyName.equalsIgnoreCase("changelog")) {
                    logger.info(METHODNAME, propertyName, ": ", myBatisProperties.getProperty(propertyName));
                }
            }
        } catch (IOException e) {
            logger.error(METHODNAME, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    logger.error(e);
                }
            }
            logger.logDuration(METHODNAME, start);
        }
        return myBatisProperties;
    }

    /**
     * Get a new MigrationLoader for a particular script path.
     *
     * @param scriptPath
     * @param myBatisProperties
     * @return
     */
    private MigrationLoader getMigrationLoader(String scriptPath, Properties myBatisProperties) {
        return new ClassloaderFileMigrationLoader(getClass().getClassLoader(),
                scriptPath,
                null,
                myBatisProperties);
    }

    /**
     * Perform the bootstrap operation for a database resource.
     *
     * @param dataSource
     * @param scriptPath
     * @param myBatisProperties
     * @return
     */
    public String bootstrapDb(DataSource dataSource, String scriptPath, Properties myBatisProperties) {
        final String METHODNAME = "migrateDb ";
        String result;

        long start = System.nanoTime();
        DataSourceConnectionProvider connectionProvider = new DataSourceConnectionProvider(dataSource);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        try {
            MigrationLoader migrationLoader = getMigrationLoader(scriptPath, myBatisProperties);

            // determine if bootstrap.sql exists - if so - run it.
            boolean bootstrapExists = false;
            for (Change item : migrationLoader.getMigrations()) {
                if (item.getFilename().endsWith("bootstrap.sql")) {
                    bootstrapExists = true;
                    break;
                }
            }
            if (bootstrapExists) {
                new BootstrapOperation().operate(connectionProvider,
                        migrationLoader,
                        getDatabaseOperationOptions(myBatisProperties),
                        printStream);
            }

            // migrate the rest of the db...
            migrateDb(dataSource, scriptPath, printStream, myBatisProperties);

        } finally {
            result = outputStream.toString();
            try {
                outputStream.close();
            } catch (Exception e) {
                logger.error(e);
            }
            try {
                printStream.close();
            } catch (Exception e) {
                logger.error(e);
            }
        }

        logger.logDuration(METHODNAME, start);
        return result;
    }

    /**
     * Perform MyBatis Migrations in-app database updates for a database resource.
     *
     * @param dataSource
     * @param scriptPath
     * @param myBatisProperties
     * @return
     */
    public String migrateDb(DataSource dataSource, String scriptPath, Properties myBatisProperties) {
        final String METHODNAME = "migrateDb ";
        String result;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);

        try {
            migrateDb(dataSource, scriptPath, printStream, myBatisProperties);
        } finally {
            result = outputStream.toString();
            try {
                outputStream.close();
            } catch (Exception e) {
                logger.error(e);
            }
            try {
                printStream.close();
            } catch (Exception e) {
                logger.error(e);
            }
        }
        return result;
    }

    /**
     * Convert a Properties instance to a DatabaseOperationOption instance.
     *
     * @param myBatisProperties
     * @return
     */
    private DatabaseOperationOption getDatabaseOperationOptions(Properties myBatisProperties) {
        final String METHODNAME = "getDatabaseOperationOptions ";
        DatabaseOperationOption result = new DatabaseOperationOption();
        for (String propertyName : myBatisProperties.stringPropertyNames()) {
            if ("changelog".equalsIgnoreCase(propertyName)) {
                result.setChangelogTable(myBatisProperties.getProperty(propertyName));
                logger.debug(METHODNAME, "set option value for ", propertyName, " '", myBatisProperties.getProperty(propertyName), "' to ", result.getChangelogTable());
            } else if ("send_full_script".equalsIgnoreCase(propertyName)) {
                result.setSendFullScript(Boolean.valueOf(myBatisProperties.getProperty(propertyName)));
                logger.debug(METHODNAME, "set option value for ", propertyName, " '", myBatisProperties.getProperty(propertyName), "' to ", result.isSendFullScript());
            } else if ("full_line_delimiter".equalsIgnoreCase(propertyName)) {
                result.setFullLineDelimiter(Boolean.valueOf(myBatisProperties.getProperty(propertyName)));
                logger.debug(METHODNAME, "set option value for ", propertyName, " '", myBatisProperties.getProperty(propertyName), "' to ", result.isFullLineDelimiter());
            } else if ("auto_commit".equalsIgnoreCase(propertyName)) {
                result.setAutoCommit(Boolean.valueOf(myBatisProperties.getProperty(propertyName)));
                logger.debug(METHODNAME, "set option value for ", propertyName, " '", myBatisProperties.getProperty(propertyName), "' to ", result.isAutoCommit());
            } else if ("delimiter".equalsIgnoreCase(propertyName)) {
                result.setDelimiter(myBatisProperties.getProperty(propertyName));
                logger.debug(METHODNAME, "set option value for ", propertyName, " '", myBatisProperties.getProperty(propertyName), "' to ", result.getDelimiter());
            } else {
                logger.warn(METHODNAME, "unused property: ", propertyName, " '", myBatisProperties.getProperty(propertyName), "'");
            }
        }
        return result;
    }

    /**
     * Perform MyBatis Migrations in-app database updates for a database resource.
     *
     * @param dataSource
     * @param scriptPath
     * @param printStream
     * @param myBatisProperties
     * @return
     */
    private void migrateDb(DataSource dataSource, String scriptPath, PrintStream printStream, Properties myBatisProperties) {
        final String METHODNAME = "migrateDb ";

        long start = System.nanoTime();
        DataSourceConnectionProvider connectionProvider = new DataSourceConnectionProvider(dataSource);

        new UpOperation().operate(connectionProvider,
                getMigrationLoader(scriptPath, myBatisProperties),
                getDatabaseOperationOptions(myBatisProperties),
                printStream);

        logger.logDuration(METHODNAME, start);
    }
}
