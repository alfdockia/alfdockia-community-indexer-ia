package alfdockia.community.indexer.ia.event;

import alfdockia.community.indexer.ia.service.NodeEventCoalescer;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.springframework.stereotype.Component;

@Component
public class ContentCreatedHandler implements OnNodeCreatedEventHandler {

    private final NodeEventCoalescer eventCoalescer;

    public ContentCreatedHandler(NodeEventCoalescer eventCoalescer) {
        this.eventCoalescer = eventCoalescer;
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        eventCoalescer.upsert(event);
    }
}
