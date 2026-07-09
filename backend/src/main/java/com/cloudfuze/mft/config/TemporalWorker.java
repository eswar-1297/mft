package com.cloudfuze.mft.config;

import com.cloudfuze.mft.transfer.workflow.TransferActivities;
import com.cloudfuze.mft.transfer.workflow.TransferWorkflowImpl;
import com.cloudfuze.mft.workflow.temporal.PipelineActivities;
import com.cloudfuze.mft.workflow.temporal.PipelineWorkflowImpl;
import com.cloudfuze.mft.workflow.temporal.ScheduledPipelineWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Starts a Temporal worker that polls the transfer task queue and runs {@link TransferWorkflowImpl}
 * plus the activity implementations. Runs inside the same process here for simplicity; in a larger
 * deployment workers scale out independently of the API.
 */
@Component
public class TemporalWorker {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorker.class);

    private final WorkflowClient client;
    private final TransferActivities transferActivities;
    private final PipelineActivities pipelineActivities;
    private final TemporalProperties props;
    private WorkerFactory factory;

    @org.springframework.beans.factory.annotation.Value("${mft.startup.connect-external:true}")
    private boolean connectExternal;

    public TemporalWorker(WorkflowClient client, TransferActivities transferActivities,
                          PipelineActivities pipelineActivities, TemporalProperties props) {
        this.client = client;
        this.transferActivities = transferActivities;
        this.pipelineActivities = pipelineActivities;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        // Allows the app (and integration tests) to start without a Temporal server reachable.
        if (!connectExternal) {
            log.info("Temporal worker disabled (mft.startup.connect-external=false)");
            return;
        }
        factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(props.getTaskQueue());
        worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class,
                PipelineWorkflowImpl.class, ScheduledPipelineWorkflowImpl.class);
        worker.registerActivitiesImplementations(transferActivities, pipelineActivities);
        factory.start();
        log.info("Temporal worker polling task queue '{}' at {}", props.getTaskQueue(), props.getTarget());
    }

    @PreDestroy
    public void stop() {
        if (factory != null) {
            factory.shutdown();
        }
    }
}
