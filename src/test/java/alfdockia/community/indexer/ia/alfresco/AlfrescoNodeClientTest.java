/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.alfresco;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AlfrescoNodeClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesContentNodeWithPathPropertiesAndAcl() throws Exception {
        AlfrescoNodeClient client = client(null);

        JsonNode entry = objectMapper.readTree("""
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "invoice.pdf",
                  "nodeType": "acme:invoice",
                  "isFile": true,
                  "isFolder": false,
                  "createdAt": "2026-08-28T10:00:00.000+0000",
                  "modifiedAt": "2026-08-28T10:01:00.000+0000",
                  "createdByUser": { "id": "admin", "displayName": "Administrator" },
                  "modifiedByUser": { "id": "admin", "displayName": "Administrator" },
                  "parentId": "parent-id",
                  "aspectNames": ["cm:titled", "cm:auditable"],
                  "properties": {
                    "cm:name": "invoice.pdf",
                    "cm:title": "Invoice"
                  },
                  "content": {
                    "mimeType": "application/pdf",
                    "encoding": "UTF-8",
                    "sizeInBytes": 42
                  },
                  "path": {
                    "elements": [
                      { "id": "company-home", "name": "Company Home" },
                      { "id": "sites", "name": "Sites" },
                      { "id": "finance", "name": "finance" }
                    ]
                  },
                  "permissions": {
                    "inherited": [
                      { "authorityId": "GROUP_finance", "name": "Consumer", "accessStatus": "ALLOWED" }
                    ],
                    "locallySet": [
                      { "authorityId": "GROUP_blocked", "name": "Read", "accessStatus": "DENIED" }
                    ]
                  }
                }
                """);

        AlfrescoNodeSnapshot snapshot = client.parse(entry);

        assertThat(snapshot.isContentNode()).isTrue();
        assertThat(snapshot.nodeRef()).isEqualTo("workspace://SpacesStore/11111111-1111-1111-1111-111111111111");
        assertThat(snapshot.site()).isEqualTo("finance");
        assertThat(snapshot.namePath()).containsExactly("Company Home", "Sites", "finance", "invoice.pdf");
        assertThat(snapshot.pathNodeIds()).containsExactly("company-home", "sites", "finance");
        assertThat(snapshot.properties()).containsEntry("cm:title", "Invoice");
        assertThat(snapshot.permissionSet().readers()).containsExactly("GROUP_finance");
        assertThat(snapshot.permissionSet().denied()).containsExactly("GROUP_blocked");
        assertThat(snapshot.permissionSet().aclId()).isNotNull();
    }

    @Test
    void returnsEmptyOnlyWhenNodeIsNotFound() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AlfrescoNodeClient client = client(restTemplate);

        server.expect(requestTo(containsString("/nodes/missing-node")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetch("missing-node")).isEmpty();
        server.verify();
    }

    @Test
    void throwsWhenAlfrescoFetchFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AlfrescoNodeClient client = client(restTemplate);

        server.expect(requestTo(containsString("/nodes/error-node")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetch("error-node"))
                .isInstanceOf(AlfrescoNodeFetchException.class);
        server.verify();
    }

    private AlfrescoNodeClient client(RestTemplate restTemplate) {
        return new AlfrescoNodeClient(
                restTemplate,
                objectMapper,
                "http://alfresco:8080",
                "/alfresco/api/-default-/public/alfresco/versions/1",
                "path,permissions,properties,aspectNames",
                "admin",
                "admin"
        );
    }
}
