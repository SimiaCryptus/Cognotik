#!/bin/bash
# Check if domain name is provided
if [ -z "$1" ]; then
  echo "Error: Domain name is required"
  echo "Usage: $0 <domain-name>"
  exit 1
fi
DOMAIN=$1
BUCKET_NAME=$DOMAIN
REGION="us-east-1"  # Change this to your preferred AWS region
CLOUDFRONT_ENABLED=true  # Set to false if you don't want CloudFront

echo "Creating S3 bucket for domain: $DOMAIN"
# Create the S3 bucket
aws s3api create-bucket --bucket $BUCKET_NAME --region $REGION
# Disable block public access settings for the bucket
echo "Disabling block public access settings for the bucket..."
aws s3api put-public-access-block --bucket $BUCKET_NAME --public-access-block-configuration "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"

# Enable website hosting on the bucket
aws s3 website s3://$BUCKET_NAME/ --index-document index.html --error-document error.html

# Create bucket policy to allow public read access
echo "Setting bucket policy to allow public read access..."
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
# Apply the bucket policy
aws s3api put-bucket-policy --bucket $BUCKET_NAME --policy file:///tmp/bucket-policy.json
# Check if the bucket policy was applied successfully
if [ $? -ne 0 ]; then
  echo "Warning: Failed to apply bucket policy. Your bucket may not be publicly accessible."
  echo "You may need to manually disable Block Public Access settings in the AWS console:"
  echo "1. Go to https://s3.console.aws.amazon.com/s3/buckets/$BUCKET_NAME?region=$REGION&tab=permissions"
  echo "2. Under 'Block public access (bucket settings)', click Edit"
  echo "3. Uncheck all four options and save changes"
  echo "4. Then run this script again"
fi
# Create sample index.html
cat > /tmp/index.html << EOF
<!DOCTYPE html>
<html>
<head>
  <title>Welcome to $DOMAIN</title>
  <style>
    body {
      font-family: Arial, sans-serif;
      margin: 40px;
      text-align: center;
    }
  </style>
</head>
<body>
  <h1>Welcome to $DOMAIN</h1>
  <p>Your S3 website is now live!</p>
</body>
</html>
EOF
# Create sample error.html
cat > /tmp/error.html << EOF
<!DOCTYPE html>
<html>
<head>
  <title>Error - $DOMAIN</title>
  <style>
    body {
      font-family: Arial, sans-serif;
      margin: 40px;
      text-align: center;
    }
  </style>
</head>
<body>
  <h1>404 - Page Not Found</h1>
  <p>The page you are looking for does not exist.</p>
</body>
</html>
EOF
# Upload the sample files to the bucket
aws s3 cp /tmp/index.html s3://$BUCKET_NAME/
aws s3 cp /tmp/error.html s3://$BUCKET_NAME/
# Route53 DNS Configuration
echo "Setting up Route53 DNS for $DOMAIN..."
# Check if the domain is registered in Route53
HOSTED_ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name $DOMAIN --max-items 1 --query "HostedZones[?Name=='$DOMAIN.'].Id" --output text | cut -d'/' -f3)
if [ -z "$HOSTED_ZONE_ID" ]; then
  echo "No Route53 hosted zone found for $DOMAIN"
  echo "Would you like to create a new hosted zone for $DOMAIN? (y/n)"
  read CREATE_ZONE
  if [ "$CREATE_ZONE" = "y" ] || [ "$CREATE_ZONE" = "Y" ]; then
    echo "Creating new Route53 hosted zone for $DOMAIN..."
    ZONE_RESPONSE=$(aws route53 create-hosted-zone --name $DOMAIN --caller-reference $(date +%s) --query "HostedZone.Id" --output text)
    HOSTED_ZONE_ID=$(echo $ZONE_RESPONSE | cut -d'/' -f3)
    echo "New hosted zone created with ID: $HOSTED_ZONE_ID"
    echo "Please update your domain's name servers at your registrar with the following name servers:"
    aws route53 get-hosted-zone --id $HOSTED_ZONE_ID --query "DelegationSet.NameServers" --output text
  else
    echo "Skipping Route53 configuration."
    HOSTED_ZONE_ID=""
  fi
fi
if [ ! -z "$HOSTED_ZONE_ID" ]; then
  # Create a JSON file for the Route53 change batch
  cat > /tmp/route53-change-batch.json << EOF
{
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "$DOMAIN",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "Z3AQBSTGFYJSTF",
          "DNSName": "s3-website-$REGION.amazonaws.com",
          "EvaluateTargetHealth": false
        }
      }
    },
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "www.$DOMAIN",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [
          {
            "Value": "$DOMAIN"
          }
        ]
      }
    }
  ]
}
EOF
  # Apply the Route53 changes
  echo "Updating Route53 DNS records..."
  aws route53 change-resource-record-sets --hosted-zone-id $HOSTED_ZONE_ID --change-batch file:///tmp/route53-change-batch.json
  if [ $? -eq 0 ]; then
    echo "DNS configuration complete! Please allow some time for DNS changes to propagate."
    echo "Your website will be accessible at: http://$DOMAIN and http://www.$DOMAIN"
  else
    echo "Failed to update DNS records. Please check your AWS permissions and try again."
  fi
fi

echo "Website setup complete!"

