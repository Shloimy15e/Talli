#!/bin/bash
# Builds a standalone Windows executable with bundled JRE.
# Output: dist/Talli/ — portable folder with Talli.exe
#
# Requires: JDK 21+ (jpackage ships with it) and Maven.

set -e

echo "==> Building fat jar..."
mvn package -q -f pom.xml

echo "==> Cleaning previous exe build..."
rm -rf dist

echo "==> Packaging standalone exe..."
jpackage \
  --type app-image \
  --name Talli \
  --app-version 1.0.0 \
  --input target \
  --main-jar talli-1.0.0.jar \
  --main-class dev.dynamiq.talli.App \
  --dest dist \
  --icon src/main/resources/icon.ico \
  --vendor "Dynamiq Solutions"

echo ""
echo "Done. Run: dist/Talli/Talli.exe"
