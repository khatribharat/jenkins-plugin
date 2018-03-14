package com.openshift.jenkins.plugins.pipeline.dsl;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;

public class OpenShiftJobVerifierExecution extends
        AbstractSynchronousNonBlockingStepExecution<Void> {

    private static final long serialVersionUID = 1L;

    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Launcher launcher;
    @StepContextParameter
    private transient EnvVars envVars;
    @StepContextParameter
    private transient Run<?, ?> runObj;

    @Inject
    private transient OpenShiftJobVerifier step;

    @Override
    protected Void run() throws Exception {
        boolean success = step.doItCore(listener, envVars, runObj, null,
                launcher);
        if (!success) {
            throw new AbortException("\""
                    + step.getDescriptor().getDisplayName() + "\" failed");
        }
        return null;
    }
}
