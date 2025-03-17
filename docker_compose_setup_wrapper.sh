#!/bin/bash
#Replace with export HOST_NAME=$(hostname) for an app deployed deployed on a server which name is known to some DNS
export HOST_IP=$(ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1')
echo $HOST_IP
docker-compose up --build
