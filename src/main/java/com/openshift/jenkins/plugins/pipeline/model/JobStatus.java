package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.internal.restclient.model.ModelNodeAdapter;
import com.openshift.internal.util.JBossDmrExtentions;
import org.jboss.dmr.ModelNode;

import java.util.Map;

public class JobStatus extends ModelNodeAdapter implements IJobStatus {
    private static final String JOB_STATUS_SUCCEEDED = "succeeded";
    private static final String JOB_STATUS_FAILED = "failed";

    protected JobStatus(ModelNode node, Map<String, String[]> propertyKeys) {
        super(node, propertyKeys);
    }

    @Override
    public int getFailed() {
        return JBossDmrExtentions.asInt(this.getNode(), this.getPropertyKeys(), JOB_STATUS_FAILED);
    }

    @Override
    public int getSucceeded() {
        return JBossDmrExtentions.asInt(this.getNode(), this.getPropertyKeys(), JOB_STATUS_SUCCEEDED);
    }
}
