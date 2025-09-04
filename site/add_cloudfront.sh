#!/bin/bash
# site/add_cloudfront.sh

# Check if bucket name is provided
if [ -z "$1" ]; then
  echo "Error: S3 bucket name is required"
  echo "Usage: $0 <bucket-name> [custom-domain]"
  echo "Example: $0 my-website-bucket"
  echo "Example: $0 my-website-bucket my-custom-domain.com"
  exit 1
fi

BUCKET_NAME=$1
CUSTOM_DOMAIN=$2
REGION="us-east-1"  # CloudFront certificates must be in us-east-1

echo "Adding CloudFront with HTTPS support to existing S3 website: $BUCKET_NAME"

# Verify the S3 bucket exists and is configured for website hosting
echo "Verifying S3 bucket configuration..."
BUCKET_EXISTS=$(aws s3api head-bucket --bucket $BUCKET_NAME 2>/dev/null && echo "true" || echo "false")

if [ "$BUCKET_EXISTS" = "false" ]; then
  echo "Error: S3 bucket '$BUCKET_NAME' does not exist or is not accessible."
  exit 1
fi

# Check if bucket is configured for website hosting
WEBSITE_CONFIG=$(aws s3api get-bucket-website --bucket $BUCKET_NAME 2>/dev/null)
if [ $? -ne 0 ]; then
  echo "Warning: Bucket is not configured for website hosting. Configuring now..."
  aws s3 website s3://$BUCKET_NAME/ --index-document index.html --error-document error.html

  # Ensure bucket has public read policy
  echo "Ensuring bucket has public read access..."
  cat > /tmp/bucket-policy.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::$BUCKET_NAME/*"
    }
  ]
}
EOF
  aws s3api put-bucket-policy --bucket $BUCKET_NAME --policy file:///tmp/bucket-policy.json
fi

# Get the S3 website endpoint
S3_WEBSITE_ENDPOINT="$BUCKET_NAME.s3-website-us-east-1.amazonaws.com"
echo "S3 website endpoint: http://$S3_WEBSITE_ENDPOINT"

# Handle SSL certificate
CERTIFICATE_ARN=""
if [ ! -z "$CUSTOM_DOMAIN" ]; then
  echo "Setting up SSL certificate for custom domain: $CUSTOM_DOMAIN"

  # Check if certificate already exists
  EXISTING_CERT=$(aws acm list-certificates --region us-east-1 --query "CertificateSummaryList[?DomainName=='$CUSTOM_DOMAIN'].CertificateArn" --output text)

  if [ ! -z "$EXISTING_CERT" ]; then
    echo "Found existing certificate: $EXISTING_CERT"
    CERTIFICATE_ARN=$EXISTING_CERT

    # Check certificate status
    CERT_STATUS=$(aws acm describe-certificate --certificate-arn $CERTIFICATE_ARN --region us-east-1 --query "Certificate.Status" --output text)
    echo "Certificate status: $CERT_STATUS"

    if [ "$CERT_STATUS" != "ISSUED" ]; then
      echo "Warning: Certificate is not in ISSUED status. CloudFront distribution may fail to deploy."
      echo "Please ensure the certificate is validated before proceeding."
    fi
  else
    echo "Requesting new SSL certificate for $CUSTOM_DOMAIN..."
    CERTIFICATE_ARN=$(aws acm request-certificate \
      --domain-name $CUSTOM_DOMAIN \
      --validation-method DNS \
      --region us-east-1 \
      --query CertificateArn \
      --output text)

    if [ -z "$CERTIFICATE_ARN" ]; then
      echo "Failed to request SSL certificate. Proceeding without custom domain."
      CUSTOM_DOMAIN=""
    else
      echo "Certificate requested: $CERTIFICATE_ARN"
      echo "You need to validate this certificate before CloudFront can use it."
      echo "Please add the DNS validation records to your domain's DNS configuration."

      # Show validation records
      sleep 5  # Wait for certificate to be processed
      echo "Certificate validation records:"
      aws acm describe-certificate --certificate-arn $CERTIFICATE_ARN --region us-east-1 --query "Certificate.DomainValidationOptions[].ResourceRecord" --output table

      echo ""
      echo "Would you like to continue creating the CloudFront distribution now? (y/n)"
      echo "Note: The distribution will not work until the certificate is validated."
      read CONTINUE_SETUP

      if [ "$CONTINUE_SETUP" != "y" ] && [ "$CONTINUE_SETUP" != "Y" ]; then
        echo "Setup cancelled. Run this script again after validating the certificate."
        exit 0
      fi
    fi
  fi
else
  echo "No custom domain specified. CloudFront will use default *.cloudfront.net domain with AWS-managed SSL."
fi

# Create CloudFront distribution configuration
echo "Creating CloudFront distribution configuration..."

if [ ! -z "$CUSTOM_DOMAIN" ] && [ ! -z "$CERTIFICATE_ARN" ]; then
  # Configuration with custom domain and SSL certificate
  cat > /tmp/cloudfront-config.json << EOF
{
  "CallerReference": "$(date +%s)-$BUCKET_NAME",
  "Comment": "CloudFront distribution for S3 website $BUCKET_NAME with custom domain $CUSTOM_DOMAIN",
  "Aliases": {
    "Quantity": 1,
    "Items": ["$CUSTOM_DOMAIN"]
  },
  "DefaultRootObject": "index.html",
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "S3-Website-$BUCKET_NAME",
        "DomainName": "$S3_WEBSITE_ENDPOINT",
        "CustomOriginConfig": {
          "HTTPPort": 80,
          "HTTPSPort": 443,
          "OriginProtocolPolicy": "http-only"
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "S3-Website-$BUCKET_NAME",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 7,
      "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
      "CachedMethods": {
        "Quantity": 2,
        "Items": ["GET", "HEAD"]
      }
    },
    "ForwardedValues": {
      "QueryString": false,
      "Cookies": {
        "Forward": "none"
      }
    },
    "TrustedSigners": {
      "Enabled": false,
      "Quantity": 0
    },
    "MinTTL": 0,
    "DefaultTTL": 86400,
    "MaxTTL": 31536000,
    "Compress": true
  },
  "CustomErrorResponses": {
    "Quantity": 1,
    "Items": [
      {
        "ErrorCode": 404,
        "ResponsePagePath": "/error.html",
        "ResponseCode": "404",
        "ErrorCachingMinTTL": 300
      }
    ]
  },
  "ViewerCertificate": {
    "ACMCertificateArn": "$CERTIFICATE_ARN",
    "SSLSupportMethod": "sni-only",
    "MinimumProtocolVersion": "TLSv1.2_2021"
  },
  "Enabled": true,
  "PriceClass": "PriceClass_100"
}
EOF
else
  # Configuration without custom domain (uses CloudFront default SSL)
  cat > /tmp/cloudfront-config.json << EOF
{
  "CallerReference": "$(date +%s)-$BUCKET_NAME",
  "Comment": "CloudFront distribution for S3 website $BUCKET_NAME",
  "DefaultRootObject": "index.html",
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "S3-Website-$BUCKET_NAME",
        "DomainName": "$S3_WEBSITE_ENDPOINT",
        "CustomOriginConfig": {
          "HTTPPort": 80,
          "HTTPSPort": 443,
          "OriginProtocolPolicy": "http-only"
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "S3-Website-$BUCKET_NAME",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 7,
      "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
      "CachedMethods": {
        "Quantity": 2,
        "Items": ["GET", "HEAD"]
      }
    },
    "ForwardedValues": {
      "QueryString": false,
      "Cookies": {
        "Forward": "none"
      }
    },
    "TrustedSigners": {
      "Enabled": false,
      "Quantity": 0
    },
    "MinTTL": 0,
    "DefaultTTL": 86400,
    "MaxTTL": 31536000,
    "Compress": true
  },
  "CustomErrorResponses": {
    "Quantity": 1,
    "Items": [
      {
        "ErrorCode": 404,
        "ResponsePagePath": "/error.html",
        "ResponseCode": "404",
        "ErrorCachingMinTTL": 300
      }
    ]
  },
  "ViewerCertificate": {
    "CloudFrontDefaultCertificate": true
  },
  "Enabled": true,
  "PriceClass": "PriceClass_100"
}
EOF
fi

# Create CloudFront distribution
echo "Creating CloudFront distribution (this may take 10-15 minutes)..."
DISTRIBUTION_RESPONSE=$(aws cloudfront create-distribution --distribution-config file:///tmp/cloudfront-config.json 2>/dev/null)

if [ $? -ne 0 ]; then
  echo "Failed to create CloudFront distribution. This might be due to:"
  echo "1. Invalid or unvalidated SSL certificate"
  echo "2. AWS permissions issues"
  echo "3. Invalid configuration"
  echo ""
  echo "Please check your AWS credentials and certificate status, then try again."
  exit 1
fi

DISTRIBUTION_ID=$(echo "$DISTRIBUTION_RESPONSE" | grep -o '"Id": "[^"]*"' | head -1 | cut -d'"' -f4)
DISTRIBUTION_DOMAIN=$(echo "$DISTRIBUTION_RESPONSE" | grep -o '"DomainName": "[^"]*"' | head -1 | cut -d'"' -f4)

echo "✅ CloudFront distribution created successfully!"
echo "Distribution ID: $DISTRIBUTION_ID"
echo "CloudFront domain: $DISTRIBUTION_DOMAIN"
echo ""

# Provide next steps
echo "🔄 Distribution Status: Deploying (this takes 10-15 minutes)"
echo ""
echo "📋 Next Steps:"
echo "1. Wait for distribution to deploy (check status with: aws cloudfront get-distribution --id $DISTRIBUTION_ID --query 'Distribution.Status')"

if [ ! -z "$CUSTOM_DOMAIN" ]; then
  echo "2. Update your DNS to point $CUSTOM_DOMAIN to $DISTRIBUTION_DOMAIN"
  echo "   - Create a CNAME record: $CUSTOM_DOMAIN -> $DISTRIBUTION_DOMAIN"
  echo "3. Your HTTPS website will be available at: https://$CUSTOM_DOMAIN"
else
  echo "2. Your HTTPS website will be available at: https://$DISTRIBUTION_DOMAIN"
fi

echo ""
echo "🌐 Current Access URLs:"
echo "HTTP (S3): http://$S3_WEBSITE_ENDPOINT"
echo "HTTPS (CloudFront): https://$DISTRIBUTION_DOMAIN"

if [ ! -z "$CUSTOM_DOMAIN" ]; then
  echo "HTTPS (Custom Domain): https://$CUSTOM_DOMAIN (after DNS update)"
fi

echo ""
echo "💡 Useful Commands:"
echo "Check distribution status: aws cloudfront get-distribution --id $DISTRIBUTION_ID --query 'Distribution.Status'"
echo "Invalidate cache: aws cloudfront create-invalidation --distribution-id $DISTRIBUTION_ID --paths '/*'"
echo "List distributions: aws cloudfront list-distributions --query 'DistributionList.Items[].{ID:Id,Domain:DomainName,Status:Status}'"

# Clean up temporary files
rm -f /tmp/cloudfront-config.json /tmp/bucket-policy.json

echo ""
echo "✅ Setup complete! Your S3 website now has CloudFront with HTTPS support."