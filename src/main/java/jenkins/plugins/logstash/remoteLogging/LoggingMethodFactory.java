/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkins.plugins.logstash.remoteLogging;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import jenkins.model.logging.LoggingMethod;
import jenkins.model.logging.LoggingMethodLocator;

/**
 *
 * @author Oleg Nenashev
 */
@Extension
public class LoggingMethodFactory extends LoggingMethodLocator {

    @Override
    protected LoggingMethod getLoggingMethod(Run run) {
        if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild)run;
            // TODO: return null if LogstashWrapper is disabled
            return new LogstashDaoLoggingMethod();
        }
        return null;
    }
    
}
