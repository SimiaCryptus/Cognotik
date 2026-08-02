# S3 bucket to host the static website content
    resource "aws_s3_bucket" "website" {
      bucket = var.domain_name
    }

    resource "aws_s3_bucket_public_access_block" "website" {
      bucket = aws_s3_bucket.website.id

      block_public_acls       = true
      block_public_policy     = true
      ignore_public_acls      = true
      restrict_public_buckets = true
    }

    resource "aws_s3_bucket_versioning" "website" {
      bucket = aws_s3_bucket.website.id

      versioning_configuration {
        status = "Enabled"
      }
    }

    resource "aws_s3_bucket_website_configuration" "website" {
      bucket = aws_s3_bucket.website.id

      index_document {
        suffix = "index.html"
      }

      error_document {
        key = "error.html"
      }
    }

    # Bucket policy allowing CloudFront OAC access
    resource "aws_s3_bucket_policy" "website" {
      bucket = aws_s3_bucket.website.id
      policy = data.aws_iam_policy_document.s3_cloudfront.json
    }

    data "aws_iam_policy_document" "s3_cloudfront" {
      statement {
        sid     = "AllowCloudFrontServicePrincipal"
        effect  = "Allow"
        actions = ["s3:GetObject"]

        resources = ["${aws_s3_bucket.website.arn}/*"]

        principals {
          type        = "Service"
          identifiers = ["cloudfront.amazonaws.com"]
        }

        condition {
          test     = "StringEquals"
          variable = "AWS:SourceArn"
          values   = [aws_cloudfront_distribution.website.arn]
        }
      }
    }

    # Upload a default index.html so the site is functional
    resource "aws_s3_object" "index" {
      bucket       = aws_s3_bucket.website.id
      key          = "index.html"
      content      = "<html><head><title>math.cognotik.com</title></head><body><h1>Welcome to math.cognotik.com</h1></body></html>"
      content_type = "text/html"
    }

    resource "aws_s3_object" "error" {
      bucket       = aws_s3_bucket.website.id
      key          = "error.html"
      content      = "<html><head><title>Error</title></head><body><h1>Page Not Found</h1></body></html>"
      content_type = "text/html"
    }