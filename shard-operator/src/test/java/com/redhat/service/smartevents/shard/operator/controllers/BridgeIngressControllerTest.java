package com.redhat.service.smartevents.shard.operator.controllers;

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.redhat.service.smartevents.shard.operator.TestSupport;
import com.redhat.service.smartevents.shard.operator.resources.BridgeIngress;
import com.redhat.service.smartevents.shard.operator.resources.ConditionReason;
import com.redhat.service.smartevents.shard.operator.resources.ConditionStatus;
import com.redhat.service.smartevents.shard.operator.resources.ConditionType;
import com.redhat.service.smartevents.shard.operator.utils.KubernetesResourcePatcher;
import com.redhat.service.smartevents.test.resource.KeycloakResource;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithOpenShiftTestServer;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@WithOpenShiftTestServer
@QuarkusTestResource(value = KeycloakResource.class, restrictToAnnotatedClass = true)
public class BridgeIngressControllerTest {

    @Inject
    BridgeIngressController bridgeIngressController;

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    KubernetesResourcePatcher kubernetesResourcePatcher;

    @BeforeEach
    void setup() {
        kubernetesResourcePatcher.cleanUp();
    }

    @Test
    void testCreateNewBridgeIngressWithoutSecrets() {
        // Given
        BridgeIngress bridgeIngress = buildBridgeIngress();

        // When
        UpdateControl<BridgeIngress> updateControl = bridgeIngressController.reconcile(bridgeIngress, null);

        // Then
        assertThat(updateControl.isNoUpdate()).isTrue();
    }

    @Test
    void testCreateNewBridgeIngress() {
        // Given
        BridgeIngress bridgeIngress = buildBridgeIngress();
        deployBridgeIngressSecret(bridgeIngress);

        // When
        UpdateControl<BridgeIngress> updateControl = bridgeIngressController.reconcile(bridgeIngress, null);

        // Then
        assertThat(updateControl.isUpdateStatus()).isTrue();
        assertThat(bridgeIngress.getStatus()).isNotNull();
        assertThat(bridgeIngress.getStatus().isReady()).isFalse();
        assertThat(bridgeIngress.getStatus().getConditionByType(ConditionType.Augmentation)).isPresent().hasValueSatisfying(c -> {
            assertThat(c.getStatus()).isEqualTo(ConditionStatus.False);
        });
        assertThat(bridgeIngress.getStatus().getConditionByType(ConditionType.Ready)).isPresent().hasValueSatisfying(c -> {
            assertThat(c.getStatus()).isEqualTo(ConditionStatus.False);
            assertThat(c.getReason()).isEqualTo(ConditionReason.DeploymentNotAvailable);
        });
    }

    @Test
    void testBridgeIngressDeployment() {
        // Given
        BridgeIngress bridgeIngress = buildBridgeIngress();
        deployBridgeIngressSecret(bridgeIngress);

        // When
        bridgeIngressController.reconcile(bridgeIngress, null);

        // Then
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(bridgeIngress.getMetadata().getNamespace()).withName(bridgeIngress.getMetadata().getName()).get();
        assertThat(deployment).isNotNull();
        assertThat(deployment.getMetadata().getOwnerReferences().size()).isEqualTo(1);
        assertThat(deployment.getMetadata().getLabels()).isNotNull();
        assertThat(deployment.getSpec().getSelector().getMatchLabels().size()).isEqualTo(1);
        assertThat(deployment.getSpec().getTemplate().getMetadata().getLabels()).isNotNull();
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage()).isNotNull();
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName()).isNotNull();
    }

    @Test
    void testBridgeIngressDeployment_deploymentReplicaFailure() throws Exception {
        // Given
        BridgeIngress bridgeIngress = buildBridgeIngress();
        deployBridgeIngressSecret(bridgeIngress);

        // When
        bridgeIngressController.reconcile(bridgeIngress, null);
        Deployment deployment = getDeploymentFor(bridgeIngress);

        // Then
        kubernetesResourcePatcher.patchDeploymentAsReplicaFailed(deployment.getMetadata().getName(), deployment.getMetadata().getNamespace());

        UpdateControl<BridgeIngress> updateControl = bridgeIngressController.reconcile(bridgeIngress, null);
        assertThat(updateControl.isUpdateStatus()).isTrue();
        assertThat(updateControl.getResource().getStatus().getConditionByType(ConditionType.Ready).get().getReason()).isEqualTo(ConditionReason.DeploymentFailed);
        assertThat(updateControl.getResource().getStatus().getConditionByType(ConditionType.Augmentation).get().getStatus()).isEqualTo(ConditionStatus.False);
    }

    @Test
    void testBridgeIngressDeployment_deploymentTimeoutFailure() throws Exception {
        // Given
        BridgeIngress bridgeIngress = buildBridgeIngress();
        deployBridgeIngressSecret(bridgeIngress);

        // When
        bridgeIngressController.reconcile(bridgeIngress, null);
        Deployment deployment = getDeploymentFor(bridgeIngress);

        // Then
        kubernetesResourcePatcher.patchDeploymentAsTimeoutFailed(deployment.getMetadata().getName(), deployment.getMetadata().getNamespace());

        UpdateControl<BridgeIngress> updateControl = bridgeIngressController.reconcile(bridgeIngress, null);
        assertThat(updateControl.isUpdateStatus()).isTrue();
        assertThat(updateControl.getResource().getStatus().getConditionByType(ConditionType.Ready).get().getReason()).isEqualTo(ConditionReason.DeploymentFailed);
        assertThat(updateControl.getResource().getStatus().getConditionByType(ConditionType.Augmentation).get().getStatus()).isEqualTo(ConditionStatus.False);
    }

    private void deployBridgeIngressSecret(BridgeIngress bridgeIngress) {
        Secret secret = new SecretBuilder()
                .withMetadata(
                        new ObjectMetaBuilder()
                                .withNamespace(bridgeIngress.getMetadata().getNamespace())
                                .withName(bridgeIngress.getMetadata().getName())
                                .build())
                .build();
        kubernetesClient
                .secrets()
                .inNamespace(bridgeIngress.getMetadata().getNamespace())
                .withName(bridgeIngress.getMetadata().getName())
                .createOrReplace(secret);
    }

    private Deployment getDeploymentFor(BridgeIngress bridgeIngress) {
        Deployment deployment = kubernetesClient.apps().deployments().inNamespace(bridgeIngress.getMetadata().getNamespace()).withName(bridgeIngress.getMetadata().getName()).get();
        assertThat(deployment).isNotNull();
        return deployment;
    }

    private BridgeIngress buildBridgeIngress() {
        return BridgeIngress.fromBuilder()
                .withBridgeId(TestSupport.BRIDGE_ID)
                .withBridgeName(TestSupport.BRIDGE_NAME)
                .withImageName(TestSupport.INGRESS_IMAGE)
                .withCustomerId(TestSupport.CUSTOMER_ID)
                .withNamespace(KubernetesResourceUtil.sanitizeName(TestSupport.CUSTOMER_ID))
                .build();
    }
}
