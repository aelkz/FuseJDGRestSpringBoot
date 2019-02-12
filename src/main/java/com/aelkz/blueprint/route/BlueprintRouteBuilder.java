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

import com.aelkz.blueprint.processor.exception.RestBusinessExceptionProcessor;
import com.aelkz.blueprint.processor.infinispan.*;
import com.aelkz.blueprint.processor.rest.RestEndpointProcessor;
import com.aelkz.blueprint.repository.BeneficiarioRepository;
import com.aelkz.blueprint.exception.BusinessException;
import com.aelkz.blueprint.processor.ClearHeaderProcessor;
import com.aelkz.blueprint.processor.HeaderDebugProcessor;
import com.aelkz.blueprint.service.BeneficiarioService;
import com.aelkz.blueprint.service.dto.BeneficiarioDTO;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.net.ConnectException;

@Component
public class BlueprintRouteBuilder extends RouteBuilder {

    @Autowired
    private Environment env;

    @Value("${api.version}")
    private String apiVersion;

    @Autowired
    private BeneficiarioService beneficiarioService;

    @Autowired
    private BeneficiarioRepository beneficiarioRepository;

    @Autowired
    private RestEndpointProcessor restEndpointProcessor;

    @Autowired
    private RestEndpointDatabaseProcessor restEndpointDatabaseProcessor;

    @Autowired
    private KeyValueBuilderProcessor keyValueBuilderProcessor;

    @Autowired
    private KeyValueParserProcessor keyValueParserProcessor;

    @Autowired
    private RestEndpointMapResponseProcessor restEndpointMapResponseProcessor;

    @Autowired
    private ExtractCacheKeyProcessor extractCacheKeyProcessor;

    @Autowired
    private HeaderDebugProcessor headerDebugProcessor;

    @Autowired
    private ClearHeaderProcessor clearHeaderProcessor;

    @Override
    public void configure() throws Exception {

        onException(BusinessException.class)
                .handled(true)
                .process(new RestBusinessExceptionProcessor())
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.ERROR, "Erro ao validar regras negociais.");

        onException(ConnectException.class)
                .handled(true)
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.ERROR, "Erro ao conectar no JBoss Data Grid.");

        // /--------------------------------------------------\
        // | Expose route w/ REST endpoint                    |
        // \--------------------------------------------------/

        restConfiguration()
                //.contextPath("/api/v" + apiVersion) => not working as expected.
                .apiContextPath("/api-docs")
                .apiProperty("api.title", "Beneficiarios API")
                .apiProperty("api.version", apiVersion)
                //.apiProperty("cors", "true")
                .dataFormatProperty("prettyPrint", "true")
                .port(env.getProperty("server.port", "8080"))
                .bindingMode(RestBindingMode.json);

        rest("/").id("beneficiarios-api")
              .consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON)
                .get(RestResourcesEnum.GET_BENEFICIARIOS.getResourcePath())
                    .description(RestResourcesEnum.GET_BENEFICIARIOS.getDescription())
                    .route()
                    .routeId(RestResourcesEnum.GET_BENEFICIARIOS.getRouteId())
                    .process(restEndpointProcessor)
                    .endRest()
                .get(RestResourcesEnum.GET_BENEFICIARIO.getResourcePath())
                    .description(RestResourcesEnum.GET_BENEFICIARIO.getDescription())
                    .route()
                    .routeId(RestResourcesEnum.GET_BENEFICIARIO.getRouteId())
                    .process(restEndpointProcessor)
                    .endRest()
                // ----- START-INFINISPAN API ----- \\
                .get(RestResourcesEnum.GET_BENEFICIARIO_CACHE.getResourcePath())
                    .description(RestResourcesEnum.GET_BENEFICIARIO_CACHE.getDescription())
                    .route()
                    .routeId(RestResourcesEnum.GET_BENEFICIARIO_CACHE.getRouteId())
                    .to("direct:api")
                    .endRest()
                // ----- END-INFINISPAN API ----- \\
                .post(RestResourcesEnum.POST_BENEFICIARIOS.getResourcePath())
                    .type(BeneficiarioDTO.class)
                    .description(RestResourcesEnum.POST_BENEFICIARIOS.getDescription())
                    .route()
                    .routeId(RestResourcesEnum.POST_BENEFICIARIOS.getRouteId())
                    .process(restEndpointProcessor)
                    .endRest();

        // /--------------------------------------------------\
        // | Configure and expose infinispan routes           |
        // \--------------------------------------------------/

        /**
         * streamCaching: This ensures that Camel will deal with large streaming payloads in a manner where Camel can
         * automatic spool big streams to temporary disk space to avoid taking up memory. The stream caching in Apache
         * Camel is fully configurable and you can setup thresholds that are based on payload size, memory left in the
         * JVM etc to trigger when to spool to disk. However the default settings are often sufficient.
         */

        from("direct:api")
                .id("direct-api-route")
                .description("direct-api-route")
                .streamCaching()
                .setBody(simple("hello"))
                .to("direct:database");

        from("direct:database")
                .streamCaching()
                .log("preparing to call infinispan with handle='${header.handle}'")
                
                .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.GET)
                .setHeader(InfinispanConstants.KEY).constant("123")
                
                .to("infinispan?cacheContainer=#remoteCacheContainer")
                .to("log:org.apache.camel.component.infinispan?level=INFO&showAll=true&multiline=true")
                .process(e -> {
                	String value = e.getIn().getBody(String.class);
                	System.out.println(value);
                })

                .choice()
                .when().simple("${body} != null")
                    .log("infinispan entry found!")
                    .process(keyValueParserProcessor)// TODO - must finish process exchange
                    .process(restEndpointMapResponseProcessor)
                .endChoice()
                .otherwise()
                    .log("infinispan entry not found.")
                    .process(restEndpointDatabaseProcessor)
                    .choice()
                    .when(simple("${header.recordFound} == true"))
                        .log(LoggingLevel.INFO, "${header.size} record(s) found.")
                        .process(keyValueBuilderProcessor)
                        .wireTap("direct:cache-put")
                        .process(restEndpointMapResponseProcessor)
                    .endChoice()
                .end();

        /*
         Rota:
         Realiza a inserção no cache utilizando o PUT.
         Logo após o PUT eu tento obter a entry recém adicionada utilizando o GET, porém não retorna nada.
         */

        from("direct:cache-put")
                .id("direct-cache-put-route")
                .description("direct-cache-put-route")
                .streamCaching()
                .split().body().streaming()
                .log("key/value object: ${body}")
                .process(extractCacheKeyProcessor)
                .log("diogo '${header.objKey}'")
                .setHeader(InfinispanConstants.KEY).constant("123")
                .setHeader(InfinispanConstants.VALUE).simple("RAPHAEL")
                .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.PUT)
                //.setHeader(InfinispanConstants.LIFESPAN_TIME).constant(3600L)
                //.setHeader(InfinispanConstants.LIFESPAN_TIME_UNIT).constant(TimeUnit.SECONDS.toString())
                .log(LoggingLevel.INFO, "infinispan PUT operation called.")
                .to("infinispan?cacheContainer=#remoteCacheContainer");
    }

}
