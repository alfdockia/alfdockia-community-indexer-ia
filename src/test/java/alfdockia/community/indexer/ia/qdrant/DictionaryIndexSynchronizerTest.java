package alfdockia.community.indexer.ia.qdrant;

import alfdockia.community.indexer.ia.alfresco.AlfrescoDictionaryClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DictionaryIndexSynchronizerTest {

    @Test
    void createsIndexesForAllowedDictionaryProperties() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://alfresco:8080/alfresco/s/api/classes?cf=all"))
                .andRespond(withSuccess("""
                        {"classes":[{"name":"acme:contract","properties":[
                          {"name":"acme:amount","dataType":"d:double"},
                          {"name":"sys:node-uuid","dataType":"d:text"}
                        ]}]}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://qdrant:6333/collections/alfresco-content/index?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        AlfrescoDictionaryClient dictionary = new AlfrescoDictionaryClient(
                restTemplate, new ObjectMapper(), "http://alfresco:8080",
                "/alfresco/s/api/classes", "admin", "admin");
        QdrantClient qdrant = new QdrantClient(
                restTemplate, "http://qdrant:6333", "", "alfresco-content", true);
        DictionaryIndexSynchronizer synchronizer = new DictionaryIndexSynchronizer(
                dictionary, qdrant, true, "", "sys,rn,ver,act");

        PayloadIndexRegistry registry = synchronizer.synchronize();

        assertThat(registry.indexes()).containsOnlyKeys("q_61636d653a616d6f756e74");
        server.verify();
    }
}
