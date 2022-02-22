package com.redhat.service.bridge.integration.tests.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.redhat.service.bridge.integration.tests.common.BridgeUtils;

import io.cucumber.java.en.Given;

public class MetricsSteps {

    @Given("^the Manager Metric \'([^\']*)\' count is at least (\\d+)$")
    public void managerMetricCount(String metricName, int minimalValue) {
        testMetricAndCount(BridgeUtils.MANAGER_URL + "/q/metrics", metricName, minimalValue);
    }

    @Given("^the Ingress Metric \'([^\']*)\' count is at least (\\d+)$")
    public void ingressMetricCount(String metricName, int minimalValue) {
        testMetricAndCount(StepsContext.endPoint + "/q/metrics", metricName, minimalValue);
    }

    private void testMetricAndCount(String metricEndpoint, String metricName, int minimalValue) {
        String metrics = BridgeUtils.jsonRequestWithAuth()
                .get(metricEndpoint)
                .then()
                .extract()
                .body()
                .asString();

        assertThat(metrics).contains(metricName);
        metrics.lines()
                .filter(l -> l.contains(metricName))
                .map(m -> m.replace(metricName + " ", ""))
                .mapToDouble(m -> Double.parseDouble(m))
                .forEach(d -> assertThat(d).as("Checking %s value", metricName).isGreaterThanOrEqualTo(minimalValue));
    }
}
