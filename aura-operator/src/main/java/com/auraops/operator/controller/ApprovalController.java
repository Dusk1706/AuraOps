package com.auraops.operator.controller;

import com.auraops.operator.crd.HealerPolicy;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);
    private final KubernetesClient kubernetesClient;

    public ApprovalController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = Objects.requireNonNull(kubernetesClient);
    }

    @PostMapping("/{namespace}/{name}/approve")
    public ResponseEntity<Void> approve(@PathVariable String namespace, @PathVariable String name) {
        log.info("Received approval request for policy {} in namespace {}", name, namespace);
        
        HealerPolicy policy = kubernetesClient.resources(HealerPolicy.class)
            .inNamespace(namespace)
            .withName(name)
            .get();

        if (policy == null) {
            return ResponseEntity.notFound().build();
        }

        if (policy.getStatus() == null) {
            return ResponseEntity.badRequest().build();
        }

        policy.getStatus().setApproved(true);
        
        kubernetesClient.resources(HealerPolicy.class)
            .inNamespace(namespace)
            .withName(name)
            .patchStatus(policy);

        log.info("Policy {} approved successfully", name);
        return ResponseEntity.ok().build();
    }
}
