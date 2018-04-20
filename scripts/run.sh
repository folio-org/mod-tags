#!/bin/bash
# Simple test script for mod-tags
# Loads mod-tags, but does not bother with the whole auth stack.
#
# Run in the main directory of mod-tags

# Parameters
OKAPIPORT=9130
OKAPIURL="http://localhost:$OKAPIPORT"
CURL="curl -w\n -D - "

# Check we have the fat jar
if [ ! -f target/mod-tags-fat.jar ]
then
  echo No fat jar found, no point in trying to run
  exit 1
fi

# Start Okapi (in dev mode, no database)
OKAPIPATH="../okapi/okapi-core/target/okapi-core-fat.jar"
java -Dport=$OKAPIPORT -jar $OKAPIPATH dev > okapi.log 2>&1 &
PID=$!
echo Started okapi on port $OKAPIPORT. PID=$PID
sleep 1 # give it time to start
echo

# Load mod-tags
echo "Loading mod-tags"
$CURL -X POST -d@target/ModuleDescriptor.json $OKAPIURL/_/proxy/modules
echo

echo "Deploying it"
$CURL -X POST \
   -d@target/DeploymentDescriptor.json \
   $OKAPIURL/_/discovery/modules
echo

# Test tenant
echo "Creating test tenant"
cat > /tmp/okapi.tenant.json <<END
{
  "id": "testlib",
  "name": "Test Library",
  "description": "Our Own Test Library"
}
END
$CURL -d@/tmp/okapi.tenant.json $OKAPIURL/_/proxy/tenants
echo
echo "Enabling it (without specifying the version)"
$CURL -X POST \
   -d'{"id":"mod-tags"}' \
   $OKAPIURL/_/proxy/tenants/testlib/modules
echo
sleep 1


# Various tests
echo Test 1: get empty list
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/tags
echo


echo Test 2: Post one
$CURL \
  -H "Content-type:application/json" \
  -H "X-Okapi-Tenant:testlib" \
  -X POST -d '{"label":"Important","description":"This is very important"}' \
  $OKAPIURL/tags

echo Test 3: get a list with the new one
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/tags
echo

echo Test 4: Post another one
# with the user-id, should trigger metadata creation
$CURL \
  -H "Content-type:application/json" \
  -H "X-Okapi-Tenant:testlib" \
  -H "X-Okapi-User-Id: 55555555-5555-5555-5555-555555555555" \
  -X POST -d '{"label":"Urgent","description":"This is UREGENT! Drop everything and fix this NOW"}' \
  $OKAPIURL/tags

echo Test 5: get a list with both
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/tags
echo

echo Test 6: query the user note
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/tags?query=label=Important
echo

echo Test 7: query both
$CURL -H "X-Okapi-Tenant:testlib" $OKAPIURL/tags?query=description=is
echo


# Trick to disable part of the script:
# Copy the 'cat' line at some point, it will skip everything until here
cat >/dev/null <<SKIPTHIS
SKIPTHIS

# Let it run
echo
echo "Hit enter to close"
read

# Clean up
echo "Cleaning up: Killing Okapi $PID"
kill $PID
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
ps | grep java && ( echo ... ; sleep 1  )
rm -rf /tmp/postgresql-embed*
ps | grep java && echo "OOPS - Still some processes running"
echo bye

