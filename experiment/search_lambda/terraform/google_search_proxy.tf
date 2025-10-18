# terraform/google_search_proxy.tf

# KMS Key for encryption
resource "aws_kms_key" "google_api_key" {
  description             = "KMS key for Google API credentials"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  tags = {
    Name        = "cognotik-google-api-key"
    Environment = var.environment
  }
}

resource "aws_kms_alias" "google_api_key" {
  name          = "alias/cognotik-google-api"
  target_key_id = aws_kms_key.google_api_key.key_id
}

# IAM Role for Lambda
resource "aws_iam_role" "google_search_proxy" {
  name = "cognotik-google-search-proxy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# IAM Policy for KMS decryption
resource "aws_iam_role_policy" "kms_decrypt" {
  name = "kms-decrypt-policy"
  role = aws_iam_role.google_search_proxy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "kms:Decrypt"
      ]
      Resource = aws_kms_key.google_api_key.arn
    }]
  })
}

# Attach basic Lambda execution role
resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.google_search_proxy.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Lambda Function
resource "aws_lambda_function" "google_search_proxy" {
  filename         = "lambda_function.zip"
  function_name    = "cognotik-google-search-proxy"
  role            = aws_iam_role.google_search_proxy.arn
  handler         = "lambda_function.lambda_handler"
  source_code_hash = filebase64sha256("lambda_function.zip")
  runtime         = "python3.11"
  timeout         = 30
  memory_size     = 256

  environment {
    variables = {
      ENCRYPTED_GOOGLE_API_KEY    = var.encrypted_google_api_key
      ENCRYPTED_SEARCH_ENGINE_ID  = var.encrypted_search_engine_id
    }
  }

  tags = {
    Name        = "cognotik-google-search-proxy"
    Environment = var.environment
  }
}

# API Gateway
resource "aws_apigatewayv2_api" "google_search_proxy" {
  name          = "cognotik-google-search-proxy"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["*"]  # Restrict this in production
    allow_methods = ["GET", "OPTIONS"]
    allow_headers = ["content-type"]
    max_age       = 300
  }
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.google_search_proxy.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id           = aws_apigatewayv2_api.google_search_proxy.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.google_search_proxy.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "search" {
  api_id    = aws_apigatewayv2_api.google_search_proxy.id
  route_key = "GET /search"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.google_search_proxy.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.google_search_proxy.execution_arn}/*/*"
}

# Outputs
output "api_endpoint" {
  value = "${aws_apigatewayv2_api.google_search_proxy.api_endpoint}/search"
  description = "Google Search Proxy API endpoint"
}

output "kms_key_id" {
  value = aws_kms_key.google_api_key.id
  description = "KMS Key ID for encrypting credentials"
}