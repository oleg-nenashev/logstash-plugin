/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkins.plugins.logstash.remoteLogging;

import hudson.Launcher;

/**
 *
 * @author Oleg Nenashev
 */
public class LocalLogstashLoggingLauncher extends Launcher.DecoratedLauncher {
    
    public LocalLogstashLoggingLauncher(Launcher inner) {
        super(inner);
    }
    
}
