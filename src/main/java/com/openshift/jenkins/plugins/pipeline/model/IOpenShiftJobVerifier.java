package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.IStoppable;
import com.openshift.restclient.capability.resources.IPodLogRetrievalAsync;
import com.openshift.restclient.model.IPod;
import hudson.Launcher;
import hudson.model.TaskListener;
import org.jboss.dmr.ModelNode;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public interface IOpenShiftJobVerifier extends ITimedOpenShiftPlugin {

    String DISPLAY_NAME = "Verify OpenShift Job";
    String API_VERSION_JOB = "batch/v1";
    // TODO(khatri): Move these to MessageConstants.java
    String EXIT_JOB_NO_JOB_OBJ = "\n\nExiting \""
            + DISPLAY_NAME
            + "\" unsuccessfully; the job \"%s\" could not be read.";
    String START_JOB_PLUGIN = "\n\nStarting the \"%s\" step with job \"%s\" from the project \"%s\".";

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    String getShowJobLogs();

    String getJobName();

    default long getGlobalTimeoutConfiguration() {
        return GlobalConfig.getBuildWait();
    }

    default String getShowJobLogs(Map<String, String> overrides) {
        return getOverride(getShowJobLogs(), overrides);
    }

    default String getJobName(Map<String, String> overrides) {
        return getOverride(getJobName(), overrides);
    }

    default boolean isJobFinished(IJob job) {
        return job.getCompletions() == job.getJobStatus().getSucceeded();
    }

    default boolean isJobRunning(IJob job) {
        return job.getCompletions() != job.getJobStatus().getSucceeded();
    }

    default boolean isActivePod(IPod pod) {
        return true;
    }

    default IJob createJob(KubernetesResource resource) {
        IJob job = null;
        ModelNode node = resource.getNode();
        String version = node.get("apiVersion").asString();
        String kind = node.get("kind").asString();
        if (API_VERSION_JOB.equals(version) && ResourceKind.JOB.equals(kind)) {
            job = new Job(resource);
        }
        return job;
    }

    default List<IPod> getJobPods(IClient client, String jobName,
                                  Map<String, String> overrides) {
        Map<String, String> jobLabels = new HashMap<>();
        jobLabels.put("job-name", jobName);
        return  client.
                list(ResourceKind.POD, getNamespace(overrides), jobLabels);
    }

    default String getPodUID(IPod pod) {
        return pod.getMetadata().get("uid");
    }

    default void streamJobPodLogs(List<PodLogs> podLogsToTrigger,
                                     boolean chatty, TaskListener listener) {
        for (PodLogs podLogs: podLogsToTrigger) {
            IPod pod = podLogs.getPod();
            String podName = pod.getName();
            if (chatty)
                listener.getLogger().println(
                        "\nOpenShiftJobVerifier begin streaming logs for job pod " + podName);
            IStoppable stop = pod
                    .accept(new CapabilityVisitor<IPodLogRetrievalAsync, IStoppable>() {
                        @Override
                        public IStoppable visit(
                                IPodLogRetrievalAsync capability) {
                            return capability
                                    .start(new IPodLogRetrievalAsync.IPodLogListener() {
                                        @Override
                                        public void onOpen() {
                                        }

                                        @Override
                                        public void onMessage(String message) {
                                            listener.getLogger().printf("[%s] %s",
                                                    podName, message);
                                        }

                                        @Override
                                        public void onClose(int code,
                                                            String reason) {
                                        }

                                        @Override
                                        public void onFailure(IOException e) {
                                            // If follow fails, try to
                                            // restart it on the next loop.
                                            podLogs.getFollow().compareAndSet(
                                                    false, true);
                                        }
                                    }, new IPodLogRetrievalAsync.Options()
                                            .follow());
                        }
                    }, null);
            podLogs.setStop(stop);
        }
    }

    // Waiting on a job requires you to check that the number of successful pods
    // for a job is more than or equal to the completions requested for the job.
    //
    // If we need to follow a job's logs, then it gets a bit more complex. Several pods for a job
    // might fail before the job is successful and the job controller would keep spawning new pods
    // to meet the required completions while respecting the required parallelism. We need to track all pods
    // for a job in order to be able to stream job logs. With every tick of the watch loop, we retrieve all pods
    // for a job and  if a pod's UID has not been seen before, we add it to a list of pods being used
    // to stream job logs.
    default void waitOnJob(IClient client, long startTime, String jobName,
           TaskListener listener, Map<String, String> overrides, long wait,
           boolean follow, boolean chatty) throws InterruptedException {
        KubernetesResource resource;
        IJob job;
        IJobStatus jobState;
        String namespace = getNamespace(overrides);
        boolean verbose = Boolean.parseBoolean(getVerbose(overrides));
        Map<String, PodLogs> uidToPodLogs = new HashMap<>();

        while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) < (startTime + wait)) {
            // let's watch the job status
            resource = client.get(API_VERSION_JOB, ResourceKind.JOB, jobName, namespace);
            job = createJob(resource);
            if (job == null) {
                listener.getLogger()
                        .println(
                                String.format(
                                        EXIT_JOB_NO_JOB_OBJ,
                                        jobName));
                break;
            }

            jobState = job.getJobStatus();
            if (verbose) {
                listener.getLogger().printf(
                        "\nOpenShiftJobVerifier job state: completions: %d, succeeded: %d, failed: %d",  job.getCompletions(), jobState.getSucceeded(), jobState.getFailed());
            }

            if (isJobRunning(job) && follow) {
                // find job pods to use for streaming logs
                List<IPod> pods = getJobPods(client, jobName, overrides);
                List<PodLogs> podLogsToTrigger = new ArrayList<>();
                for (IPod pod : pods) {
                    if (chatty)
                        listener.getLogger().println(
                                "\nOpenShiftJobVerifier found pod " + pod.getName());
                    String uid = getPodUID(pod);
                    PodLogs podLogs = uidToPodLogs.get(uid);
                    if (podLogs == null) {
                        podLogs = new PodLogs(pod, false);
                        uidToPodLogs.put(uid, podLogs);
                        podLogsToTrigger.add(podLogs);
                    } else if (podLogs.getFollow().compareAndSet(true, false)) {
                        podLogsToTrigger.add(podLogs);
                    }
                }
                this.streamJobPodLogs(podLogsToTrigger, chatty, listener);
            }

            if (isJobFinished(job)) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO(khatri): Delete the job here
                throw e;
            }
        }
        for (PodLogs podLogs : uidToPodLogs.values()) {
            podLogs.stop();
        }
    }

    default boolean coreLogic(Launcher launcher, TaskListener listener,
                              Map<String, String> overrides) throws InterruptedException {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        String jobName = getJobName(overrides);
        String namespace = getNamespace(overrides);
        listener.getLogger().println(
                String.format(START_JOB_PLUGIN,
                        DISPLAY_NAME, jobName,
                        namespace));

        boolean follow = Boolean.parseBoolean(getShowJobLogs(overrides));
        if (chatty)
            listener.getLogger().printf(
                    "\nOpenShiftJobVerifier logger follow  %b", follow);

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);

        if (client != null) {
            KubernetesResource resource = client.get(API_VERSION_JOB, ResourceKind.JOB, jobName, namespace);
            IJob job = createJob(resource);

            if (job != null) {
                if (chatty) {
                    listener.getLogger().println(
                            "\nOpenShiftJobVerifier job retrieved " + job);
                }

                long startTime = TimeUnit.NANOSECONDS
                        .toMillis(System.nanoTime());
                long wait = getTimeout(listener, chatty, overrides);
                waitOnJob(client, startTime, jobName, listener, overrides,
                        wait, follow, chatty);
                // TODO(khatri): Confirm that this should always return 'true'. This is incorrect, need something like verifyBuild here
                return true;
            } else {
                listener.getLogger()
                        .println(
                                String.format(
                                        EXIT_JOB_NO_JOB_OBJ,
                                        jobName));
                return false;
            }
        } else {
            return false;
        }
    }

    class PodLogs {
        private IPod pod;
        private IStoppable stop;
        private final AtomicBoolean needToFollow;

        public PodLogs(IPod pod, boolean follow) {
            this.pod = pod;
            needToFollow = new AtomicBoolean(follow);
        }

        public void setStop(IStoppable stop) {
            this.stop = stop;
        }

        public void stop() {
            if (stop != null) {
                stop.stop();
            }
        }

        public AtomicBoolean getFollow() {
            return needToFollow;
        }

        public IPod getPod() {
            return pod;
        }
    }
}