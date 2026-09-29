/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.service;

/**
 * Resume los contadores y el resultado de una reindexacion administrativa.
 */
public record ReindexSummary(
        String scope,
        String target,
        int total,
        int indexed,
        int skipped,
        int deleted,
        String message
) {
}
