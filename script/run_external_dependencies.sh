#!/usr/bin/env bash
docker run --name rtclassification-cassandra -p 9160:9160 -p 9042:9042 -d cassandra:3
