/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aelkz.blueprint.configuration.infinispan;

import java.util.Objects;
import org.apache.camel.component.infinispan.processor.idempotent.InfinispanIdempotentRepository;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
// not import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Configuration
@ConfigurationProperties(prefix = "infinispan")
//@Component
public class InfinispanAutoConfiguration {

    private Logger logger = LoggerFactory.getLogger(getClass());

    // The name of the Infinispan service.
    private String service = "datagrid-app-hotrod";

    // The name of the Infinispan cache.
    private String cacheName = "default";

    // Defines a bean named 'remoteCacheContainer' that points to the remote Infinispan cluster.
    @Bean(initMethod = "start", destroyMethod = "stop")
    public BasicCacheContainer remoteCacheContainer(Environment environment) {

        String serviceBaseName = service.toUpperCase().replace("-", "_");

        String host = environment.getProperty(serviceBaseName + "_SERVICE_HOST") == null ?
                "localhost" : environment.getProperty(serviceBaseName + "_SERVICE_HOST");

        String port = environment.getProperty(serviceBaseName + "_SERVICE_PORT") == null ?
                "11222" : environment.getProperty(serviceBaseName + "_SERVICE_PORT");

        String maxRetriesStr = environment.getProperty(serviceBaseName + "_SERVICE_RETRIES") == null ?
                "10" : environment.getProperty(serviceBaseName + "_SERVICE_RETRIES");

        Integer maxRetries = 0;

        try {
            maxRetries = Integer.valueOf(maxRetriesStr);
        }catch (NumberFormatException ex) {
            logger.error("Error trying to get infinispan maxRetries value.");
        }

        Objects.requireNonNull(host, "Infinispan service host not found in the environment");
        Objects.requireNonNull(port, "Infinispan service port not found in the environment");

        String hostPort = host + ":" + port;
        logger.info("Connecting to the Infinispan service at {}", hostPort);

        ConfigurationBuilder builder = new ConfigurationBuilder()
                .forceReturnValues(true)
                .addServers(hostPort)
                .maxRetries(maxRetries)
                .security()
                .connectionTimeout(3000)
                ;

        // new RemoteCacheManagerFactory().getRemoteCacheManager()
        return new RemoteCacheManager(builder.create(), false);
    }

    // Defines a Camel idempotent repository based on the Infinispan cache container.
    @Bean
    public InfinispanIdempotentRepository infinispanRepository(BasicCacheContainer cacheContainer) {
        return InfinispanIdempotentRepository.infinispanIdempotentRepository(cacheContainer, cacheName);
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

}