/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.qdrant;

import java.lang.reflect.Array;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Construye el texto canonico de busqueda a partir de nombre, propiedades y contenido.
 */
public final class SearchTextBuilder {

    private final int maxLength;

    public SearchTextBuilder(int maxLength) {
        this.maxLength = maxLength;
    }

    public String build(String name, Map<String, Object> properties, String extractedText) {
        Map<String, Object> sortedProperties = new LinkedHashMap<>();
        (properties == null ? Map.<String, Object>of() : properties).entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().startsWith("sys:"))
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> sortedProperties.put(entry.getKey(), entry.getValue()));

        List<String> candidates = new ArrayList<>();
        candidates.add(name);
        sortedProperties.values().forEach(value -> addTextValues(candidates, value));
        candidates.add(extractedText);

        Set<String> seen = new LinkedHashSet<>();
        List<String> parts = new ArrayList<>();
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            if (!normalized.isBlank() && seen.add(normalized.toLowerCase(Locale.ROOT))) {
                parts.add(normalized);
            }
        }
        String result = String.join("\n", parts);
        return maxLength > -1 && result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    private void addTextValues(List<String> values, Object value) {
        if (value instanceof String text) {
            values.add(text);
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addTextValues(values, item));
            return;
        }
        if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                addTextValues(values, Array.get(value, index));
            }
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Z}\\s]+", " ")
                .trim();
    }
}
