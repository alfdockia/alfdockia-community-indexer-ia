/*
 * Copyright (c) 2026 AIgen Technologies S.L.
 *
 * La propiedad intelectual de este modulo pertenece a AIgen Technologies S.L.
 * Consulte el archivo LICENSE en la raiz del repositorio para conocer los
 * terminos de licencia aplicables.
 */

package alfdockia.community.indexer.ia.service;

import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Consolida eventos pendientes por nodo y prioriza borrados y cambios de permisos.
 */
@Component
public class NodeEventCoalescer {

    private static final Logger LOG = LoggerFactory.getLogger(NodeEventCoalescer.class);

    private final EventIndexingService indexingService;
    private final TaskScheduler scheduler;
    private final long quietPeriodMillis;
    private final ConcurrentMap<String, PendingMutation> pending = new ConcurrentHashMap<>();

    public NodeEventCoalescer(
            EventIndexingService indexingService,
            TaskScheduler scheduler,
            @Value("${indexer.events.coalesce-ms:2000}") long quietPeriodMillis
    ) {
        this.indexingService = indexingService;
        this.scheduler = scheduler;
        this.quietPeriodMillis = Math.max(0, quietPeriodMillis);
    }

    public void upsert(RepoEvent<DataAttributes<Resource>> event) {
        submit(event, Mutation.UPSERT);
    }

    public void permissionsUpdated(RepoEvent<DataAttributes<Resource>> event) {
        submit(event, Mutation.PERMISSIONS);
    }

    public void delete(RepoEvent<DataAttributes<Resource>> event) {
        submit(event, Mutation.DELETE);
    }

    private void submit(RepoEvent<DataAttributes<Resource>> event, Mutation mutation) {
        String nodeId = nodeId(event);
        if (nodeId == null || nodeId.isBlank()) {
            LOG.debug("Ignoring event {} because its resource is not a node", event == null ? null : event.getId());
            return;
        }

        PendingMutation next = pending.compute(nodeId, (ignored, current) -> merge(current, mutation, event));
        scheduler.schedule(
                () -> drain(nodeId, next),
                Instant.now().plusMillis(quietPeriodMillis)
        );
    }

    private PendingMutation merge(
            PendingMutation current,
            Mutation incoming,
            RepoEvent<DataAttributes<Resource>> event
    ) {
        if (current == null) {
            return new PendingMutation(incoming, event, 1);
        }
        if (incoming.priority >= current.mutation.priority) {
            return new PendingMutation(incoming, event, current.eventCount + 1);
        }
        return new PendingMutation(current.mutation, current.event, current.eventCount + 1);
    }

    private void drain(String nodeId, PendingMutation expected) {
        if (!pending.remove(nodeId, expected)) {
            return;
        }
        LOG.info("Coalesced {} Alfresco events for node {} into one {} operation",
                expected.eventCount, nodeId, expected.mutation);
        switch (expected.mutation) {
            case UPSERT -> indexingService.upsert(expected.event);
            case PERMISSIONS -> indexingService.permissionsUpdated(expected.event);
            case DELETE -> indexingService.delete(expected.event);
        }
    }

    private String nodeId(RepoEvent<DataAttributes<Resource>> event) {
        if (event == null || event.getData() == null || !(event.getData().getResource() instanceof NodeResource node)) {
            return null;
        }
        return node.getId();
    }

    private enum Mutation {
        UPSERT(1),
        PERMISSIONS(2),
        DELETE(3);

        private final int priority;

        Mutation(int priority) {
            this.priority = priority;
        }
    }

    private record PendingMutation(
            Mutation mutation,
            RepoEvent<DataAttributes<Resource>> event,
            int eventCount
    ) {
    }
}
