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
package org.cdsframework.ejb.local;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.CopyStrategyConfiguration;
import net.sf.ehcache.config.SizeOfPolicyConfiguration;
import org.cdsframework.annotation.Cached;
import org.cdsframework.base.BaseDTO;
import org.cdsframework.enumeration.CacheType;
import org.cdsframework.enumeration.Operation;
import org.cdsframework.exceptions.MtsException;
import org.cdsframework.exceptions.CacheLoadException;
import org.cdsframework.exceptions.NotFoundException;
import org.cdsframework.util.DTOUtils;
import org.cdsframework.util.LogUtils;

/**
 *
 * @author HLN Consulting, LLC
 */
@Singleton
@LocalBean
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class CacheMGRLocal {

    @EJB
    private PropertyMGRLocal propertyMGRLocal;
    private LogUtils logger;
    private CacheManager cacheManager;
    private Map<Class<? extends BaseDTO>, List<Class<? extends BaseDTO>>> classDependencies = new HashMap<Class<? extends BaseDTO>, List<Class<? extends BaseDTO>>>();
    private List<Class<? extends BaseDTO>> intializing = new ArrayList<Class<? extends BaseDTO>>();
    private final String defaultCache = "Standard";
    private int maxTimeout = 15;
    private int sizeOfDepthLimit = 3500;
    private String copyStrategyClass = null;

    /*
     * Default no arg constructor initializes the superclass.
     */
    public CacheMGRLocal() {
        logger = LogUtils.getLogger(CacheMGRLocal.class);
        logger.debug("CacheMGRLocal Constructor");
    }

    @PostConstruct
    private void postConstructor() {
        final String METHODNAME = "postConstructor ";
        logger.info(METHODNAME, " creating CacheManager");
        cacheManager = CacheManager.create();

        // Get Properties
        maxTimeout = propertyMGRLocal.get("EHCACHE_MAX_TIMEOUT", Integer.class);
        sizeOfDepthLimit = propertyMGRLocal.get("EHCACHE_SIZE_OF_DEPTH_LIMIT", Integer.class);
        copyStrategyClass = propertyMGRLocal.get("EHCACHE_COPY_STRATEGY_CLASS", String.class);
        logger.info(METHODNAME, "maxTimeout=", maxTimeout, " copyStrategyClass=", copyStrategyClass);

        // Set the max timeout
        cacheManager.getTransactionController().setDefaultTransactionTimeout(maxTimeout);
        String[] cacheNames = cacheManager.getCacheNames();
        logger.info(METHODNAME, "cacheManager.getActiveConfigurationText()=", cacheManager.getActiveConfigurationText());
        logger.info(METHODNAME, "cacheManager.getConfiguration().getConfigurationSource()=", cacheManager.getConfiguration().getConfigurationSource());
        for (String cache : cacheNames) {
            logger.info(METHODNAME, "cache=", cache);
        }
        logger.info(METHODNAME, "add the cache=", defaultCache);
        // Add the standard defauft cache
        cacheManager.addCache(defaultCache);
    }

    @PreDestroy
    private void preDestroy() {
        final String METHODNAME = "preDestroy ";
        logger.info(METHODNAME);
        cacheManager.shutdown();

    }

    /**
     * Purge a map from the cache
     *
     * @param <S>
     * @param dtoClass
     * @throws MtsException
     * @throws NotFoundException
     */
    public <S extends BaseDTO> void purgeCache(Class<S> dtoClass) throws MtsException, NotFoundException {
        final String METHODNAME = "purgeCache ";
        // Cache Exist ?
        if (isCacheExist(dtoClass)) {
            Cache cache = getCache(dtoClass);
            logger.info("Purging cache of ", cache.getName(), " cacheKey ", dtoClass.getSimpleName(), " entry.");

            // To Do, instead purging the cache, lookup the object and update it
            // Example, UserDTO is changed SessionDTO is purged,
            // Use query capabilities of ehCache to locate the SessionDTO's that contain the userDTO and update them

            // DtoClass is the cache
            if (dtoClass.getSimpleName().equalsIgnoreCase(cache.getName())) {
                logger.info(METHODNAME + "removing by cache.getName()=" + cache.getName());
                cache.removeAll();
            } else {
                cache.remove(dtoClass.getSimpleName());
                logger.info(METHODNAME + "removing by dtoClass.getSimpleName()=" + dtoClass.getSimpleName());
            }

            // Reinitialize the cache, after update logic is in place this will not be necessary
//            EJBUtils.getDtoBo(dtoClass).initializeCache();
        }
    }

    /**
     * Refresh a member of a cached map
     *
     * @param <S>
     * @param dto
     * @param operation
     * @throws MtsException
     * @throws NotFoundException
     */
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public <S extends BaseDTO> void save(S dto, Operation operation) throws MtsException, NotFoundException {
        final String METHODNAME = "save ";

        if (dto != null) {
            Class<? extends BaseDTO> dtoClass = dto.getClass();
            // Cache Exist ?
            if (isCacheExist(dtoClass)) {
                Cache cache = getCache(dtoClass);
                // DtoClass is the cache
                if (dtoClass.getSimpleName().equalsIgnoreCase(cache.getName())) {
                    // Handle Add
                    if (operation == Operation.ADD) {
                        logger.debug(METHODNAME, "operation=", operation, " cached element, ", dto);
                        cache.put(new Element(dto.getPrimaryKey(), dto));

                        // Handle Update
                    } else if (operation == Operation.UPDATE || operation == Operation.DELETE) {
                        Element element = cache.get(dto.getPrimaryKey());
                        if (element == null) {
                            // Log this to see if Elements don't exist in the cache but requests for update are occurring
                            logger.error(METHODNAME, "operation=", operation, " did not find element, ", dto, " in the cache=", cache.getName(), " this is very odd");
                        }
                        if (operation == Operation.UPDATE) {
                            logger.debug(METHODNAME, "operation=", operation, " updating cached element, ", dto);
                            cache.put(new Element(dto.getPrimaryKey(), dto));
                        } else if (operation == Operation.DELETE) {
                            logger.debug(METHODNAME, "operation=", operation, " removed cached element, ", dto);
                            cache.remove(dto.getPrimaryKey());
                        }
                    }
                } else {
                    // Map approach
                    Map<Object, S> cachedMap = (Map<Object, S>) getCachedMap(dtoClass);
                    if (cachedMap != null) {
                        // Handle Add
                        if (operation == Operation.ADD) {
                            logger.debug(METHODNAME, "operation=", operation, " cached MAP element, ", dto, " in map");
                            cachedMap.put(dto.getPrimaryKey(), dto);
                        } else if (operation == Operation.UPDATE || operation == Operation.DELETE) {
                            S dtoObject = cachedMap.get(dto.getPrimaryKey());
                            if (dtoObject == null) {
                                // Log this to see if Elements don't exist in the cache but requests for update are occurring
                                logger.error(METHODNAME, "operation=", operation, " did not find element, ", dto, " in the cache=", cache.getName(), " this is very odd");
                            }
                            if (operation == Operation.UPDATE) {
                                logger.debug(METHODNAME, "operation=", operation, " updating cached MAP element, ", dto, " in map");
                                cachedMap.put(dto.getPrimaryKey(), dto);
                            } else if (operation == Operation.DELETE) {
                                logger.debug(METHODNAME, "operation=", operation, " removed cached MAP element, ", dto, " in map");
                                cachedMap.remove(dto.getPrimaryKey());
                            }
                        }

                        // Update the cache with the Map changes, may not be necessary but just in case it is.
                        if (!cachedMap.isEmpty()) {
                            logger.debug(METHODNAME, "updating cached MAP in cache=", cache.getName());
                            cache.put(new Element(dtoClass.getSimpleName(), cachedMap));
                        } else {
                            logger.debug(METHODNAME, "removing cached MAP from cache=", cache.getName());
                            cache.remove(dtoClass.getSimpleName());
                        }
                    } else {
                        logger.error(METHODNAME, "MAP not found");
                    }
                }

                // Example UserDTO is changed SessionDTO is purged
                List<Class<? extends BaseDTO>> dependencyList = classDependencies.get(dtoClass);
                if (dependencyList != null) {
                    for (Class<? extends BaseDTO> type : dependencyList) {
                        purgeCache(type);
                    }
                }
            }
        }
    }

    /**
     * DO NOT CALL THIS DIRECTLY. Use the BO to call it as the cache may not be initialized retrieve a single object from the cache
     *
     * @param <S>
     * @param dto
     * @return
     * @throws MtsException
     * @throws NotFoundException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public <S extends BaseDTO> S getCachedDTOByPrimaryKey(S dto) throws MtsException, NotFoundException {
        final String METHODNAME = "getCachedDTOByPrimaryKey ";
        S result = null;
        if (dto != null && dto.getPrimaryKey() != null) {
            Class<S> dtoClass = (Class<S>) dto.getClass();
            // Cache Exist ?
            if (isCacheExist(dtoClass)) {
                Cache cache = getCache(dtoClass);
                // DtoClass is the cache
                if (dtoClass.getSimpleName().equalsIgnoreCase(cache.getName())) {
                    Element element = cache.get(dto.getPrimaryKey());
                    if (element != null) {
                        result = (S) element.getObjectValue();
                    }
                } else {
                    // Map approach
                    Map<Object, S> cachedMap = getCachedMap(dtoClass);
                    if (cachedMap != null) {
                        result = cachedMap.get(dto.getPrimaryKey());
                    } else {
                        logger.error(METHODNAME, " cachedObject is null for ", dto.getClass().getSimpleName());
                    }
                }
            }
        }
        return result;
    }

    @Lock(LockType.WRITE)
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public <S extends BaseDTO> boolean isCacheInitialized(Class<S> dtoClass) throws MtsException, NotFoundException {
        final String METHODNAME = "isCacheInitialized ";
        boolean cacheInitialized = false;

        /*
        if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
            logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " intializing=", intializing);
        }
        */
        if (intializing.contains(dtoClass)) {
            logger.error(METHODNAME, "currently intializing ", dtoClass.getSimpleName(), " intializing ", intializing);
            throw new CacheLoadException(dtoClass.getSimpleName() + " is already intializing! Set isSelfReferencing = true if this DTO is self-referencing.");
        }
        try {
            logger.debug(METHODNAME, "adding ", dtoClass.getSimpleName(), " to initializing cache tracker");
            intializing.add(dtoClass);
            Cache cache = getCache(dtoClass);
            /*
            if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
                logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " cache.getName()=", cache.getName());
                logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " dtoClass.getSimpleName().equalsIgnoreCase(cache.getName())=", dtoClass.getSimpleName().equalsIgnoreCase(cache.getName()));
                try {
                    cache.getSize();
                }
                catch (Exception e) {
                    logger.error(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " Size corrupt?", e);
                }
            }
            */
            if (dtoClass.getSimpleName().equalsIgnoreCase(cache.getName())) {
                logger.debug(METHODNAME, "cache.getSize()= ", cache.getSize());
                /*
                if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
                    logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(),  "cache.getSize()= ", cache.getSize());
                }
                */
                if (cache.getSize() > 0) {
                    cacheInitialized = true;
                }
            } else {
                /*
                if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
                    logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " HashMap version");
                }
                */
                Element element = cache.get(dtoClass.getSimpleName());
                if (element != null) {
                    Map<Object, S> cachedMap = (Map<Object, S>) element;
                    logger.debug(METHODNAME, "cachedMap.isEmpty()= ", cachedMap.isEmpty());
                    if (!cachedMap.isEmpty()) {
                        cacheInitialized = true;
                    }
                }
            }
        } finally {
            logger.debug(METHODNAME, "cacheInitialized=", cacheInitialized, " for ", dtoClass.getSimpleName());
            if (cacheInitialized) {
                logger.debug(METHODNAME, "removing ", dtoClass.getSimpleName(), " from initializing cache tracker");
                intializing.remove(dtoClass);
            }
            /*
            if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
                logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " cacheInitialized=", cacheInitialized);
            }
            */
        }
        return cacheInitialized;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public <S extends BaseDTO> void initializeCache(Class<S> dtoClass, List<S> list) throws MtsException, NotFoundException {
        final String METHODNAME = "initializeCache ";
        logger.logBegin(METHODNAME);

        /*
        if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
            logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " list=", list);
        }
        */
        
        try {
            Cache cache = getCache(dtoClass);
            if (dtoClass.getSimpleName().equalsIgnoreCase(cache.getName())) {
                for (S baseDTO : list) {
                    cache.put(new Element(baseDTO.getPrimaryKey(), baseDTO));
                }
            } else {
                Map<Object, S> baseDTOMap = new LinkedHashMap<Object, S>();
                for (S baseDTO : list) {
                    baseDTOMap.put(baseDTO.getPrimaryKey(), baseDTO);
                }
                cache.put(new Element(dtoClass.getSimpleName(), baseDTOMap));
            }
            recordClassDependencies(new ArrayList<Class>(), dtoClass, dtoClass);
//            Cached cached = DTOUtils.getCached(dtoClass);
//            for (Class<? extends BaseDTO> type : cached.customDependencyList()) {
//                addClassDependency(type, dtoClass);
//            }
        } finally {
            boolean remove = intializing.remove(dtoClass);
            logger.debug(METHODNAME, "removing ", dtoClass.getSimpleName(), " from initializing cache tracker", 
                    "remove=", remove, " initializing array=", intializing);
            /*
            if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
                logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " remove=", remove, " initializing array=", intializing);
            }
            */
        }
    }

    /**
     * DO NOT CALL THIS DIRECTLY. Use the BO to call it as the cache may not be initialized Returns a particular map.
     *
     * @param <S>
     * @param dtoClass
     * @return
     * @throws MtsException
     * @throws NotFoundException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public <S extends BaseDTO> Map<Object, S> getCachedMap(Class<S> dtoClass) throws MtsException, NotFoundException {
        final String METHODNAME = "getCachedMap ";
        logger.logBegin(METHODNAME);
        logger.debug(METHODNAME, "for ", dtoClass.getSimpleName());
        Map<Object, S> cachedMap = new HashMap<Object, S>();
        try {
            // CacheExists ?
            if (isCacheExist(dtoClass)) {
                Cache cache = getCache(dtoClass);
                Object cachedObject = null;
                // DtoClass is the cache
                if (dtoClass.getSimpleName().equalsIgnoreCase(cache.getName())) {
                    List keys = cache.getKeys();
                    if (!keys.isEmpty()) {
                        Map<Object, Element> elements = cache.getAll(keys);
                        Comparator dtoComparator = DTOUtils.getDtoComparator(dtoClass);
                        HashMap<Object, S> dtoMap = new LinkedHashMap<Object, S>();
                        if (dtoComparator != null) {
                            List<S> values = new ArrayList<S>();
                            for (Map.Entry<Object, Element> mapEntry : elements.entrySet()) {
                                values.add((S) mapEntry.getValue().getObjectValue());
                            }
                            Collections.sort(values, dtoComparator);
                            for (S item : values) {
                                dtoMap.put(item.getPrimaryKey(), item);
                            }
                        } else {
                            for (Map.Entry<Object, Element> mapEntry : elements.entrySet()) {
                                dtoMap.put(mapEntry.getKey(), (S) mapEntry.getValue().getObjectValue());
                                //dtoMap.put(((S) mapEntry.getValue().getObjectValue()).getPrimaryKey(), (S) mapEntry.getValue().getObjectValue());
                            }
                        }
                        cachedObject = dtoMap;
                    }
                } else {
                    Element element = cache.get(dtoClass.getSimpleName());
                    if (element != null) {
                        cachedObject = element.getObjectValue();
                    }
                }

                // Convert to Map
                if (cachedObject != null) {
                    cachedMap = (Map<Object, S>) cachedObject;
                    logger.debug(METHODNAME, "cachedMap.size()=", cachedMap.size());
                }
            } else {
                logger.error(METHODNAME, "Cache for ", dtoClass.getSimpleName(), " does not exist, ensure you are calling the BO.getCachedMap");
            }
        } finally {
            logger.logEnd(METHODNAME);
        }
        return cachedMap;
    }

    private <S extends BaseDTO> boolean isCacheExist(Class<S> dtoClass) throws MtsException {
        final String METHODNAME = "isCacheExist ";
        boolean cacheExists = cacheManager.cacheExists(getCacheName(dtoClass));
        if (!cacheExists) {
            logger.error(METHODNAME, "Cache for ", dtoClass.getSimpleName(), " does not exist, ensure you are calling the BO.getCachedMap");
        }
        return cacheExists;
    }

    private <S extends BaseDTO> String getCacheName(Class<S> dtoClass) throws MtsException {
        final String METHODNAME = "getCacheName ";
        logger.logBegin(METHODNAME);
        String cacheName = defaultCache;
        try {
            String cacheKey = dtoClass.getSimpleName();
            if (DTOUtils.isCached(dtoClass)) {
                // Determine the cacheType, must be configured in ehcache.xml
                CacheType cacheType = DTOUtils.getCached(dtoClass).cacheType();
                cacheName = cacheType.toString();
                // Cache per Class ?
                if (cacheType == CacheType.Class) {
                    cacheName = cacheKey;
                }
            } else {
                throw new MtsException("DtoClass " + dtoClass.getSimpleName() + " is not cached");
            }
        } finally {
            logger.logEnd(METHODNAME);
        }

        return cacheName;
    }

    private <S extends BaseDTO> Cache getCache(Class<S> dtoClass) throws MtsException {
        final String METHODNAME = "getCache ";
        logger.logBegin(METHODNAME); 

        Cache cache = null;
        try {
            String cacheName = getCacheName(dtoClass);
            boolean cacheNameFound = cacheManager.cacheExists(cacheName);
            /*
            if (dtoClass.getSimpleName().equalsIgnoreCase("RecommendationStatusLkDTO")) {
                logger.debug(METHODNAME, "dtoClass=", dtoClass.getSimpleName(), " cacheNameFound=", cacheNameFound);
            }
            */
            
            if (!cacheNameFound) {
                Cached dtoCacheConfig = DTOUtils.getCached(dtoClass);
                CacheConfiguration cacheConfiguration = new CacheConfiguration();
                cacheConfiguration.setName(cacheName);
                cacheConfiguration.setEternal(true);
                if (dtoCacheConfig.transactionEnabled()) {
                    cacheConfiguration.setTransactionalMode("xa_strict");
                    CopyStrategyConfiguration copyStrategyConfiguration = new CopyStrategyConfiguration();
                    copyStrategyConfiguration.setClass(copyStrategyClass);
                    cacheConfiguration.addCopyStrategy(copyStrategyConfiguration);
                } else {
                    logger.info(METHODNAME, "disabling transactions for DTO: ", dtoClass.getCanonicalName());
                }
                SizeOfPolicyConfiguration sizeOfPolicyConfiguration = new SizeOfPolicyConfiguration();
                sizeOfPolicyConfiguration.setMaxDepth(sizeOfDepthLimit);
                sizeOfPolicyConfiguration.setMaxDepthExceededBehavior("abort");
                cacheConfiguration.addSizeOfPolicy(sizeOfPolicyConfiguration);
                cache = new Cache(cacheConfiguration);
                cacheManager.addCache(cache);
            }
            cache = cacheManager.getCache(cacheName);
        } finally {
            logger.logEnd(METHODNAME);
        }

        return cache;
    }

    /**
     * Record the DTO class dependencies that a cached map has
     *
     * @param dtoClass
     * @param sourceClass
     */
    private void recordClassDependencies(List<Class> processedList, Class<? extends BaseDTO> dtoClass, Class<? extends BaseDTO> sourceClass) {
        final String METHODNAME = "recordClassDependencies ";
        // logger.debug(METHODNAME, "got - ", processedList.size(), " - ", dtoClass.getSimpleName(), " - ", sourceClass.getSimpleName());
        processedList.add(dtoClass);
        List<Field> referenceDTOs = DTOUtils.getReferenceDTOs(dtoClass);
        for (Field field : referenceDTOs) {
            Class<? extends BaseDTO> type = (Class<? extends BaseDTO>) field.getType();
            if (!processedList.contains(type)) {
                addClassDependency(type, sourceClass);
                recordClassDependencies(processedList, type, sourceClass);
            }
        }
        Set<Class<? extends BaseDTO>> childClasses = DTOUtils.getParentChildRelationshipMapByDTO(dtoClass).keySet();
        for (Class<? extends BaseDTO> type : childClasses) {
            if (!processedList.contains(type)) {
                addClassDependency(type, sourceClass);
                recordClassDependencies(processedList, type, sourceClass);
            }
        }
    }

    /**
     * Utility to add a member to the dependency map
     *
     * @param dtoClass
     * @param sourceClass
     */
    private void addClassDependency(Class<? extends BaseDTO> dtoClass, Class<? extends BaseDTO> sourceClass) {
        final String METHODNAME = "addClassDependency ";
        if (dtoClass != sourceClass && DTOUtils.isCached(dtoClass)) {
            List<Class<? extends BaseDTO>> dependencyList = classDependencies.get(dtoClass);
            if (dependencyList == null) {
                dependencyList = new ArrayList<Class<? extends BaseDTO>>();
                classDependencies.put(dtoClass, dependencyList);
            }
            if (!dependencyList.contains(sourceClass)) {
                dependencyList.add(sourceClass);
                logger.debug(METHODNAME, "Added ", sourceClass.getSimpleName(), " to ", dtoClass.getSimpleName());
            }
        }
    }
}
