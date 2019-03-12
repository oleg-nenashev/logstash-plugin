To run this demo:

1. `make run`
1. when that is started enough, `docker-compose up -d agent`
1. go to http://localhost:8080/job/sample/
1. **Build Now**
1. inspect **Console Output**
1. see console annotations in `Running on`__`Jenkins`__`in /var/jenkins_home/workspace/sample`
1. **hide**/**show** blocks
1. configure http://localhost:5601/ with `logstash*` and `@timestamp`
1. inspect **External log (Kibana)**
1. compare `sender` fields to `docker ps` container IDs
1. verify that passwords are masked
