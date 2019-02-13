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

package com.aelkz.blueprint.route;

import com.aelkz.blueprint.configuration.infinispan.InfinispanAutoConfiguration;

public enum RouteEnum {

    DIRECT_API( "direct:api", "direct-api-route", "direct-api-route"),
    DIRECT_FETCH_DATA( "direct:fetch-data", "direct-fecth-data", "direct-fecth-data"),
    DIRECT_CACHE_CHECK_RESPONSE("direct:cache-check-response", "cache-check-response", "cache-check-response"),
    DIRECT_DATABASE("direct:database", "database", "database"),
    DIRECT_CACHE_PUT("direct:cache-put", "cache-put", "cache-put"),
    DIRECT_CACHE_ENTRY_FOUND("direct:cache-entry-found", "cache-entry-found", "cache-entry-found"),
    INFINISPAN(InfinispanAutoConfiguration.CAMEL_URI, "infinispan-cache-container", "infinispan-cache-container"),
    INFINISPAN_LOG(InfinispanAutoConfiguration.CAMEL_LOG_URI, "infinispan-log", "infinispan-log"),
    ;

    private String uri;
    private String id;
    private String description;

    RouteEnum(String uri, String id, String description) {
        this.uri = uri;
        this.id = id;
        this.description = description;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
