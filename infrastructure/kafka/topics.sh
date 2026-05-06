#!/usr/bin/env bash
# Creates all LearnPulse topics + DLQs. Run once at first compose-up via kafka-init service.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
REPLICATION=1   # dev: single broker

create() {
  local topic=$1 partitions=$2
  kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$REPLICATION"
  echo "  created: $topic (partitions=$partitions)"
}

echo "Creating LearnPulse Kafka topics on $BOOTSTRAP ..."

# Main topics (partition counts mirror prod intent; replication=1 for dev)
create course.published        3
create user.enrolled           6
create module.unlocked         6
create course.completed        6
create certificate.generated   6

# Dead-letter queues (1 partition each — DLQ traffic is low)
create course.published.dlq       1
create user.enrolled.dlq          1
create module.unlocked.dlq        1
create course.completed.dlq       1
create certificate.generated.dlq  1

echo "All topics ready."
