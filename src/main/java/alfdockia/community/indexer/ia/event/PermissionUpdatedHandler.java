/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.event;

import alfdockia.community.indexer.ia.service.NodeEventCoalescer;
import org.alfresco.event.sdk.handling.handler.OnPermissionUpdatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.springframework.stereotype.Component;

/**
 * Recibe eventos de permisos y los delega al consolidador.
 */
@Component
public class PermissionUpdatedHandler implements OnPermissionUpdatedEventHandler {

    private final NodeEventCoalescer eventCoalescer;

    public PermissionUpdatedHandler(NodeEventCoalescer eventCoalescer) {
        this.eventCoalescer = eventCoalescer;
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        eventCoalescer.permissionsUpdated(event);
    }
}
