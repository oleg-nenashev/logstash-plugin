/*
 * The MIT License
 *
 * Copyright 2013 Hewlett-Packard Development Company, L.P.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.logstash;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarPasswordPair;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsConfig;
import hudson.CloseProofOutputStream;
import hudson.Proc;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.Serializable;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.input.NullInputStream;

/**
 * Build wrapper that decorates the build's logger to insert a
 * {@code LogstashNote} on each output line.
 *
 * @author K Jonathan Harker
 */
public class LogstashBuildWrapper extends BuildWrapper {

  /**
   * Create a new {@link LogstashBuildWrapper}.
   */
  @DataBoundConstructor
  public LogstashBuildWrapper() {}

  /**
   * {@inheritDoc}
   */
  @Override
  public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

    return new Environment() {};
  }

  private static final NullInputStream NULL_INPUT_STREAM = new NullInputStream(0);
  
    @Override
    public Launcher decorateLauncher(final AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        if (build.getBuiltOn().getNodeName().isEmpty()) {
            return super.decorateLauncher(build, launcher, listener);
        }
        
        return new Launcher.DecoratedLauncher(launcher) {
            public Proc launch(Launcher.ProcStarter ps) throws IOException {
                final RemoteLogstashWriter wr = new RemoteLogstashWriter(build, Jenkins.getActiveInstance());
                final OutputStreamWrapper stream = new OutputStreamWrapper(wr);
                
                
                // RemoteLogstashReporterStream(new CloseProofOutputStream(ps.stdout()
                final OutputStream out = ps.stdout() == null ? null : stream;
                final OutputStream err = ps.stdout() == null ? null : stream;
                final InputStream in = (ps.stdin() == null || ps.stdin() == NULL_INPUT_STREAM) ? null : new RemoteInputStream(ps.stdin(), false);
                final String workDir = ps.pwd() == null ? null : ps.pwd().getRemote();

                // TODO: we do not reverse streams => the parameters
                try {
                    final RemoteLaunchCallable callable = new RemoteLaunchCallable(
                            ps.cmds(), ps.masks(), ps.envs(), in, 
                            false, out, false, err, 
                            false, ps.quiet(), workDir, listener);
                    
                    return new Launcher.RemoteLauncher.ProcImpl(getChannel().call(callable));
                } catch (InterruptedException e) {
                    throw (IOException) new InterruptedIOException().initCause(e);
                }
            }
        };
    }
    
    private static class OutputStreamWrapper extends OutputStream implements Serializable {

        private final RemoteLogstashWriter wr;

        public OutputStreamWrapper(RemoteLogstashWriter wr) {
            this.wr = wr;
        }
        
        public Object readResolve() {
            return new RemoteLogstashOutputStream(wr);
        }
        
        @Override
        public void write(int b) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
    }
                
    private static class RemoteLaunchCallable extends MasterToSlaveCallable<Launcher.RemoteProcess,IOException> {
        private final List<String> cmd;
        private final boolean[] masks;
        private final String[] env;
        private final InputStream in;
        private final OutputStream out;
        private final OutputStream err;
        private final String workDir;
        private final TaskListener listener;
        private final boolean reverseStdin, reverseStdout, reverseStderr;
        private final boolean quiet;

        RemoteLaunchCallable(List<String> cmd, boolean[] masks, String[] env, InputStream in, boolean reverseStdin, OutputStream out, boolean reverseStdout, OutputStream err, boolean reverseStderr, boolean quiet, String workDir, TaskListener listener) {
            this.cmd = new ArrayList<String>(cmd);
            this.masks = masks;
            this.env = env;
            this.in = in;
            this.out = out;
            this.err = err;
            this.workDir = workDir;
            this.listener = listener;
            this.reverseStdin = reverseStdin;
            this.reverseStdout = reverseStdout;
            this.reverseStderr = reverseStderr;
            this.quiet = quiet;
        }

        public Launcher.RemoteProcess call() throws IOException {
            Launcher.ProcStarter ps = new Launcher.LocalLauncher(listener).launch();
            ps.cmds(cmd).masks(masks).envs(env).stdin(in).stdout(out).stderr(err).quiet(quiet);
            if(workDir!=null)   ps.pwd(workDir);
            if (reverseStdin)   ps.writeStdin();
            if (reverseStdout)  ps.readStdout();
            if (reverseStderr)  ps.readStderr();

            final Proc p = ps.start();

            return Channel.current().export(Launcher.RemoteProcess.class,new Launcher.RemoteProcess() {
                public int join() throws InterruptedException, IOException {
                    try {
                        return p.join();
                    } finally {
                        // make sure I/O is delivered to the remote before we return
                        try {
                            Channel.current().syncIO();
                        } catch (Throwable _) {
                            // this includes a failure to sync, slave.jar too old, etc
                        }
                    }
                }

                public void kill() throws IOException, InterruptedException {
                    p.kill();
                }

                public boolean isAlive() throws IOException, InterruptedException {
                    return p.isAlive();
                }

                public Launcher.IOTriplet getIOtriplet() {
                    Launcher.IOTriplet r = new Launcher.IOTriplet();
                        /* TODO: we do not need reverse?
                    if (reverseStdout)  r.stdout = new RemoteInputStream(p.getStdout());
                    if (reverseStderr)  r.stderr = new RemoteInputStream(p.getStderr());
                    if (reverseStdin)   r.stdin  = new RemoteOutputStream(p.getStdin());
                    */
                    return r;
                    
                }
            });
        }

        private static final long serialVersionUID = 1L;
    }


  /**
   * {@inheritDoc}
   */
  @Override
  public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) {
    LogstashWriter logstash = getLogStashWriter(build, logger);

    LogstashOutputStream los = new LogstashOutputStream(logger, logstash);

    if (build.getProject() instanceof BuildableItemWithBuildWrappers) {
      BuildableItemWithBuildWrappers project = (BuildableItemWithBuildWrappers) build.getProject();
      for (BuildWrapper wrapper: project.getBuildWrappersList()) {
        if (wrapper instanceof MaskPasswordsBuildWrapper) {
          List<VarPasswordPair> allPasswordPairs = new ArrayList<VarPasswordPair>();

          MaskPasswordsBuildWrapper maskPasswordsWrapper = (MaskPasswordsBuildWrapper) wrapper;
          List<VarPasswordPair> jobPasswordPairs = maskPasswordsWrapper.getVarPasswordPairs();
          if (jobPasswordPairs != null) {
            allPasswordPairs.addAll(jobPasswordPairs);
          }

          MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
          List<VarPasswordPair> globalPasswordPairs = config.getGlobalVarPasswordPairs();
          if (globalPasswordPairs != null) {
            allPasswordPairs.addAll(globalPasswordPairs);
          }

          return los.maskPasswords(allPasswordPairs);
        }
      }
    }

    return los;
  }

  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  // Method to encapsulate calls for unit-testing
  LogstashWriter getLogStashWriter(AbstractBuild<?, ?> build, OutputStream errorStream) {
    return new LogstashWriter(build, errorStream);
  }

  /**
   * Registers {@link LogstashBuildWrapper} as a {@link BuildWrapper}.
   */
  @Extension
  public static class DescriptorImpl extends BuildWrapperDescriptor {

    public DescriptorImpl() {
      super(LogstashBuildWrapper.class);
      load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(AbstractProject<?, ?> item) {
      return true;
    }
  }
}
