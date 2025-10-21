#!/bin/bash
cd "$(dirname "$0")"

echo "Cleaning and building TPipe dependencies..."
cd ../TPipe/TPipe
./gradlew clean
chmod 755 gradlew
chmod -R 755 build .gradle 2>/dev/null || true
chmod -R 755 */build */src/main/resources 2>/dev/null || true
./gradlew build -x test

echo "Building TPipeWriter..."
cd ../../TPipeWriter
./gradlew clean
./gradlew shadowJar

echo "Build complete. Press any key to close..."
read -n 1
