/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkins.plugins.logstash.util;

import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.uniqueid.IdStore;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Creates on-demand Unique IDs.
 * @author Oleg Nenashev
 */
public class UniqueIdHelper {
    
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public static String getOrCreateId(hudson.model.Job<?,?> job) {
     if (Jenkins.getInstance() == null) {
         return null;
     }
        
     String id = IdStore.getId(job);
     if (id == null) {
         IdStore.makeId(job);
         id = IdStore.getId(job);;
     }
     return id;
  }
}
