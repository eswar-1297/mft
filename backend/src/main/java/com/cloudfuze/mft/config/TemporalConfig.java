package com.cloudfuze.mft.config;

import com.cloudfuze.mft.crypto.CryptoVault;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.CodecDataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Connects to the Temporal service and exposes a {@link WorkflowClient}. The worker that actually
 * executes workflows/activities is started by {@link TemporalWorker}.
 */
@Configuration
@EnableConfigurationProperties(TemporalProperties.class)
public class TemporalConfig {

    @Bean(destroyMethod = "shutdown")
    public WorkflowServiceStubs workflowServiceStubs(TemporalProperties props) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(props.getTarget())
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs service, TemporalProperties props,
                                         CryptoVault vault) {
        // Encrypt all payloads at rest in Temporal history with our AES-GCM codec.
        var dataConverter = new CodecDataConverter(
                DefaultDataConverter.newDefaultInstance(),
                List.of(new EncryptionCodec(vault)));
        return WorkflowClient.newInstance(service,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(props.getNamespace())
                        .setDataConverter(dataConverter)
                        .build());
    }
}
