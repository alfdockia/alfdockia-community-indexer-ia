/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Comprueba la clave administrativa cuando esta configurada.
 */
@Component
public class IndexerAdminSecurity {

    private final String apiKey;
    private final String apiKeyHeader;

    public IndexerAdminSecurity(
            @Value("${indexer.admin.api-key:}") String apiKey,
            @Value("${indexer.admin.api-key-header:X-Indexer-Admin-Key}") String apiKeyHeader
    ) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiKeyHeader = hasText(apiKeyHeader) ? apiKeyHeader : "X-Indexer-Admin-Key";
    }

    public void requireAllowed(HttpServletRequest request) {
        if (!hasText(apiKey)) {
            return;
        }
        String received = request.getHeader(apiKeyHeader);
        if (!constantTimeEquals(apiKey, received)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave administrativa no valida");
        }
    }

    private boolean constantTimeEquals(String expected, String received) {
        if (received == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
