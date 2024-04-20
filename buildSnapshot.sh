#!/bin/bash

set -e

TAG=$(git tag | sort -n -r | head -n 1)
echo "Last git tag: ${TAG}"
TAG1=$(echo "${TAG}" | cut -d "." -f 1 | sed "s/v//")
TAG2=$(echo "${TAG}" | cut -d "." -f 2)
TAG3=$(echo "${TAG}" | cut -d "." -f 3)
TAG2=$((TAG2+1))

SNAP="${TAG1}.${TAG2}.${TAG3}-SNAPSHOT"
echo "Next SNAPSHOT version: $SNAP"

./gradlew clean assemble
docker buildx build --pull --push --platform linux/arm64,linux/amd64 --tag ghcr.io/mouse256/arylic-mqtt:${SNAP} -f src/main/docker/Dockerfile.jvm .

