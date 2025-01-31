package com.redhat.service.smartevents.manager;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.service.smartevents.manager.providers.ResourceNamesProvider;
import com.redhat.service.smartevents.processor.actions.ActionService;

@ApplicationScoped
public class ActionServiceImpl implements ActionService {

    @Inject
    BridgesService bridgesService;

    @Inject
    ResourceNamesProvider resourceNamesProvider;

    @Override
    public String getBridgeEndpoint(String bridgeId, String customerId) {
        return bridgesService.getReadyBridge(bridgeId, customerId).getEndpoint();
    }

    @Override
    public String getConnectorTopicName(String processorId) {
        return resourceNamesProvider.getProcessorTopicName(processorId);
    }
}
