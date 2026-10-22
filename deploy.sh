#!/bin/bash

set -e

APP_NAME="shopsphere"
JAR_NAME="shopsphere-0.0.1-SNAPSHOT.jar"
S3_BUCKET="YOUR_BUCKET_NAME"

echo "Building application..."
./mvnw clean package -DskipTests

echo "Uploading JAR..."
aws s3 cp "target/$JAR_NAME" "s3://$S3_BUCKET/$JAR_NAME"

echo "Deployment artifact uploaded."

echo "Deployment completed successfully."