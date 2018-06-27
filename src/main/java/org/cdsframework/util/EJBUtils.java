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

import java.util.HashMap;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.validation.ValidatorFactory;
import org.cdsframework.base.BaseBO;
import org.cdsframework.base.BaseDAO;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.base.BaseSecurityMGR;
import org.cdsframework.ejb.local.PropertyMGRLocal;
import org.cdsframework.enumeration.LogLevel;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.NotFoundException;

/**
 *
 * @author HLN Consulting, LLC
 */
public class EJBUtils {

    private static final LogUtils logger = LogUtils.getLogger(EJBUtils.class);
    private static final String COMP_LOOKUP_BASE = "java:comp/";
    private static final String SECURITYMGRLOOKUP = "java:module/SecurityMGRInternal";
    private static final String VALIDATORFACTORYLOOKUP = "ValidatorFactory";
    private static final String TRANSACTIONSYNCHRONIZATIONREGISTRY = "TransactionSynchronizationRegistry";
    private static final String PROPERTYMGRLOOKUP = "java:app/mts-ejb-core-support/PropertyMGRLocal";

    private static final Map<String, Object> cacheMap = new HashMap<String, Object>();

    /**
     * Returns the transaction synchronization registry.
     *
     * @return
     * @throws MtsException
     */
    public static TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() throws MtsException {
        return (TransactionSynchronizationRegistry) performCompLookup(TRANSACTIONSYNCHRONIZATIONREGISTRY);
    }

    /**
     * Log transaction status.
     *
     * @throws MtsException
     */
    public static void logTransactionStatus() throws MtsException {
        final String METHODNAME = "logTransactionStatus ";
        int trxStatus = getTransactionSynchronizationRegistry().getTransactionStatus();
        String trxStatusDesc = "";
        if (trxStatus == Status.STATUS_ACTIVE) {
            trxStatusDesc = "STATUS_ACTIVE";
        } else if (trxStatus == Status.STATUS_COMMITTED) {
            trxStatusDesc = "STATUS_COMMITTED";
        } else if (trxStatus == Status.STATUS_COMMITTING) {
            trxStatusDesc = "STATUS_COMMITTING";
        } else if (trxStatus == Status.STATUS_MARKED_ROLLBACK) {
            trxStatusDesc = "STATUS_MARKED_ROLLBACK";
        } else if (trxStatus == Status.STATUS_NO_TRANSACTION) {
            trxStatusDesc = "STATUS_NO_TRANSACTION";
        } else if (trxStatus == Status.STATUS_PREPARED) {
            trxStatusDesc = "STATUS_PREPARED";
        } else if (trxStatus == Status.STATUS_PREPARING) {
            trxStatusDesc = "STATUS_PREPARING";
        } else if (trxStatus == Status.STATUS_ROLLEDBACK) {
            trxStatusDesc = "STATUS_ROLLEDBACK";
        } else if (trxStatus == Status.STATUS_ROLLING_BACK) {
            trxStatusDesc = "STATUS_ROLLING_BACK";
        } else if (trxStatus == Status.STATUS_UNKNOWN) {
            trxStatusDesc = "STATUS_UNKNOWN";
        }
        logger.info(METHODNAME + "TransactionStatus=" + trxStatusDesc);
    }

    /**
     * Returns the security manager.
     *
     * @return
     * @throws MtsException
     */
    public static BaseSecurityMGR getSecurityMGRLocal() throws MtsException {
        return (BaseSecurityMGR) getBaseLookupObject(SECURITYMGRLOOKUP, false);
    }

    /**
     * Returns the property manager.
     *
     * @return
     * @throws MtsException
     */
    public static PropertyMGRLocal getPropertyMGRLocal() throws MtsException {
        return (PropertyMGRLocal) getBaseLookupObject(PROPERTYMGRLOOKUP, true);
    }

    /**
     * Returns the validator factory.
     *
     * @return
     * @throws MtsException
     */
    public static ValidatorFactory getValidatorFactory() throws MtsException {
        return (ValidatorFactory) performCompLookup(VALIDATORFACTORYLOOKUP);
    }

    /**
     * Perform a comp namespace lookup.
     *
     * @param reference
     * @return
     * @throws MtsException
     */
    public static Object performCompLookup(String reference) throws MtsException {
        return getBaseLookupObject(COMP_LOOKUP_BASE + reference);
    }

    /**
     * Perform a base lookup.
     *
     * @param reference
     * @return
     * @throws MtsException
     */
    public static Object getBaseLookupObject(String reference) throws MtsException {
        return getBaseLookupObject(reference, true);
    }

