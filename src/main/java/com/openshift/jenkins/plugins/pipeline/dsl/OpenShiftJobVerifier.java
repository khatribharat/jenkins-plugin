package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftJobVerifier;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collection;
import java.util.Map;

public class OpenShiftJobVerifier extends TimedOpenShiftBaseStep implements
        IOpenShiftJobVerifier {
    protected final String job;
    protected String showJobLogs;

    @DataBoundConstructor
    public OpenShiftJobVerifier(String job) { this.job = job != null ? job.trim() : null; }

    public String getShowJobLogs() {
        return showJobLogs;
    }

    @DataBoundSetter
    public void setShowJobLogs(String showJobLogs) {
        this.showJobLogs = showJobLogs != null ? showJobLogs.trim()
                : null;
    }

    public String getJobName() {
        return job;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl
            implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            super(OpenShiftJobVerifierExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "openshiftVerifyJob";
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            if (!arguments.containsKey("job"))
                throw new IllegalArgumentException(
                        "need to specify job");
            Object job = arguments.get("job");
            if (job == null || job.toString().trim().length() == 0)
                throw new IllegalArgumentException(
                        "need to specify job");
            OpenShiftJobVerifier step = new OpenShiftJobVerifier(
                    job.toString());
            if (arguments.containsKey("showBuildLogs")) {
                Object showBuildLogs = arguments.get("showBuildLogs");
                if (showBuildLogs != null) {
                    step.setShowJobLogs(showBuildLogs.toString());
                }
            }

            ParamVerify.updateTimedDSLBaseStep(arguments, step);
            return step;
        }
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        return true;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions(
            AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return null;
    }

}
