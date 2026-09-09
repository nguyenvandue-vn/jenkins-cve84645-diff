package hudson.cli;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.EditDistance;
import hudson.util.StreamTaskListener;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.SCMDecisionHandler;
import jenkins.triggers.SCMTriggerItem;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

@Extension
/* loaded from: BuildCommand.class */
public class BuildCommand extends CLICommand {

    @Argument(metaVar = "JOB", usage = "Name of the job to build", required = true)
    public Job<?, ?> job;

    @Option(name = "-f", usage = "Follow the build progress. Like -s only interrupts are not passed through to the build.")
    public boolean follow = false;

    @Option(name = "-s", usage = "Wait until the completion/abortion of the command. Interrupts are passed through to the build.")
    public boolean sync = false;

    @Option(name = "-w", usage = "Wait until the start of the command")
    public boolean wait = false;

    @Option(name = "-c", usage = "Check for SCM changes before starting the build, and if there's no change, exit without doing a build")
    public boolean checkSCM = false;

    @Option(name = "-p", usage = "Specify the build parameters in the key=value format.")
    public Map<String, String> parameters = new HashMap();

    @Option(name = "-v", usage = "Prints out the console output of the build. Use with -s")
    public boolean consoleOutput = false;

    @Option(name = "-r")
    @Deprecated
    public int retryCnt = 10;
    protected static final String BUILD_SCHEDULING_REFUSED = "Build scheduling Refused by an extension, hence not in Queue.";

    public String getShortDescription() {
        return Messages.BuildCommand_ShortDescription();
    }

    protected int run() throws Exception {
        String format;
        this.job.checkPermission(Item.BUILD);
        if (this.sync) {
            this.job.checkPermission(Item.CANCEL);
        }
        ParametersAction a = null;
        if (!this.parameters.isEmpty()) {
            ParametersDefinitionProperty pdp = this.job.getProperty((Class<ParametersDefinitionProperty>) ParametersDefinitionProperty.class);
            if (pdp == null) {
                throw new IllegalStateException(this.job.getFullDisplayName() + " is not parameterized but the -p option was specified.");
            }
            List<ParameterValue> values = new ArrayList<>();
            for (Map.Entry<String, String> e : this.parameters.entrySet()) {
                String name = e.getKey();
                ParameterDefinition pd = pdp.getParameterDefinition(name);
                if (pd == null) {
                    String nearest = EditDistance.findNearest(name, pdp.getParameterDefinitionNames());
                    if (nearest == null) {
                        format = String.format("'%s' is not a valid parameter.", name);
                    } else {
                        format = String.format("'%s' is not a valid parameter. Did you mean %s?", name, nearest);
                    }
                    throw new CmdLineException((CmdLineParser) null, format);
                }
                ParameterValue val = pd.createValue(this, Util.fixNull(e.getValue()));
                if (val == null) {
                    throw new CmdLineException((CmdLineParser) null, String.format("Cannot resolve the value for the parameter '%s'.", name));
                }
                values.add(val);
            }
            for (ParameterDefinition pd2 : pdp.getParameterDefinitions()) {
                if (!this.parameters.containsKey(pd2.getName())) {
                    ParameterValue defaultValue = pd2.getDefaultParameterValue();
                    if (defaultValue == null) {
                        throw new CmdLineException((CmdLineParser) null, String.format("No default value for the parameter '%s'.", pd2.getName()));
                    }
                    values.add(defaultValue);
                }
            }
            a = new ParametersAction(values);
        }
        if (this.checkSCM) {
            SCMTriggerItem item = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(this.job);
            if (item == null) {
                throw new AbortException(this.job.getFullDisplayName() + " has no SCM trigger, but checkSCM was specified");
            }
            if (SCMDecisionHandler.firstShouldPollVeto(this.job) != null || !item.poll(new StreamTaskListener(this.stdout, getClientCharset())).hasChanges()) {
                return 0;
            }
        }
        if (!this.job.isBuildable()) {
            String msg = Messages.BuildCommand_CLICause_CannotBuildUnknownReasons(this.job.getFullDisplayName());
            if ((this.job instanceof ParameterizedJobMixIn.ParameterizedJob) && this.job.isDisabled()) {
                msg = Messages.BuildCommand_CLICause_CannotBuildDisabled(this.job.getFullDisplayName());
            } else if (this.job.isHoldOffBuildUntilSave()) {
                msg = Messages.BuildCommand_CLICause_CannotBuildConfigNotSaved(this.job.getFullDisplayName());
            }
            throw new IllegalStateException(msg);
        }
        Queue.Item item2 = ParameterizedJobMixIn.scheduleBuild2(this.job, 0, new Action[]{new CauseAction(new CLICause(Jenkins.getAuthentication2().getName())), a});
        QueueTaskFuture<? extends Run<?, ?>> f = item2 != null ? item2.m45getFuture() : null;
        if (this.wait || this.sync || this.follow) {
            if (f == null) {
                throw new IllegalStateException(BUILD_SCHEDULING_REFUSED);
            }
            Run<?, ?> b = (Run) f.waitForStart();
            this.stdout.println("Started " + b.getFullDisplayName());
            this.stdout.flush();
            if (this.sync || this.follow) {
                try {
                    if (this.consoleOutput) {
                        int i = 0;
                        while (i <= this.retryCnt) {
                            try {
                                b.writeWholeLogTo(this.stdout);
                                break;
                            } catch (FileNotFoundException | NoSuchFileException e2) {
                                if (i == this.retryCnt) {
                                    AbortException abortException = new AbortException();
                                    abortException.initCause(e2);
                                    throw abortException;
                                }
                                i++;
                                Thread.sleep(100);
                            }
                        }
                    }
                    f.get();
                    this.stdout.println("Completed " + b.getFullDisplayName() + " : " + String.valueOf(b.getResult()));
                    return b.getResult().ordinal;
                } catch (InterruptedException e3) {
                    if (this.follow) {
                        return 125;
                    }
                    if (this.job.hasPermission(Item.CANCEL)) {
                        f.cancel(true);
                    }
                    AbortException abortException2 = new AbortException();
                    abortException2.initCause(e3);
                    throw abortException2;
                }
            }
            return 0;
        }
        return 0;
    }

    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(Messages.BuildCommand_PrintUsageSummary());
    }

    /* loaded from: BuildCommand$CLICause.class */
    public static class CLICause extends Cause.UserIdCause {
        private String startedBy;

        public CLICause() {
            this.startedBy = "unknown";
        }

        public CLICause(String startedBy) {
            this.startedBy = startedBy;
        }

        @Override // hudson.model.Cause.UserIdCause, hudson.model.Cause
        public String getShortDescription() {
            User user = User.getById(this.startedBy, false);
            String userName = user != null ? user.getDisplayName() : this.startedBy;
            return Messages.BuildCommand_CLICause_ShortDescription(userName);
        }

        @Override // hudson.model.Cause.UserIdCause, hudson.model.Cause
        public void print(TaskListener listener) {
            listener.getLogger().println(Messages.BuildCommand_CLICause_ShortDescription(ModelHyperlinkNote.encodeTo("/user/" + this.startedBy, this.startedBy)));
        }

        @Override // hudson.model.Cause.UserIdCause
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass() || !super.equals(o)) {
                return false;
            }
            CLICause cliCause = (CLICause) o;
            return Objects.equals(this.startedBy, cliCause.startedBy);
        }

        @Override // hudson.model.Cause.UserIdCause
        public int hashCode() {
            return Objects.hash(Integer.valueOf(super.hashCode()), this.startedBy);
        }
    }
}
