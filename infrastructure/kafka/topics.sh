#!/usr/bin/env bash
# Creates all LearnPulse topics + DLQs. Run once at first compose-up via kafka-init service.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
REPLICATION=1   # dev: single broker; prod: 3

MS_30_DAYS=2592000000
MS_90_DAYS=7776000000

create() {
  local topic=$1 partitions=$2 retention_ms=$3
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$REPLICATION" \
    --config cleanup.policy=delete \
    --config retention.ms="$retention_ms"
  echo "  created: $topic (partitions=$partitions, retention=${retention_ms}ms)"
}

echo "Creating LearnPulse Kafka topics on $BOOTSTRAP ..."

# Main topics
create course.published        3  $MS_30_DAYS
create user.enrolled           6  $MS_30_DAYS
create module.unlocked         6  $MS_30_DAYS
create course.completed        6  $MS_90_DAYS
create certificate.generated   6  $MS_90_DAYS

# Dead-letter queues
create course.published.dlq         1  $MS_30_DAYS
create user.enrolled.dlq            1  $MS_30_DAYS
create module.unlocked.dlq          1  $MS_30_DAYS
create course.completed.dlq         1  $MS_90_DAYS
create certificate.generated.dlq    1  $MS_90_DAYS

echo "All topics ready."