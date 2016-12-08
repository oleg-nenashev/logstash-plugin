FROM jenkinsci/jenkins:2.32

COPY plugins.txt /plugins.txt
RUN /usr/local/bin/plugins.sh /plugins.txt

# See PR links from https://issues.jenkins-ci.org/browse/JENKINS-38381
ADD https://jitpack.io/com/github/jglick/durable-task-plugin/8f11c53/durable-task-plugin-8f11c53.hpi /usr/share/jenkins/ref/plugins/durable-task.jpi 
ADD https://jitpack.io/com/github/jglick/workflow-api-plugin/99f1c51/workflow-api-plugin-99f1c51.hpi /usr/share/jenkins/ref/plugins/workflow-api.jpi
ADD https://jitpack.io/com/github/jglick/workflow-support-plugin/cf6f11f/workflow-support-plugin-cf6f11f.hpi /usr/share/jenkins/ref/plugins/workflow-support.jpi
ADD https://jitpack.io/com/github/jglick/workflow-job-plugin/211bd14/workflow-job-plugin-211bd14.hpi /usr/share/jenkins/ref/plugins/workflow-job.jpi
ADD https://jitpack.io/com/github/jglick/workflow-durable-task-step-plugin/940d360/workflow-durable-task-step-plugin-940d360.hpi /usr/share/jenkins/ref/plugins/workflow-durable-task-step.jpi
ADD https://jitpack.io/com/github/jglick/logstash-plugin/e168c03/logstash-plugin-e168c03.hpi /usr/share/jenkins/ref/plugins/logstash.jpi

ADD JENKINS_HOME /usr/share/jenkins/ref/

# https://github.com/docker/docker/issues/6119 workaround:
USER root
RUN chown -R jenkins.jenkins /usr/share/jenkins/ref
USER jenkins
