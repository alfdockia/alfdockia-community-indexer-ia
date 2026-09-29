/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.alfresco;

import alfdockia.community.indexer.ia.qdrant.AlfrescoPropertyDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlfrescoDictionaryClientTest {

    @Test
    void readsArrayAndObjectPropertyDefinitionsFromAllClasses() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://alfresco:8080/alfresco/s/api/classes?cf=all"))
                .andRespond(withSuccess("""
                        {
                          "classes": [
                            {"name":"acme:contract","properties":[
                              {"name":"acme:amount","dataType":"d:double","multiValued":false}
                            ]},
                            {"name":"acme:legal","isAspect":true,"properties":{
                              "acme:signed":{"dataType":"d:boolean","multiValued":false}
                            }}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        AlfrescoDictionaryClient client = new AlfrescoDictionaryClient(
                restTemplate, new ObjectMapper(), "http://alfresco:8080",
                "/alfresco/s/api/classes", "admin", "admin");

        assertThat(client.fetchProperties()).containsExactly(
                new AlfrescoPropertyDefinition("acme:amount", "d:double", false),
                new AlfrescoPropertyDefinition("acme:signed", "d:boolean", false)
        );
        server.verify();
    }
}
