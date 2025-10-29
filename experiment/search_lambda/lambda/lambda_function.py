# lambda_function.py
import base64
import boto3
import json
import os
import urllib.parse
import urllib.request
from botocore.exceptions import ClientError

# Initialize KMS client
kms = boto3.client('kms')


def decrypt_value(encrypted_value):
    """Decrypt a KMS-encrypted value"""
    try:
        # Decode from base64 (standard KMS format)
        ciphertext_blob = base64.b64decode(encrypted_value)

        response = kms.decrypt(
            CiphertextBlob=ciphertext_blob
        )
        return response['Plaintext'].decode('utf-8')
    except ClientError as e:
        print(f"Decryption failed: {e}")
        raise


def lambda_handler(event, context):
    """
    Proxy Google Custom Search API requests
    Expected query parameters:
    - q: search query (required)
    - num: number of results (optional, default 10, max 10)
    - start: starting index (optional, for pagination)
    """

    # CORS headers
    headers = {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Allow-Methods': 'GET, OPTIONS'
    }

    # Handle preflight
    if event.get('httpMethod') == 'OPTIONS':
        return {
            'statusCode': 200,
            'headers': headers,
            'body': ''
        }

    try:
        # Get encrypted credentials from environment variables
        encrypted_api_key = os.environ['ENCRYPTED_GOOGLE_API_KEY']
        encrypted_engine_id = os.environ['ENCRYPTED_SEARCH_ENGINE_ID']

        # Decrypt credentials
        api_key = decrypt_value(encrypted_api_key)
        engine_id = decrypt_value(encrypted_engine_id)

        # Parse query parameters
        params = event.get('queryStringParameters', {})

        if not params or 'q' not in params:
            return {
                'statusCode': 400,
                'headers': headers,
                'body': json.dumps({'error': 'Missing required parameter: q'})
            }

        query = params['q']
        num_results = int(params.get('num', 10))  # Cap at 10
        start_index = params.get('start', '1')

        # Build Google API URL
        google_params = {
            'key': api_key,
            'cx': engine_id,
            'q': query,
            'num': str(num_results),
            'start': start_index
        }

        url = 'https://www.googleapis.com/customsearch/v1?' + urllib.parse.urlencode(google_params)

        # Make request to Google
        req = urllib.request.Request(
            url,
            headers={'User-Agent': 'CognoTik-Proxy/1.0'}
        )

        with urllib.request.urlopen(req, timeout=30) as response:
            data = response.read()

        return {
            'statusCode': 200,
            'headers': headers,
            'body': data.decode('utf-8')
        }

    except urllib.error.HTTPError as e:
        error_body = e.read().decode('utf-8')
        print(f"Google API error: {e.code} - {error_body}")
        return {
            'statusCode': e.code,
            'headers': headers,
            'body': json.dumps({
                'error': f'Google API error: {e.code}',
                'details': error_body
            })
        }

    except Exception as e:
        print(f"Lambda error: {str(e)}")
        return {
            'statusCode': 500,
            'headers': headers,
            'body': json.dumps({'error': str(e)})
        }
