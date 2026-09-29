package alfdockia.community.indexer.ia.qdrant;

import alfdockia.community.indexer.ia.util.AlfrescoQualifiedNameTranslator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PayloadIndexRegistry {

    private static final Map<String, String> SCHEMAS = Map.ofEntries(
            Map.entry("d:boolean", "bool"),
            Map.entry("d:int", "integer"),
            Map.entry("d:long", "integer"),
            Map.entry("d:float", "float"),
            Map.entry("d:double", "float"),
            Map.entry("d:decimal", "float"),
            Map.entry("d:date", "datetime"),
            Map.entry("d:datetime", "datetime"),
            Map.entry("d:text", "keyword"),
            Map.entry("d:mltext", "keyword"),
            Map.entry("d:qname", "keyword"),
            Map.entry("d:noderef", "keyword"),
            Map.entry("d:category", "keyword")
    );

    private final Map<String, String> indexes;

    private PayloadIndexRegistry(Map<String, String> indexes) {
        this.indexes = Collections.unmodifiableMap(new LinkedHashMap<>(indexes));
    }

    public static PayloadIndexRegistry from(
            List<AlfrescoPropertyDefinition> definitions,
            Set<String> includedNamespaces,
            Set<String> excludedNamespaces
    ) {
        Map<String, String> indexes = new LinkedHashMap<>();
        definitions.stream()
                .filter(definition -> definition != null && hasText(definition.name()))
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .forEach(definition -> {
                    String namespace = namespace(definition.name());
                    if (excludedNamespaces.contains(namespace)
                            || (!includedNamespaces.isEmpty() && !includedNamespaces.contains(namespace))) {
                        return;
                    }
                    String schema = SCHEMAS.get(normalizeType(definition.dataType()));
                    if (schema != null) {
                        indexes.put(AlfrescoQualifiedNameTranslator.encode(definition.name()), schema);
                    }
                });
        return new PayloadIndexRegistry(indexes);
    }

    public Map<String, String> indexes() {
        return indexes;
    }

    public Optional<String> resolve(String encodedQName) {
        return Optional.ofNullable(indexes.get(encodedQName));
    }

    private static String namespace(String qname) {
        int separator = qname.indexOf(':');
        return separator > 0 ? qname.substring(0, separator) : "";
    }

    private static String normalizeType(String dataType) {
        return dataType == null ? "" : dataType.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
