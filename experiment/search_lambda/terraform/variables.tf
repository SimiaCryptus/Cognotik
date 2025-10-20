# variables.tf

variable "environment" {
  description = "Environment name (e.g., dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "encrypted_google_api_key" {
  description = "KMS-encrypted Google API key"
  type        = string
  sensitive   = true
}

variable "encrypted_search_engine_id" {
  description = "KMS-encrypted Google Search Engine ID"
  type        = string
  sensitive   = true
}

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}