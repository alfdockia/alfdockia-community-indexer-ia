package alfdockia.community.indexer.ia.service;

import alfdockia.community.indexer.ia.alfresco.AlfrescoNodeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentIndexingPolicyTest {

    private final ContentIndexingPolicy policy = new ContentIndexingPolicy(
            "cm:content", "application/pdf");

    @Test
    void acceptsConfiguredContentTypeAndMimeType() {
        assertThat(policy.allows(snapshot("cm:content", "application/pdf"))).isTrue();
    }

    @Test
    void rejectsOtherTypesAndMimeTypes() {
        assertThat(policy.allows(snapshot("acme:contract", "application/pdf"))).isFalse();
        assertThat(policy.allows(snapshot("cm:content", "text/plain"))).isFalse();
    }

    private AlfrescoNodeSnapshot snapshot(String nodeType, String mimeType) {
        return new AlfrescoNodeSnapshot(
                "node-1", "workspace://SpacesStore/node-1", "document.pdf", nodeType,
                true, false, null, null, Map.of(), Map.of(), "parent", List.of(), Map.of(),
                Map.of("mimeType", mimeType), Map.of(), Map.of(), List.of(), List.of(), null,
                null, Map.of());
    }
}
