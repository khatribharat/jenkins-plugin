package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.restclient.model.IResource;

public interface IJob extends IResource {
    IJobStatus getJobStatus();
    int getCompletions();
}
