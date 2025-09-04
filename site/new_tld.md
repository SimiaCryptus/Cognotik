# AWS S3 Static Website Deployment Script Documentation

## Overview

The `new_tld.sh` script is a comprehensive bash script that automates the creation and configuration of a static website
hosted on AWS S3, with optional CloudFront CDN distribution and Route53 DNS management. This script handles the entire
setup process from S3 bucket creation to SSL certificate provisioning and DNS configuration.

## Prerequisites

### Required Tools

- **AWS CLI**: Must be installed and configured with appropriate credentials
- **Bash**: Unix/Linux shell environment
- **jq**: JSON processor (recommended for debugging)

### Required AWS Permissions

The script requires the following AWS IAM permissions:

#### S3 Permissions

- `s3:CreateBucket`
- `s3:PutBucketPolicy`
- `s3:PutBucketWebsite`
- `s3:PutPublicAccessBlock`
- `s3:PutObject`

#### Route53 Permissions

- `route53:ListHostedZonesByName`
- `route53:CreateHostedZone`
- `route53:GetHostedZone`
- `route53:ChangeResourceRecordSets`

#### CloudFront Permissions

- `cloudfront:CreateDistribution`
- `cloudfront:GetDistribution`

#### ACM (AWS Certificate Manager) Permissions

- `acm:RequestCertificate`
- `acm:DescribeCertificate`

## Usage

### Basic Syntax

```bash
./new_tld.sh <domain-name>
```

### Examples

```bash
# Create a website for example.com
./new_tld.sh example.com

# Create a website for myblog.net
./new_tld.sh myblog.net
```

## Configuration Variables

The script includes several configurable variables at the top:

| Variable             | Default Value | Description                            |
|----------------------|---------------|----------------------------------------|
| `REGION`             | `us-east-1`   | AWS region for S3 bucket creation      |
| `CLOUDFRONT_ENABLED` | `true`        | Enable/disable CloudFront distribution |

## Script Workflow

### 1. Input Validation

- Checks if domain name is provided as argument
- Exits with error message if no domain is specified

### 2. S3 Bucket Setup

- Creates S3 bucket with domain name
- Disables block public access settings
- Enables static website hosting
- Applies public read bucket policy
- Uploads sample `index.html` and `error.html` files

### 3. Route53 DNS Configuration

- Searches for existing hosted zone for the domain
- Optionally creates new hosted zone if none exists
- Creates A record pointing to S3 website endpoint
- Creates CNAME record for www subdomain

### 4. CloudFront Distribution (Optional)

- Requests SSL certificate from AWS Certificate Manager (ACM)
- Adds certificate validation records to Route53
- Creates CloudFront distribution with HTTPS redirect
- Updates Route53 records to point to CloudFront

## Generated Files

The script creates several temporary files during execution:

| File                                | Purpose                                 |
|-------------------------------------|-----------------------------------------|
| `/tmp/bucket-policy.json`           | S3 bucket policy for public read access |
| `/tmp/index.html`                   | Sample homepage                         |
| `/tmp/error.html`                   | Sample 404 error page                   |
| `/tmp/route53-change-batch.json`    | Route53 DNS record changes              |
| `/tmp/validation-change-batch.json` | Certificate validation records          |
| `/tmp/cloudfront-config.json`       | CloudFront distribution configuration   |

## Output Information

### Successful Execution

Upon successful completion, the script provides:

- S3 website endpoint URL
- CloudFront distribution ID (if enabled)
- HTTPS URL (if CloudFront is enabled)
- Next steps for content upload

### Error Handling

The script includes error handling for:

- Missing domain argument
- Failed bucket policy application
- DNS configuration failures
- Certificate request failures

## Security Considerations

### Public Access

- The script disables S3 block public access settings
- Applies a bucket policy allowing public read access
- This makes all bucket content publicly accessible

### SSL/TLS

- When CloudFront is enabled, enforces HTTPS redirects
- Uses TLS 1.2 as minimum protocol version
- Implements SNI-only SSL support for cost optimization

## Troubleshooting

### Common Issues

#### 1. Bucket Policy Application Failure

**Symptoms**: Warning about failed bucket policy application
**Solution**:

- Manually disable Block Public Access in AWS Console
- Re-run the script

#### 2. Route53 Hosted Zone Not Found

**Symptoms**: No hosted zone found message
**Solution**:

- Choose to create new hosted zone when prompted
- Update nameservers at domain registrar

#### 3. Certificate Validation Timeout

**Symptoms**: Certificate remains in "Pending Validation" status
**Solution**:

- Verify DNS records were created correctly
- Wait up to 30 minutes for validation
- Check domain ownership

### Manual Verification Steps

1. **Verify S3 Website**: Visit `http://<bucket-name>.s3-website-<region>.amazonaws.com`
2. **Check DNS Propagation**: Use `dig` or `nslookup` to verify DNS records
3. **Verify SSL Certificate**: Check certificate status in ACM console
4. **Test CloudFront**: Verify distribution status and test HTTPS access

## Cost Implications

### AWS Services Used

- **S3**: Storage and requests
- **Route53**: Hosted zone ($0.50/month) and queries
- **CloudFront**: Data transfer and requests
- **ACM**: SSL certificates (free for CloudFront)

### Cost Optimization Tips

- Use CloudFront for better performance and lower S3 request costs
- Monitor CloudFront usage to avoid unexpected charges
- Consider S3 Intelligent Tiering for infrequently accessed content

## Customization Options

### Modifying Default Files

Edit the heredoc sections to customize:

- Default homepage content
- Error page styling
- Meta tags and SEO elements

### Adding Custom Domains

The script supports:

- Root domain (example.com)
- www subdomain (www.example.com)
- Additional subdomains can be added by modifying the CloudFront aliases

### Regional Considerations

- Change `REGION` variable for different AWS regions
- Note: ACM certificates for CloudFront must be in `us-east-1`

## Best Practices

1. **Backup**: Always backup existing DNS settings before running
2. **Testing**: Test in a development environment first
3. **Monitoring**: Set up CloudWatch alarms for cost monitoring
4. **Security**: Regularly review bucket policies and access logs
5. **Performance**: Use CloudFront for global content delivery

## Support and Maintenance

### Regular Tasks

- Monitor SSL certificate expiration (auto-renewed by ACM)
- Review CloudFront cache behaviors
- Update content and maintain website files
- Monitor AWS costs and usage

### Script Updates

The script can be enhanced to support:

- Multiple domain aliases
- Custom SSL certificates
- Advanced CloudFront configurations
- Automated content deployment