output "website_url" {
      description = "The HTTPS URL of the website"
      value       = "https://${var.domain_name}"
    }

    output "s3_bucket_name" {
      description = "Name of the S3 bucket hosting the website"
      value       = aws_s3_bucket.website.id
    }

    output "cloudfront_distribution_id" {
      description = "CloudFront distribution ID"
      value       = aws_cloudfront_distribution.website.id
    }

    output "cloudfront_domain_name" {
      description = "CloudFront distribution domain name"
      value       = aws_cloudfront_distribution.website.domain_name
    }

    output "acm_certificate_arn" {
      description = "ARN of the ACM certificate"
      value       = aws_acm_certificate.website.arn
    }