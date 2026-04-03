# 1. Package Lambda
cd lambda
zip lambda_function.zip lambda_function.py

# 2. Deploy infrastructure
cd ../terraform
terraform init
terraform plan
terraform apply

# 3. Get KMS key ID
KMS_KEY_ID=$(terraform output -raw kms_key_id)

# 4. Encrypt credentials
./encrypt_credentials.sh

# 5. Update terraform.tfvars with encrypted values
# 6. Apply again to update Lambda environment variables
terraform apply

# 7. Get API endpoint
terraform output api_endpoint
