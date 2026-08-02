variable "domain_name" {
      description = "The full domain name for the website"
      type        = string
      default     = "math.cognotik.com"
    }

    variable "hosted_zone_id" {
      description = "Route53 hosted zone ID"
      type        = string
      default     = "Z07503642SI78TKFLN23D"
    }

    variable "hosted_zone_name" {
      description = "Route53 hosted zone name"
      type        = string
      default     = "cognotik.com"
    }

    variable "aws_region" {
      description = "Primary AWS region for resources"
      type        = string
      default     = "us-east-1"
    }