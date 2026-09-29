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
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeEventCoalescerTest {

    @Test
    void coalescesAnUploadBurstIntoOneUpsert() {
        EventIndexingService indexing = mock(EventIndexingService.class);
        List<Runnable> scheduled = new ArrayList<>();
        NodeEventCoalescer coalescer = new NodeEventCoalescer(indexing, scheduler(scheduled), 1500);
        RepoEvent<DataAttributes<Resource>> latest = null;

        for (int index = 0; index < 8; index++) {
            latest = event("node-1");
            coalescer.upsert(latest);
        }
        scheduled.forEach(Runnable::run);

        verify(indexing, times(1)).upsert(latest);
    }

    @Test
    void deleteWinsOverOlderOrLateUploadEventsInTheSameBurst() {
        EventIndexingService indexing = mock(EventIndexingService.class);
        List<Runnable> scheduled = new ArrayList<>();
        NodeEventCoalescer coalescer = new NodeEventCoalescer(indexing, scheduler(scheduled), 1500);
        RepoEvent<DataAttributes<Resource>> deleted = event("node-1");

        coalescer.upsert(event("node-1"));
        coalescer.delete(deleted);
        coalescer.upsert(event("node-1"));
        scheduled.forEach(Runnable::run);

        verify(indexing, times(1)).delete(deleted);
        verify(indexing, times(0)).upsert(any());
    }

    @Test
    void permissionRefreshWinsOverOrdinaryUpdatesInTheSameBurst() {
        EventIndexingService indexing = mock(EventIndexingService.class);
        List<Runnable> scheduled = new ArrayList<>();
        NodeEventCoalescer coalescer = new NodeEventCoalescer(indexing, scheduler(scheduled), 1500);
        RepoEvent<DataAttributes<Resource>> permissions = event("node-1");

        coalescer.upsert(event("node-1"));
        coalescer.permissionsUpdated(permissions);
        coalescer.upsert(event("node-1"));
        scheduled.forEach(Runnable::run);

        verify(indexing, times(1)).permissionsUpdated(permissions);
        verify(indexing, times(0)).upsert(any());
    }

    private TaskScheduler scheduler(List<Runnable> tasks) {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        return scheduler;
    }

    @SuppressWarnings("unchecked")
    private RepoEvent<DataAttributes<Resource>> event(String nodeId) {
        RepoEvent<DataAttributes<Resource>> event = mock(RepoEvent.class);
        DataAttributes<Resource> data = mock(DataAttributes.class);
        NodeResource node = mock(NodeResource.class);
        when(node.getId()).thenReturn(nodeId);
        when(data.getResource()).thenReturn(node);
        when(event.getData()).thenReturn(data);
        return event;
    }
}
