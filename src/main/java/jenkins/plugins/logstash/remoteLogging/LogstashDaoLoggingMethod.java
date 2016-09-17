/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkins.plugins.logstash.remoteLogging;

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsConfig;
import hudson.Launcher;
import hudson.Proc;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.RemoteInputStream;
import hudson.tasks.BuildWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.logging.LoggingMethod;
import jenkins.plugins.logstash.LogstashBuildWrapper;
import jenkins.plugins.logstash.LogstashOutputStream;
import jenkins.plugins.logstash.LogstashWriter;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Perform logging to {@link LogstashIndexerDao}.
 *
 * @author Oleg Nenashev
 */
public class LogstashDaoLoggingMethod extends LoggingMethod {

    @Override
    public Launcher decorateLauncher(Launcher l, Run run, Node node) {
        if (node instanceof Jenkins) {
            return new LocalLogstashLoggingLauncher(l);
        } else {
            return new RemoteLogstashLoggingLauncher(l, run);
        }
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
        if (build instanceof AbstractBuild) {
            return new ConsoleLogFilter() {
                @Override
                public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) throws IOException, InterruptedException {
                    return _decorateLogger(build, logger);
                }
            };
        }
        return null;
    }

    // Method to encapsulate calls for unit-testing
    LogstashWriter getLogStashWriter(Run run, OutputStream errorStream) {
        return new LogstashWriter(run, errorStream);
    }
    
    private OutputStream _decorateLogger(AbstractBuild build, OutputStream logger) {
        LogstashWriter logstash = getLogStashWriter(build, logger);
        LogstashOutputStream los = new LogstashOutputStream(logger, logstash);
        return los.maskPasswords(getVarPasswordPairs(build));
    }

    @Restricted(NoExternalUse.class)
    public static List<MaskPasswordsBuildWrapper.VarPasswordPair> getVarPasswordPairs(Run build) {
        List<MaskPasswordsBuildWrapper.VarPasswordPair> allPasswordPairs = new ArrayList<MaskPasswordsBuildWrapper.VarPasswordPair>();
        Job job = build.getParent();
        if (job instanceof BuildableItemWithBuildWrappers) {
            BuildableItemWithBuildWrappers project = (BuildableItemWithBuildWrappers) job;
            for (BuildWrapper wrapper : project.getBuildWrappersList()) {
                if (wrapper instanceof MaskPasswordsBuildWrapper) {
                    MaskPasswordsBuildWrapper maskPasswordsWrapper = (MaskPasswordsBuildWrapper) wrapper;
                    List<MaskPasswordsBuildWrapper.VarPasswordPair> jobPasswordPairs = maskPasswordsWrapper.getVarPasswordPairs();
                    if (jobPasswordPairs != null) {
                        allPasswordPairs.addAll(jobPasswordPairs);
                    }

                    MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
                    List<MaskPasswordsBuildWrapper.VarPasswordPair> globalPasswordPairs = config.getGlobalVarPasswordPairs();
                    if (globalPasswordPairs != null) {
                        allPasswordPairs.addAll(globalPasswordPairs);
                    }

                    return allPasswordPairs;
                }
            }
        }
        return allPasswordPairs;
    }

}
