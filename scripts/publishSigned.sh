#!/bin/bash
echo "Assumed that you ran 'sbt +clean +update dependencyUpdates evicted +test:compile +test' first!"
echo ""
if grep -q SNAP build.sbt
then
   echo "There are SNAPSHOTs in the build! Aborting."
   exit 1
fi

sbt +coreJS/publishSigned +coreJVM/publishSigned +fscape-cdp/publishSigned +fscape-macros/publishSigned +fscape-modules/publishSigned +fscape-views/publishSigned +lucreJS/publishSigned +lucreJVM/publishSigned

