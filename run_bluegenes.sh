#!/usr/bin/bash

source alliance_bluegenes.env

export BLUEGENES_DEFAULT_MINE_NAME
export BLUEGENES_DEFAULT_NAMESPACE
export SERVER_PORT
export HIDE_REGISTRY_MINES
export BLUEGENES_ADDITIONAL_MINES

java -jar target/bluegenes.jar
