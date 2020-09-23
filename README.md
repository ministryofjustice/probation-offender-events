# probation-offender-events

[![CircleCI](https://circleci.com/gh/ministryofjustice/probation-offender-events/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/probation-offender-events)
[![Docker](https://quay.io/repository/hmpps/probation-offender-events/status)](https://quay.io/repository/hmpps/probation-offender-events/status)

### Generate events for the offender changes in probation

Background service for generating events for the **probation-offender-events** from Delius changes indicating a change to an offender in probation

Events are generated when this service detects a change in Delius via the Delius OFFENDER_DELTA table. Rows are added via Delius database triggers. These triggers are currently limited changes to core offender changes; e.g changes to the management of a sentence will not typically raise an event.

For each change detected two events will be raised:

*   a generic event **OFFENDER_CHANGED**
*   a more specific event with greater detail on what has changed: e.g **OFFENDER_ALIAS_CHANGED**

We recommend subscribing to the specific events, the **OFFENDER_CHANGED** is deprecated and kept only for backward compatibility with existing subscribers.

The specific events currently being raised are

*   **OFFENDER_ADDRESS_CHANGED** is raised when an offender's address is changed
*   **OFFENDER_DETAILS_CHANGED** is raised when offender's details has changed, e.g. date of birth
*   **OFFENDER_MANAGER_CHANGED** is raised when an offender manager is allocated to the offender
*   **OFFENDER_ALIAS_CHANGED** is raised when a alias is changed
*   **OFFENDER_OFFICER_CHANGED** is raised when a current offender manager name changes

### Topic subscription

Clients are expected to use a SQS AWS queue to receive events with the queue subscribed to **probation-offender-events** topic.

Clients can subscribe to one or more events. A typical subscription could be:

<pre>    resource "aws_sns_topic_subscription" "my_probation_subscription" {
    provider      = aws.london
    topic_arn     = module.probation_offender_events.topic_arn
    protocol      = "sqs"
    endpoint      = module.my_queue.sqs_arn
    filter_policy = "{\"eventType\":[ \"OFFENDER_DETAILS_CHANGED\", \"OFFENDER_ADDRESS_CHANGED\"] }"
    }

</pre>

The DPS Tech Team will create the queue and subscription on your behalf. If you are using the MoJ Cloud Platform we will need your namespace to send secrets there for your new queue.

## Architecture and design

The service uses a Spring Scheduler to pull the OFFENDER_DELTA table via the **community-api** 

The scheduler will request all records every 10 seconds and process all records found. Multiple instances of this service can run currently since each record is "locked" when retrieved.
The lock is "released" by either deleting the record or marking it as permanently failed. Any records that have not been processed with 10 minutes is assumed to ready to be processed again. This logic is encapsulated in the **community-api**.

### Running

`localstack` is used to emulate the AWS SQS and Elastic Search service. When running the integration test this will be started automatically. If you want the tests to use an already running version of `localstack` run the tests with the environment `AWS_PROVIDER=localstack`. This has the benefit of running the test quicker without the overhead of starting the `localstack` container.

Any commands in `localstack/setup-sns.sh`will be run when `localstack` starts, so this should contain commands to create the appropriate topic and test queues.

Running all services locally:
```bash
docker-compose up 
```

*Please note the above will not work on a Mac using docker desktop since the docker network host mode is not supported on a Mac*

For a Mac it recommended running all components *except* probation-offender-events (see below) then running probation-offender-events externally:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun 
```

Queues and topics will automatically be created when the `localstack` container starts.

Running all services except this application (hence allowing you to run this in the IDE)

```bash
docker-compose up --scale probation-offender-events=0 
```

Depending on the speed of your machine when running all services you may need to scale `probation-offender-events=0` until localstack starts. This is a workaround for an issue whereby Spring Boot gives up trying to connect to SNS when the services first starts up.

### Running tests

#### External localstack

`SNS_PROVIDER=localstack ./gradlew check` will override the default behaviour and will expect localstack to already be started externally. In this mode the following services must be started `sqs,sns,es`

`docker-compose up localstack` will start the required AWS services.  

## Regression test

Recommended regression tests is as follows:

* Trigger a change in Delius, e.g for any offender change their date of birth
* Within Delius search for the offender in the BETA National Search - you should be able to search using the updated data of birth
* Check [Offender events dev tool](https://offender-events-ui-dev.prison.service.justice.gov.uk/messages) that at least 2 events are raised, e.g. *OFFENDER_DETAILS_CHANGED* and *OFFENDER_CHANGED*

## Support

Performance of the polling can be checked with this Application Insights Query
``` kusto
customEvents
| where name == "ProbationOffenderEvent"
| project update_age_seconds = toint(customDimensions.timeSinceUpdateSeconds), timestamp
| summarize max(update_age_seconds),min(update_age_seconds), avg(update_age_seconds) by bin(timestamp, 1m)
| render columnchart with (kind = unstacked ) ;   
```
Typically, we expect the time between a change in Delius to the time a message is published, should be around 10 seconds.

An alert will be raised if this time increases substantially.

Number of events that have been raised can be found with 

``` kusto
customEvents
| where cloud_RoleName == "probation-offender-events"
| summarize count() by bin(timestamp, 60m)
| render columnchart 
```
Although 2 events will be published per update, only a single telemetry event will be raised.


Number of changes that have been permanently marked as failed can be found with

``` kusto
customEvents
| where cloud_RoleName == "probation-offender-events"
| where name == "ProbationOffenderPermanentlyFailedEvent"
```
These are events where the system cannot publish a message due to the offender no longer being accessible to the community-api, typically because no organisation is related to the offender, so they become "invisible".
We would expect very few of these in any given month.