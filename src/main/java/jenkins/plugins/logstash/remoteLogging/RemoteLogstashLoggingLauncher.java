/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkins.plugins.logstash.remoteLogging;

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.plugins.logstash.LogstashBuildWrapper;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.input.NullInputStream;

/**
 *
 * @author Oleg Nenashev
 */
public class RemoteLogstashLoggingLauncher extends Launcher.DecoratedLauncher {

    private static final NullInputStream NULL_INPUT_STREAM = new NullInputStream(0);
    private final Run run;

    public RemoteLogstashLoggingLauncher(Launcher inner, Run run) {
        super(inner);
        this.run = run;
    }

    @Override
    public Proc launch(Launcher.ProcStarter ps) throws IOException {
        final RemoteLogstashWriter wrOut = new RemoteLogstashWriter(run, Jenkins.getInstance());
        final RemoteLogstashWriter wrErr = new RemoteLogstashWriter(run, Jenkins.getInstance());

        List<String> passwordStrings = new ArrayList<String>();
        for (MaskPasswordsBuildWrapper.VarPasswordPair password : LogstashDaoLoggingMethod.getVarPasswordPairs(run)) {
            passwordStrings.add(password.getPassword());
        }
        final OutputStreamWrapper streamOut = new OutputStreamWrapper(wrOut, passwordStrings, "");
        final OutputStreamWrapper streamErr = new OutputStreamWrapper(wrErr, passwordStrings, "ERROR: ");

        // RemoteLogstashReporterStream(new CloseProofOutputStream(ps.stdout()
        final OutputStream out = ps.stdout() == null ? null : streamOut;
        final OutputStream err = ps.stdout() == null ? null : streamErr;
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

    private static class OutputStreamWrapper extends OutputStream implements Serializable {

        private final RemoteLogstashWriter wr;
        private final List<String> passwordStrings;
        private final String prefix;

        public OutputStreamWrapper(RemoteLogstashWriter wr, List<String> passwordStrings, String prefix) {
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

    private static class RemoteLaunchCallable extends MasterToSlaveCallable<Launcher.RemoteProcess, IOException> {

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
            if (workDir != null) {
                ps.pwd(workDir);
            }
            if (reverseStdin) {
                ps.writeStdin();
            }
            if (reverseStdout) {
                ps.readStdout();
            }
            if (reverseStderr) {
                ps.readStderr();
            }

            final Proc p = ps.start();

            return Channel.current().export(Launcher.RemoteProcess.class, new Launcher.RemoteProcess() {
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
}