    /**
     * Returns a base lookup.
     *
     * @param reference
     * @param throwException
     * @return
     * @throws MtsException
     */
    public static Object getBaseLookupObject(String reference, boolean throwException) throws MtsException {
        final String METHODNAME = "getBaseLookupObject ";
        if (reference == null) {
            throw new MtsException("reference is null");
        }
        Object lookupObject = cacheMap.get(reference);
        try {
            if (lookupObject == null) {
                InitialContext ctx = new InitialContext();
//                logger.info(METHODNAME, "reference=", reference);
                lookupObject = ctx.lookup(reference);
                cacheMap.put(reference, lookupObject);
            }
        } catch (NamingException ex) {
            if (throwException) {
                logger.error(METHODNAME, "Naming Exception ", ex.getExplanation(), " ", ex.getMessage(), " ", ex.getRemainingName(), " ", ex.getRemainingName());
                throw new MtsException("NamingException caught, Message:" + ex.getMessage(), ex);
            }
        }
        return lookupObject;
    }

    // these are cached references to what is in the cacheMap - put here to save on the constant getJndiDaoReferenceURI calls
    private final static Map<Class<? extends BaseDTO>, BaseDAO> BASE_DAO_CACHE = new HashMap<Class<? extends BaseDTO>, BaseDAO>();

    /**
     * Get a DTOs DAO.
     *
     * @param <S>
     * @param dtoClass
     * @return
     * @throws MtsException
     */
    public static <S extends BaseDTO> BaseDAO getDtoDao(Class<S> dtoClass) throws MtsException {
        final String METHODNAME = "getDtoDao ";
        logger.logBegin(METHODNAME);
        long start = System.nanoTime();
        BaseDAO<S> result = BASE_DAO_CACHE.get(dtoClass);
        try {
            if (result == null) {
                String reference = DTOUtils.getJndiDaoReferenceURI(dtoClass);
                if (!DTOUtils.isNoDAO(dtoClass)) {
                    logger.debug("Looking up DAO for: ", dtoClass.getSimpleName(), " - ", reference);
                    result = (BaseDAO<S>) getBaseLookupObject(reference, true);
                    BASE_DAO_CACHE.put(dtoClass, result);
                }
            }
        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);
            logger.logEnd(METHODNAME);
        }
        return result;
    }

    /**
     * Get the cached map for a particular DTO.
     *
     * @param <S>
     * @param dtoClass
     * @return
     * @throws MtsException
     * @throws NotFoundException
     */
    public static <S extends BaseDTO> Map<Object, S> getCachedMap(Class<S> dtoClass) throws MtsException, NotFoundException {
        return getDtoBo(dtoClass, false).getCachedMap();
    }

    /**
     * Get the cached DTO by primary key.
     *
     * @param <S>
     * @param dto
     * @return
     * @throws MtsException
     * @throws NotFoundException
     */
    public static <S extends BaseDTO> S getCachedDTOByPrimaryKey(S dto) throws MtsException, NotFoundException {
        return (S) getDtoBo(dto.getClass(), false).getCachedDTOByPrimaryKey(dto);
    }

    /**
     * Get a DTOs BO.
     *
     * @param <S>
     * @param dtoClass
     * @return
     * @throws MtsException
     */
    public static <S extends BaseDTO> BaseBO getDtoBo(Class<S> dtoClass) throws MtsException {
        return getDtoBo(dtoClass, false);
    }

    // these are cached references to what is in the cacheMap - put here to save on the constant getJndiBoReferenceURI calls
    private final static Map<Class<? extends BaseDTO>, BaseBO> BASE_BO_CACHE = new HashMap<Class<? extends BaseDTO>, BaseBO>();

    /**
     * Get a DTOs BO.
     *
     * @param <S>
     * @param dtoClass
     * @param debug
     * @return
     * @throws MtsException
     */
    public static <S extends BaseDTO> BaseBO getDtoBo(Class<S> dtoClass, boolean debug) throws MtsException {
        final String METHODNAME = "getDtoBo ";
        logger.logBegin(METHODNAME);
        long start = System.nanoTime();
        BaseBO<S> result = BASE_BO_CACHE.get(dtoClass);
        try {
            if (result == null) {
                String reference = DTOUtils.getJndiBoReferenceURI(dtoClass);
                logger.debug("Looking up BO for: ", dtoClass.getSimpleName(), " - ", reference);
                result = (BaseBO<S>) getBaseLookupObject(reference, true);
                logger.debug("Returning a native instance for: ", dtoClass.getSimpleName());
                result.setDebugBO(debug);
                BASE_BO_CACHE.put(dtoClass, result);
            }
        } finally {
            logger.logDuration(LogLevel.DEBUG, METHODNAME, start);
            logger.logEnd(METHODNAME);
        }
        return result;
    }
}
