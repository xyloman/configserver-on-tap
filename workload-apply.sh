#!/bin/bash

name=configserver

tanzu apps workloads delete ${name} --yes

# Use to avoid conflicts with multiple tilt instances
# This will download and build the artifact locally removing the need for build service to also resolve dependencies
./mvnw clean package -DskipTests
unzip ./target/*.jar -d ./target/src

tanzu apps workloads apply \
  --file config/workload.yaml \
  --local-path ./target/src \
  --tail \
  --tail-timestamp \
  --source-image dev.local:5000/${name} \
  --yes

tanzu apps workloads tail \
  --timestamp \
  ${name}