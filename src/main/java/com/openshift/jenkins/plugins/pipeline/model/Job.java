package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.restclient.IClient;
import org.jboss.dmr.ModelNode;

import java.util.Map;

public class Job extends KubernetesResource implements IJob {
    private static final String JOB_COMPLETIONS = "spec.completions";
    private static final String JOB_STATUS = "status";
    private Map<String, String[]> propertyKeys;

    public Job(ModelNode node, IClient client, Map<String, String[]> propertyKeys) {
        super(node, client, propertyKeys);
        this.propertyKeys = propertyKeys;
    }

    public Job(KubernetesResource resource) {
        this(resource.getNode(), resource.getClient(), resource.getPropertyKeys());
    }

    @Override
    public IJobStatus getJobStatus() {
        return new JobStatus(this.get(JOB_STATUS), this.propertyKeys);
    }

    @Override
    public int getCompletions() {
        return this.asInt(JOB_COMPLETIONS);
    }
}
