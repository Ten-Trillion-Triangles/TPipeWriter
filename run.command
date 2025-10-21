#!/bin/bash

# Find AWS credentials and set environment variables
AWS_DIR="$HOME/.aws"
CREDENTIALS_FILE="$AWS_DIR/credentials"

if [ -f "$CREDENTIALS_FILE" ]; then
    export AWS_ACCESS_KEY_ID=$(grep -A2 "\[default\]" "$CREDENTIALS_FILE" | grep "aws_access_key_id" | cut -d'=' -f2 | tr -d ' ')
    export AWS_SECRET_ACCESS_KEY=$(grep -A2 "\[default\]" "$CREDENTIALS_FILE" | grep "aws_secret_access_key" | cut -d'=' -f2 | tr -d ' ')
    echo "AWS credentials loaded"
else
    echo "AWS credentials file not found at $CREDENTIALS_FILE"
    exit 1
fi

# Run TPipeWriter using gradle with increased memory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building TPipeWriter..."
./gradlew installDist

echo "Starting TPipeWriter..."
export JAVA_HOME=$(/usr/libexec/java_home -v 24)
./build/install/TPipeWriter/bin/TPipeWriter