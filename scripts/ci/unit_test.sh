#!/bin/bash

project=$1
echo $(pwd;ls -la)
cd $project && yarn run lint && yarn run test:jenkins && cd ..
