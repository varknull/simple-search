#!/bin/bash

set -o errexit # exit on errors
WAIT_TIME=20
export WORKING_DIR=$(pwd)

echo "Using '$(docker-compose --version)'"

cd ${WORKING_DIR}
export APP_JAR=$(ls target/ | grep '.jar' | grep fat)
echo "Using artifact 'target/${APP_JAR}'"

docker-compose --file ${WORKING_DIR}/docker/docker-compose.yml up --build -d

echo "Waiting for test environment to be ready (timeout in ${WAIT_TIME} seconds)..."

for i in `seq ${WAIT_TIME}`; do
    state="$(docker inspect --format "{{.State.Health.Status}}" "vertx-service")"
    if [[ "$state" == "healthy" ]]; then
      timeout=0
      break
    fi

    sleep 1
done

if [[ ${timeout} != 0 ]]; then
    echo "Operation timed out" >&2
else
    echo "Service is up on port 8888!"
fi

exit 0
