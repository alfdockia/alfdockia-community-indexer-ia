/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.admin;

import alfdockia.community.indexer.ia.service.EventIndexingService;
import alfdockia.community.indexer.ia.service.ReindexSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone la reindexacion administrativa de nodos, carpetas y sites.
 */
@RestController
public class ReindexAdminController {

    private final EventIndexingService indexingService;
    private final IndexerAdminSecurity security;

    public ReindexAdminController(EventIndexingService indexingService, IndexerAdminSecurity security) {
        this.indexingService = indexingService;
        this.security = security;
    }

    @PostMapping("/admin/reindex/nodes/{nodeId}")
    public ReindexSummary reindexNode(@PathVariable String nodeId, HttpServletRequest request) {
        security.requireAllowed(request);
        return indexingService.reindexNode(nodeId);
    }

    @PostMapping("/admin/reindex/folders/{nodeId}")
    public ReindexSummary reindexFolder(@PathVariable String nodeId, HttpServletRequest request) {
        security.requireAllowed(request);
        return indexingService.reindexFolder(nodeId);
    }

    @PostMapping("/admin/reindex/sites/{siteId}")
    public ReindexSummary reindexSite(@PathVariable String siteId, HttpServletRequest request) {
        security.requireAllowed(request);
        return indexingService.reindexSite(siteId);
    }
}
