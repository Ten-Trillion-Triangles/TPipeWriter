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

# Find and execute TPipeWriter jar (prefer shadow jar)
JAR_FILE=$(find . -name "*-all.jar" -type f | head -1)
if [ -z "$JAR_FILE" ]; then
    JAR_FILE=$(find . -name "TPipeWriter*.jar" -type f | head -1)
fi

if [ -z "$JAR_FILE" ]; then
    echo "TPipeWriter jar not found"
    exit 1
fi

echo "Starting TPipeWriter..."
java -jar "$JAR_FILE"