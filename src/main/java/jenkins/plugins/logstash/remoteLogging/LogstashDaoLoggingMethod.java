/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkins.plugins.logstash.remoteLogging;

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarPasswordPair;
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
import java.io.Serializable;
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
    public ConsoleLogFilter createLoggerDecorator(Run<?, ?> run) {
        return new ConsoleLogFilter() {
            @Override
            public OutputStream decorateLogger(Run run, OutputStream logger) throws IOException, InterruptedException {
                return _decorateLogger(run, logger);
            }
        };
    }

    @Override
    public OutputStreamWrapper provideOutStream(Run run) {
        RemoteLogstashWriter wr = new RemoteLogstashWriter(run, Jenkins.getInstance());
        List<String> passwords = new ArrayList<>();
        for (VarPasswordPair pair : getVarPasswordPairs(run)) {
            passwords.add(pair.getPassword());
        }
        return new LogstashOutputStreamWrapper(wr, passwords, "");
    }

    @Override
    public OutputStreamWrapper provideErrStream(Run run) {
        RemoteLogstashWriter wr = new RemoteLogstashWriter(run, Jenkins.getInstance());
        List<String> passwords = new ArrayList<>();
        for (VarPasswordPair pair : getVarPasswordPairs(run)) {
            passwords.add(pair.getPassword());
        }
        return new LogstashOutputStreamWrapper(wr, passwords, "Error: ");
    }

    // Method to encapsulate calls for unit-testing
    LogstashWriter getLogStashWriter(Run run, OutputStream errorStream) {
        return new LogstashWriter(run, errorStream);
    }
    
    private OutputStream _decorateLogger(Run build, OutputStream logger) {
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

    private static class LogstashOutputStreamWrapper extends OutputStreamWrapper {

        private final RemoteLogstashWriter wr;
        private final List<String> passwordStrings;
        private final String prefix;

        public LogstashOutputStreamWrapper(RemoteLogstashWriter wr, List<String> passwordStrings, String prefix) {
            this.wr = wr;
            this.passwordStrings = passwordStrings;
            this.prefix = prefix;
        }

        public Object readResolve() {
            return new RemoteLogstashOutputStream(wr, prefix).maskPasswords(passwordStrings);
        }

        @Override
        public void write(int b) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

}
