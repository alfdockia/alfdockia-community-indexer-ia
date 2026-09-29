package alfdockia.community.indexer.ia.event;

import alfdockia.community.indexer.ia.service.NodeEventCoalescer;
import org.alfresco.event.sdk.handling.handler.OnPermissionUpdatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.springframework.stereotype.Component;

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
