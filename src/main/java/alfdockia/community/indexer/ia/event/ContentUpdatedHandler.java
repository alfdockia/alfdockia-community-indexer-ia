/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.event;

import alfdockia.community.indexer.ia.service.NodeEventCoalescer;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.springframework.stereotype.Component;

/**
 * Recibe eventos de actualizacion de contenido y los delega al consolidador.
 */
@Component
public class ContentUpdatedHandler implements OnNodeUpdatedEventHandler {

    private final NodeEventCoalescer eventCoalescer;

    public ContentUpdatedHandler(NodeEventCoalescer eventCoalescer) {
        this.eventCoalescer = eventCoalescer;
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        eventCoalescer.upsert(event);
    }
}
