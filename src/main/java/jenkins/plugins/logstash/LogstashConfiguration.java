package jenkins.plugins.logstash;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.syslog.MessageFormat;

import org.apache.http.client.utils.URIBuilder;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.model.GlobalConfiguration;
import jenkins.plugins.logstash.LogstashInstallation.Descriptor;
import jenkins.plugins.logstash.configuration.ElasticSearch;
import jenkins.plugins.logstash.configuration.LogstashIndexer;
import jenkins.plugins.logstash.configuration.RabbitMq;
import jenkins.plugins.logstash.configuration.Redis;
import jenkins.plugins.logstash.configuration.Syslog;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao.IndexerType;
import net.sf.json.JSONObject;

@Extension
public class LogstashConfiguration extends GlobalConfiguration
{
  private static final Logger LOGGER = Logger.getLogger(LogstashConfiguration.class.getName());
  private LogstashIndexer<?> logstashIndexer;
  private boolean dataMigrated = false;
  private transient LogstashIndexer<?> activeIndexer;

  public LogstashConfiguration()
  {
    load();
    activeIndexer = logstashIndexer;
  }

  /**
   * Returns the current logstash indexer configuration.
   *
   * @return configuration instance
   */
  public LogstashIndexer<?> getLogstashIndexer()
  {
    return logstashIndexer;
  }

  public void setLogstashIndexer(LogstashIndexer<?> logstashIndexer)
  {
    this.logstashIndexer = logstashIndexer;
  }

  /**
   * Returns the actual instance of the logstash dao.
   * @return dao instance
   */
  @CheckForNull
  public LogstashIndexerDao getIndexerInstance()
  {
    if (activeIndexer != null)
    {
      return activeIndexer.getInstance();
    }
    return null;
  }

  public List<?> getIndexerTypes()
  {
    return LogstashIndexer.all();
  }

  @SuppressWarnings("deprecation")
  @Initializer(after = InitMilestone.JOB_LOADED)
  public void migrateData()
  {
    if (!dataMigrated)
    {
      Descriptor descriptor = LogstashInstallation.getLogstashDescriptor();
      if (descriptor.getType() != null)
      {
        IndexerType type = descriptor.getType();
        switch (type)
        {
          case REDIS:
            LOGGER.log(Level.INFO, "Migrating logstash configuration for Redis");
            Redis redis = new Redis();
            redis.setHost(descriptor.getHost());
            redis.setPort(descriptor.getPort());
            redis.setKey(descriptor.getKey());
            redis.setPassword(descriptor.getPassword());
            logstashIndexer = redis;
            break;
          case ELASTICSEARCH:
            LOGGER.log(Level.INFO, "Migrating logstash configuration for Elastic Search");
            URI uri;
            try
            {
              uri = (new URIBuilder(descriptor.getHost()))
                  .setPort(descriptor.getPort())
                  .setPath("/" + descriptor.getKey()).build();
              ElasticSearch es = new ElasticSearch();
              es.setUri(uri);
              es.setUsername(descriptor.getUsername());
              es.setPassword(descriptor.getPassword());
              logstashIndexer = es;
            }
            catch (URISyntaxException e)
            {
              LOGGER.log(Level.INFO, "Migrating logstash configuration for Elastic Search failed: " + e.toString());
            }
            break;
          case RABBIT_MQ:
            LOGGER.log(Level.INFO, "Migrating logstash configuration for  RabbitMQ");
            RabbitMq rabbitMq = new RabbitMq();
            rabbitMq.setHost(descriptor.getHost());
            rabbitMq.setPort(descriptor.getPort());
            rabbitMq.setQueue(descriptor.getKey());
            rabbitMq.setUsername(descriptor.getUsername());
            rabbitMq.setPassword(descriptor.getPassword());
            logstashIndexer = rabbitMq;
            break;
          case SYSLOG:
            LOGGER.log(Level.INFO, "Migrating logstash configuration for  SYSLOG");
            Syslog syslog = new Syslog();
            syslog.setHost(descriptor.getHost());
            syslog.setPort(descriptor.getPort());
            syslog.setSyslogProtocol(descriptor.getSyslogProtocol());
            switch (descriptor.getSyslogFormat())
            {
              case RFC3164:
                syslog.setMessageFormat(MessageFormat.RFC_3164);
                break;
              case RFC5424:
                syslog.setMessageFormat(MessageFormat.RFC_5424);
                break;
              default:
                syslog.setMessageFormat(MessageFormat.RFC_3164);
                break;
            }
            logstashIndexer = syslog;
            break;
          default:
            LOGGER.log(Level.INFO, "unknown logstash Indexer type: " + type);
            break;
        }
        activeIndexer = logstashIndexer;
      }
      dataMigrated = true;
      save();
    }
  }

  @Override
  public boolean configure(StaplerRequest staplerRequest, JSONObject json) throws FormException
  {
    // when we bind the stapler request we get a new instance of logstashIndexer.
    // logstashIndexer is holder for the dao instance.
    // To avoid that we get a new dao instance in case there was no change in configuration
    // we compare it to the currently active configuration.
    staplerRequest.bindJSON(this, json);
    if (!Objects.equals(logstashIndexer, activeIndexer))
    {
      activeIndexer = logstashIndexer;
    }
    save();
    return true;
  }

  public static LogstashConfiguration getInstance()
  {
    return GlobalConfiguration.all().get(LogstashConfiguration.class);
  }

}
