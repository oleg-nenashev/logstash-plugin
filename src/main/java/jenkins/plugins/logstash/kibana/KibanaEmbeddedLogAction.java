/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jenkins.plugins.logstash.kibana;

import hudson.model.Action;
import hudson.model.Run;
import jenkins.plugins.logstash.util.UniqueIdHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Displays Embedded log.
 * @author Oleg Nenashev
 */
@Restricted(NoExternalUse.class)
public class KibanaEmbeddedLogAction implements Action {
    
    private final String jobId;
    private final Run run;
    
    public KibanaEmbeddedLogAction(Run run) {
        this.run = run;
        this.jobId =  UniqueIdHelper.getOrCreateId(run.getParent());
    }
    
    @Override
    public String getDisplayName() {
        return "External log (Kibana)";
    }

    @Override
    public String getIconFileName() {
        return "terminal.png";
    }

    @Override
    public String getUrlName() {
        return "externalLogKibana";
    } 

    public Run getRun() {
        return run;
    }

    public String getJobId() {
        return jobId;
    }
    
    @Restricted(NoExternalUse.class)
    public String getIframeSrc() {
        String kibanaUrl = "http://localhost:5601";
        
        return kibanaUrl + "/app/kibana#/discover" +
                "?_g=(refreshInterval:(display:Off,pause:!f,value:0)," +
               // "time:(from:" + run.getStartTimeInMillis() + ",mode:absolute,to:now,format:epoch_millis))" +
                "time:(from:now-24h,mode:quick,to:now))" +
                "&_a=(columns:!(message),filters:!()," +
                "index:logstash,interval:auto,query:(query_string:(analyze_wildcard:!t," +
                "query:'data.jobId:" + jobId + "%20AND%20data.buildNum:" + run.getNumber() + "'))," +
                "sort:!('@timestamp',asc),vis:(aggs:!((params:(field:data.jobId,orderBy:'2',size:20)" + 
                ",schema:segment,type:terms),(id:'2',schema:metric,type:count)),type:histogram))" + 
                "&indexPattern=logstash&type=histogram";
    }
}
