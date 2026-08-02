# Alias record pointing the domain to CloudFront
    resource "aws_route53_record" "website" {
      zone_id = var.hosted_zone_id
      name    = var.domain_name
      type    = "A"

      alias {
        name                   = aws_cloudfront_distribution.website.domain_name
        zone_id                = aws_cloudfront_distribution.website.hosted_zone_id
        evaluate_target_health = false
      }
    }

    # IPv6 alias record
    resource "aws_route53_record" "website_ipv6" {
      zone_id = var.hosted_zone_id
      name    = var.domain_name
      type    = "AAAA"

      alias {
        name                   = aws_cloudfront_distribution.website.domain_name
        zone_id                = aws_cloudfront_distribution.website.hosted_zone_id
        evaluate_target_health = false
      }
    }