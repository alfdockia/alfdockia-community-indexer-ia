package alfdockia.community.indexer.ia.qdrant;

import alfdockia.community.indexer.ia.alfresco.AlfrescoDictionaryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(20)
public class DictionaryIndexSynchronizer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DictionaryIndexSynchronizer.class);

    private final AlfrescoDictionaryClient dictionaryClient;
    private final QdrantClient qdrantClient;
    private final boolean enabled;
    private final Set<String> includedNamespaces;
    private final Set<String> excludedNamespaces;
    private volatile PayloadIndexRegistry registry = PayloadIndexRegistry.from(java.util.List.of(), Set.of(), Set.of());
    private volatile Instant lastSynchronizedAt;

    public DictionaryIndexSynchronizer(
            AlfrescoDictionaryClient dictionaryClient,
            QdrantClient qdrantClient,
            @Value("${qdrant.payload.dynamic-indexes.enabled:true}") boolean enabled,
            @Value("${qdrant.payload.dynamic-indexes.include-namespaces:}") String includeNamespaces,
            @Value("${qdrant.payload.dynamic-indexes.exclude-namespaces:sys,rn,ver,act}") String excludeNamespaces
    ) {
        this.dictionaryClient = dictionaryClient;
        this.qdrantClient = qdrantClient;
        this.enabled = enabled;
        this.includedNamespaces = csv(includeNamespaces);
        this.excludedNamespaces = csv(excludeNamespaces);
    }

    @Override
    public void run(ApplicationArguments args) {
        synchronize();
    }

    @Scheduled(fixedDelayString = "${qdrant.payload.dynamic-indexes.refresh-ms:3600000}")
    public PayloadIndexRegistry synchronize() {
        if (!enabled) {
            return registry;
        }
        PayloadIndexRegistry next = PayloadIndexRegistry.from(
                dictionaryClient.fetchProperties(), includedNamespaces, excludedNamespaces);
        next.indexes().forEach(qdrantClient::createPayloadIndex);
        registry = next;
        lastSynchronizedAt = Instant.now();
        LOG.info("Synchronized {} dynamic Qdrant payload indexes", next.indexes().size());
        return next;
    }

    public PayloadIndexRegistry registry() {
        return registry;
    }

    public Instant lastSynchronizedAt() {
        return lastSynchronizedAt;
    }

    private Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