# Set up CloudFront distribution with HTTPS if enabled
if [ "$CLOUDFRONT_ENABLED" = true ]; then
  echo "Setting up CloudFront distribution with HTTPS..."
  
  # First, request an SSL certificate from ACM
  echo "Requesting SSL certificate for $DOMAIN and www.$DOMAIN..."
  CERTIFICATE_ARN=$(aws acm request-certificate \
    --domain-name $DOMAIN \
    --validation-method DNS \
    --subject-alternative-names www.$DOMAIN \
    --region us-east-1 \
    --query CertificateArn \
    --output text)
  
  if [ -z "$CERTIFICATE_ARN" ]; then
    echo "Failed to request SSL certificate. Skipping CloudFront setup."
  else
    echo "Certificate requested: $CERTIFICATE_ARN"
    
    # Get certificate validation records and add them to Route53
    if [ ! -z "$HOSTED_ZONE_ID" ]; then
      echo "Adding certificate validation records to Route53..."
      sleep 5  # Wait a bit for the certificate to be processed
      
      VALIDATION_RECORDS=$(aws acm describe-certificate \
        --certificate-arn $CERTIFICATE_ARN \
        --region us-east-1 \
        --query "Certificate.DomainValidationOptions[].ResourceRecord")
      
      if [ ! -z "$VALIDATION_RECORDS" ]; then
        # Create validation records in Route53
        for i in $(seq 0 1); do
          RECORD_NAME=$(aws acm describe-certificate --certificate-arn $CERTIFICATE_ARN --region us-east-1 --query "Certificate.DomainValidationOptions[$i].ResourceRecord.Name" --output text)
          RECORD_VALUE=$(aws acm describe-certificate --certificate-arn $CERTIFICATE_ARN --region us-east-1 --query "Certificate.DomainValidationOptions[$i].ResourceRecord.Value" --output text)
          
          if [ ! -z "$RECORD_NAME" ] && [ ! -z "$RECORD_VALUE" ]; then
            cat > /tmp/validation-change-batch.json << EOF
{
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "$RECORD_NAME",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [
          {
            "Value": "$RECORD_VALUE"
          }
        ]
      }
    }
  ]
}
EOF
            aws route53 change-resource-record-sets --hosted-zone-id $HOSTED_ZONE_ID --change-batch file:///tmp/validation-change-batch.json
          fi
        done
        
        echo "Certificate validation records added. Certificate will be validated automatically."
        echo "This may take up to 30 minutes. You can check the status in the AWS Certificate Manager console."
        
        # Create CloudFront distribution configuration file
        cat > /tmp/cloudfront-config.json << EOF
{
  "CallerReference": "$(date +%s)",
  "Comment": "CloudFront distribution for $DOMAIN",
  "Aliases": {
    "Quantity": 2,
    "Items": ["$DOMAIN", "www.$DOMAIN"]
  },
  "DefaultRootObject": "index.html",
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "S3-$BUCKET_NAME",
        "DomainName": "$BUCKET_NAME.s3.amazonaws.com",
        "S3OriginConfig": {
          "OriginAccessIdentity": ""
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "S3-$BUCKET_NAME",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 2,
      "Items": ["GET", "HEAD"],
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
    "MinTTL": 0,
    "DefaultTTL": 86400,
    "MaxTTL": 31536000
  },
  "ViewerCertificate": {
    "ACMCertificateArn": "$CERTIFICATE_ARN",
    "SSLSupportMethod": "sni-only",
    "MinimumProtocolVersion": "TLSv1.2_2021"
  },
  "Enabled": true
}
EOF
        
        echo "Creating CloudFront distribution (this may take a few minutes)..."
        DISTRIBUTION_RESPONSE=$(aws cloudfront create-distribution --distribution-config file:///tmp/cloudfront-config.json)
        DISTRIBUTION_ID=$(echo "$DISTRIBUTION_RESPONSE" | grep -o '"Id": "[^"]*"' | cut -d'"' -f4)
        DISTRIBUTION_DOMAIN=$(echo "$DISTRIBUTION_RESPONSE" | grep -o '"DomainName": "[^"]*"' | cut -d'"' -f4)
        
        echo "CloudFront distribution created: $DISTRIBUTION_ID"
        echo "CloudFront domain: $DISTRIBUTION_DOMAIN"
        
        # Update Route53 records to point to CloudFront
        if [ ! -z "$HOSTED_ZONE_ID" ] && [ ! -z "$DISTRIBUTION_DOMAIN" ]; then
          echo "Updating Route53 records to point to CloudFront..."
          cat > /tmp/route53-cloudfront-change.json << EOF
{
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "$DOMAIN",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "Z2FDTNDATAQYW2",
          "DNSName": "$DISTRIBUTION_DOMAIN",
          "EvaluateTargetHealth": false
        }
      }
    }
  ]
}
EOF
          aws route53 change-resource-record-sets --hosted-zone-id $HOSTED_ZONE_ID --change-batch file:///tmp/route53-cloudfront-change.json
        fi
      fi
    fi
  fi
  
  echo "Your website is available at: http://$BUCKET_NAME.s3-website-$REGION.amazonaws.com"
  echo "Once CloudFront is deployed and DNS propagates, it will be available at: https://$DOMAIN"
else
  echo "Your website is available at: http://$BUCKET_NAME.s3-website-$REGION.amazonaws.com"
  echo ""
  if [ -z "$HOSTED_ZONE_ID" ]; then
    echo "Next steps:"
    echo "1. Configure your domain's DNS to point to the S3 website endpoint"
    echo "2. For HTTPS support, enable CloudFront by setting CLOUDFRONT_ENABLED=true"
    echo "3. Upload your website content to the S3 bucket"
  else
    echo "Next steps:"
    echo "1. For HTTPS support, enable CloudFront by setting CLOUDFRONT_ENABLED=true"
    echo "2. Upload your website content to the S3 bucket"
  fi
fi