#!/usr/bin/env bash
set -e
export TERM=ansi
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2
export PAGER=

aws --endpoint-url=http://localhost:4575 sns create-topic --name probation_offender_events
aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name test_queue
aws --endpoint-url=http://localhost:4575 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:probation_offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4576/queue/test_queue

echo "SNS Configured"
