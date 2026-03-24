package com.auraops.operator.infrastructure.kubernetes;

import com.auraops.operator.application.IncidentContext;
import com.auraops.operator.config.HealerProperties;
import com.auraops.operator.infrastructure.observability.LokiLogCollector;
import com.auraops.operator.infrastructure.observability.TempoTraceCollector;
import io.fabric8.kubernetes.api.model.ContainerStateWaitingBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KubernetesTelemetryCollectorTest {

    @Test
    void collect_prefersExternalObservabilitySignalsWhenAvailable() {
        var kubernetesClient = mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        LokiLogCollector lokiLogCollector = mock(LokiLogCollector.class);
        TempoTraceCollector tempoTraceCollector = mock(TempoTraceCollector.class);
        HealerProperties healerProperties = new HealerProperties();
        @SuppressWarnings("unchecked")
        MixedOperation<Pod, PodList, PodResource> pods = mock(MixedOperation.class);
        @SuppressWarnings("unchecked")
        NonNamespaceOperation<Pod, PodList, PodResource> namespacedPods = mock(NonNamespaceOperation.class);
        @SuppressWarnings("unchecked")
        FilterWatchListDeletable<Pod, PodList, PodResource> labeledPods = mock(FilterWatchListDeletable.class);

        Deployment deployment = new DeploymentBuilder()
            .withMetadata(new ObjectMetaBuilder().withName("payments-api").withNamespace("payments").build())
            .withNewSpec()
                .withNewSelector()
                    .addToMatchLabels("app", "payments-api")
                .endSelector()
            .endSpec()
            .build();

        Pod pod = new PodBuilder()
            .withMetadata(new ObjectMetaBuilder().withName("payments-api-123").withNamespace("payments").addToLabels("app", "payments-api").build())
            .withNewStatus()
                .withPhase("Running")
                .withContainerStatuses(new ContainerStatusBuilder()
                    .withName("app")
                    .withRestartCount(2)
                    .withState(new io.fabric8.kubernetes.api.model.ContainerStateBuilder()
                        .withWaiting(new ContainerStateWaitingBuilder().withReason("CrashLoopBackOff").build())
                        .build())
                    .build())
            .endStatus()
            .build();
        PodList podList = new PodListBuilder().withItems(pod).build();

        when(kubernetesClient.pods()).thenReturn(pods);
        when(pods.inNamespace("payments")).thenReturn(namespacedPods);
        when(namespacedPods.withLabels(deployment.getSpec().getSelector().getMatchLabels())).thenReturn(labeledPods);
        when(labeledPods.list()).thenReturn(podList);
        when(lokiLogCollector.collectWithStatus(deployment, healerProperties.getMaxTotalLogLines()))
            .thenReturn(new LokiLogCollector.Result(List.of("loki line"), true, true, null, null));
        when(tempoTraceCollector.collectWithStatus(deployment))
            .thenReturn(new TempoTraceCollector.Result(List.of("traceId=abc123 service=payments-api operation=POST /charge durationMs=845"), true, true, null, null));

        KubernetesTelemetryCollector collector = new KubernetesTelemetryCollector(
            kubernetesClient,
            healerProperties,
            lokiLogCollector,
            tempoTraceCollector
        );

        IncidentContext context = collector.collect(deployment);

        assertThat(context.logs()).containsExactly("loki line");
        assertThat(context.traces()).containsExactly("traceId=abc123 service=payments-api operation=POST /charge durationMs=845");
        assertThat(context.restartCount()).isEqualTo(2);
        assertThat(context.additionalMetrics()).containsEntry("readyReplicas", 0);
    }
}
