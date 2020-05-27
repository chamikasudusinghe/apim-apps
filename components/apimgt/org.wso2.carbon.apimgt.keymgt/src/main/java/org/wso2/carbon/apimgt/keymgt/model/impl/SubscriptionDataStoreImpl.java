/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.apimgt.keymgt.model.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.InMemorySubscriptionValidationConstants;
import org.wso2.carbon.apimgt.api.model.subscription.CacheableEntity;
import org.wso2.carbon.apimgt.keymgt.model.SubscriptionDataStore;
import org.wso2.carbon.apimgt.keymgt.model.entity.API;
import org.wso2.carbon.apimgt.keymgt.model.entity.Application;
import org.wso2.carbon.apimgt.keymgt.model.entity.ApplicationKeyMapping;
import org.wso2.carbon.apimgt.keymgt.model.entity.ApplicationPolicy;
import org.wso2.carbon.apimgt.keymgt.model.entity.Policy;
import org.wso2.carbon.apimgt.keymgt.model.entity.Subscription;
import org.wso2.carbon.apimgt.keymgt.model.entity.Subscriber;
import org.wso2.carbon.apimgt.keymgt.model.entity.SubscriptionPolicy;
import org.wso2.carbon.apimgt.keymgt.model.util.SubscriptionDataStoreUtil;
import org.wso2.carbon.base.MultitenantConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SubscriptionDataStoreImpl implements SubscriptionDataStore {

    private static final Log log = LogFactory.getLog(SubscriptionDataStoreImpl.class);

    // Maps for keeping Subscription related details.
    private Map<String, ApplicationKeyMapping> applicationKeyMappingMap;
    private Map<Integer, Application> applicationMap;
    private Map<String, API> apiMap;
    //    private Map<String, Policy> policyMap;
//    private Map<String, APIPolicy> apiPolicyMap;
    private Map<String, SubscriptionPolicy> subscriptionPolicyMap;
    private Map<String, ApplicationPolicy> appPolicyMap;
    private Map<String, Subscription> subscriptionMap;
    private Map<String, Subscriber> subscriberMap;
    public static final int LOADING_POOL_SIZE = 7;
    private int tenantId = MultitenantConstants.SUPER_TENANT_ID;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(LOADING_POOL_SIZE);

    public SubscriptionDataStoreImpl(int tenantId) {

        this.tenantId = tenantId;
        initializeStore();
    }

    public SubscriptionDataStoreImpl() {

        initializeStore();
    }

    private void initializeStore() {

        this.applicationKeyMappingMap = new ConcurrentHashMap<>();
        this.applicationMap = new ConcurrentHashMap<>();
        this.apiMap = new ConcurrentHashMap<>();
        this.subscriptionPolicyMap = new ConcurrentHashMap<>();
        this.appPolicyMap = new ConcurrentHashMap<>();
        this.subscriptionMap = new ConcurrentHashMap<>();
        this.subscriberMap = new ConcurrentHashMap<>();
        initializeLoadingTasks();
    }

    @Override
    public SubscriptionDataStore getInstance(int tenantId) {

        return new SubscriptionDataStoreImpl(tenantId);
    }

    @Override
    public Application getApplicationById(int appId) {

        return applicationMap.get(appId);
    }

    @Override
    public ApplicationKeyMapping getKeyMappingByKey(String key) {

        return applicationKeyMappingMap.get(key);
    }

    @Override
    public API getApiByContextAndVersion(String context, String version) {

        return null;
    }

    @Override
    public SubscriptionPolicy getSubscriptionPolicyByName(String policyName, int tenantId) {

        String key = InMemorySubscriptionValidationConstants.POLICY_TYPE.SUBSCRIPTION +
                SubscriptionDataStoreUtil.getPolicyCacheKey(policyName, tenantId);
        return subscriptionPolicyMap.get(key);
    }

    @Override
    public ApplicationPolicy getApplicationPolicyByName(String policyName, int tenantId) {

        String key = InMemorySubscriptionValidationConstants.POLICY_TYPE.APPLICATION +
                SubscriptionDataStoreUtil.getPolicyCacheKey(policyName, tenantId);
        return appPolicyMap.get(key);
    }

    @Override
    public Subscription getSubscriptionById(int appId, int apiId) {

        return subscriptionMap.get(SubscriptionDataStoreUtil.getSubscriptionCacheKey(appId, apiId));
    }

    public void initializeLoadingTasks() {

        Runnable apiTask = new PopulateTask<String, API>(apiMap,
                () -> {
                    try {
                        log.debug("Calling loadAllApis. ");
                        return new SubscriptionDataLoaderImpl().loadAllApis(tenantId);
                    } catch (APIManagementException e) {
                        log.error("Exception while loading APIs " + e);
                    }
                    return null;
                });

        executorService.schedule(apiTask, 0, TimeUnit.SECONDS);

        Runnable subscriptionLoadingTask = new PopulateTask<String, Subscription>(subscriptionMap,
                () -> {
                    try {
                        log.debug("Calling loadAllSubscriptions.");
                        return new SubscriptionDataLoaderImpl().loadAllSubscriptions(tenantId);
                    } catch (APIManagementException e) {
                        log.error("Exception while loading Subscriptions " + e);
                    }
                    return null;
                });

        executorService.schedule(subscriptionLoadingTask, 0, TimeUnit.SECONDS);

        Runnable applicationLoadingTask = new PopulateTask<Integer, Application>(applicationMap,
                () -> {
                    try {
                        log.debug("Calling loadAllApplications.");
                        return new SubscriptionDataLoaderImpl().loadAllApplications(tenantId);
                    } catch (APIManagementException e) {
                        log.error("Exception while loading Applications " + e);
                    }
                    return null;
                });

        executorService.schedule(applicationLoadingTask, 0, TimeUnit.SECONDS);

        Runnable keyMappingsTask =
                new PopulateTask<String, ApplicationKeyMapping>(applicationKeyMappingMap,
                        () -> {
                            try {
                                log.debug("Calling loadAllKeyMappings.");
                                return new SubscriptionDataLoaderImpl().loadAllKeyMappings(tenantId);
                            } catch (APIManagementException e) {
                                log.error("Exception while loading ApplicationKeyMapping " + e);
                            }
                            return null;
                        });

        executorService.schedule(keyMappingsTask, 0, TimeUnit.SECONDS);

        Runnable subPolicyLoadingTask =
                new PopulateTask<String, SubscriptionPolicy>(subscriptionPolicyMap,
                        () -> {
                            try {
                                log.debug("Calling loadAllSubscriptionPolicies.");
                                return new SubscriptionDataLoaderImpl().loadAllSubscriptionPolicies(tenantId);
                            } catch (APIManagementException e) {
                                log.error("Exception while loading Subscription Policies " + e);
                            }
                            return null;
                        });

        executorService.schedule(subPolicyLoadingTask, 0, TimeUnit.SECONDS);

        Runnable appPolicyLoadingTask =
                new PopulateTask<String, ApplicationPolicy>(appPolicyMap,
                        () -> {
                            try {
                                log.debug("Calling loadAllAppPolicies.");
                                return new SubscriptionDataLoaderImpl().loadAllAppPolicies(tenantId);
                            } catch (APIManagementException e) {
                                log.error("Exception while loading Application Policies " + e);
                            }
                            return null;
                        });

        executorService.schedule(appPolicyLoadingTask, 0, TimeUnit.SECONDS);
//todo load subscribers
//        Runnable apiPolicyLoadingTask = //todo validate from local cache
//                new PeriodicPopulateTask<String, APIPolicy>(apiPolicyMap,
//                        () -> {
//                            try {
//                                log.debug("Calling loadAllApiPolicies.");
//                                return dataLoader.loadAllApiPolicies();
//                            } catch (APIManagementException e) {
//                                log.error("Exception while loading Api Policies");
//                            }
//                            return null;
//                        });
//
//        executorService.scheduleAtFixedRate(apiPolicyLoadingTask, 0,
//                this.mapBasedSubscriptionStoreConfig.getPolicyLoadingFrequency(), TimeUnit.SECONDS);

    }

    private <T extends Policy> T getPolicy(String policyName, int tenantId,
                                           Map<String, T> policyMap) {

        return policyMap.get(SubscriptionDataStoreUtil.getPolicyCacheKey(policyName, tenantId));
    }

    private class PopulateTask<K, V extends CacheableEntity<K>> implements Runnable {

        private Map<K, V> entityMap;
        private Supplier<List<V>> supplier;

        PopulateTask(Map<K, V> entityMap, Supplier<List<V>> supplier) {

            this.entityMap = entityMap;
            this.supplier = supplier;
        }

        public void run() {

            List<V> list = supplier.get();
            HashMap<K, V> tempMap = new HashMap<>();

            if (list != null) {
                for (V v : list) {
                    tempMap.put(v.getCacheKey(), v);
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Adding entry Key : %s Value : %s", v.getCacheKey(), v));
                    }

                    if (!tempMap.isEmpty()) {
                        entityMap.clear();
                        entityMap.putAll(tempMap);
                    }
                }

            } else {
                if (log.isDebugEnabled()) {
                    log.debug("List is null for " + supplier.getClass());
                }
            }
        }
    }
}
