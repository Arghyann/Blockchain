#!/bin/bash

echo "Stopping any running containers..."
docker-compose down

echo "Starting P2P Docker network and streaming logs directly..."
docker-compose up --build
