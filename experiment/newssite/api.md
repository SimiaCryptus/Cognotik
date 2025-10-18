# News API Documentation

## Overview

The News API provides a comprehensive interface for accessing and searching news stories, comments, and user information. This RESTful API supports pagination, advanced search capabilities, and detailed metadata for all content types.

**Base URL:** `/api`

**Content Type:** `application/json`

**Authentication:** Not required (based on current implementation)

---

## Table of Contents

1. [Stories API](#stories-api)
2. [Comments API](#comments-api)
3. [Users API](#users-api)
4. [Data Models](#data-models)
5. [Error Handling](#error-handling)
6. [Rate Limiting](#rate-limiting)
7. [Examples](#examples)

---

## Stories API

### Get Top Stories

Retrieves a paginated list of the current top stories, ranked by score.

**Endpoint:** `GET /api/stories/top`

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | Integer | No | 1 | Page number (1-indexed) |
| `pageSize` | Integer | No | 30 | Number of items per page (1-100) |

**Response:** `PaginatedStoryIds`

```json
{
  "ids": [1234, 5678, 9012],
  "page": 1,
  "pageSize": 30,
  "totalCount": 150,
  "hasMore": true
}
```

**Example Request:**
```bash
GET /api/stories/top?page=1&pageSize=10
```

---

### Get New Stories

Retrieves a paginated list of the most recently submitted stories.

**Endpoint:** `GET /api/stories/new`

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | Integer | No | 1 | Page number (1-indexed) |
| `pageSize` | Integer | No | 30 | Number of items per page (1-100) |

**Response:** `PaginatedStoryIds`

**Example Request:**
```bash
GET /api/stories/new?page=2&pageSize=20
```

---

### Get Story Details

Retrieves complete details for a specific story.

**Endpoint:** `GET /api/stories/{id}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | Yes | Unique story identifier |

**Response:** `Story`

```json
{
  "id": 1234,
  "title": "Breaking: Major Tech Announcement",
  "url": "https://example.com/article",
  "text": null,
  "author": "techuser",
  "score": 342,
  "timestamp": "2024-01-15T10:30:00Z",
  "commentIds": [5678, 5679, 5680],
  "commentCount": 45,
  "tags": ["technology", "breaking"],
  "topics": ["tech", "business"],
  "location": "San Francisco, CA"
}
```

**Example Request:**
```bash
GET /api/stories/1234
```

**Error Responses:**
- `404 Not Found` - Story with specified ID does not exist

---

### Search Stories

Performs an advanced search for stories based on multiple criteria.

**Endpoint:** `POST /api/stories/search`

**Request Body:** `StorySearchRequest`

```json
{
  "query": "artificial intelligence",
  "tags": ["technology", "ai"],
  "topics": ["tech", "science"],
  "location": "San Francisco",
  "author": "techuser",
  "minScore": 50,
  "after": "2024-01-01T00:00:00Z",
  "before": "2024-12-31T23:59:59Z",
  "sortOrder": "SCORE",
  "page": 1,
  "pageSize": 30
}
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | String | No | Full-text search query |
| `tags` | Array[String] | No | Filter by story tags |
| `topics` | Array[String] | No | Filter by story topics |
| `location` | String | No | Filter by geographical location |
| `author` | String | No | Filter by author username |
| `minScore` | Integer | No | Minimum score threshold |
| `after` | ISO 8601 DateTime | No | Stories created after this timestamp |
| `before` | ISO 8601 DateTime | No | Stories created before this timestamp |
| `sortOrder` | String | No | Sort order: `SCORE`, `RECENT`, or `COMMENTS` |
| `page` | Integer | No | Page number (default: 1) |
| `pageSize` | Integer | No | Items per page (default: 30, max: 100) |

**Response:** `PaginatedStoryIds`

**Example Request:**
```bash
POST /api/stories/search
Content-Type: application/json

{
  "query": "machine learning",
  "tags": ["ai"],
  "minScore": 100,
  "sortOrder": "SCORE",
  "page": 1,
  "pageSize": 20
}
```

---

### Get Story Comments

Retrieves a paginated list of top-level comment IDs for a specific story.

**Endpoint:** `GET /api/stories/{id}/comments`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | Yes | Unique story identifier |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | Integer | No | 1 | Page number (1-indexed) |
| `pageSize` | Integer | No | 30 | Number of items per page (1-100) |

**Response:** `PaginatedCommentIds`

```json
{
  "ids": [5678, 5679, 5680],
  "page": 1,
  "pageSize": 30,
  "totalCount": 45,
  "hasMore": true
}
```

**Example Request:**
```bash
GET /api/stories/1234/comments?page=1&pageSize=50
```

---

## Comments API

### Get Comment Details

Retrieves complete details for a specific comment, including nested reply IDs.

**Endpoint:** `GET /api/comments/{id}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | Long | Yes | Unique comment identifier |

**Response:** `Comment`

```json
{
  "id": 5678,
  "author": "commentuser",
  "text": "<p>This is an insightful comment about the article.</p>",
  "timestamp": "2024-01-15T11:00:00Z",
  "parentId": 1234,
  "childCommentIds": [5690, 5691],
  "tags": ["insightful"],
  "topics": ["tech"],
  "location": "New York, NY"
}
```

**Example Request:**
```bash
GET /api/comments/5678
```

**Error Responses:**
- `404 Not Found` - Comment with specified ID does not exist

---

## Users API

### Get User Details

Retrieves profile information for a specific user.

**Endpoint:** `GET /api/users/{username}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `username` | String | Yes | Case-sensitive username |

**Response:** `User`

```json
{
  "id": "techuser",
  "created": "2020-05-10T08:30:00Z",
  "karma": 15420,
  "about": "Software engineer interested in AI and distributed systems.",
  "submittedIds": [1234, 5678, 9012],
  "location": "San Francisco, CA",
  "topics": ["technology", "science", "programming"],
  "tags": ["developer", "ai-enthusiast"]
}
```

**Example Request:**
```bash
GET /api/users/techuser
```

**Error Responses:**
- `404 Not Found` - User with specified username does not exist

---

### Get User Stories

Retrieves a paginated list of story IDs submitted by a specific user.

**Endpoint:** `GET /api/users/{username}/stories`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `username` | String | Yes | Case-sensitive username |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | Integer | No | 1 | Page number (1-indexed) |
| `pageSize` | Integer | No | 30 | Number of items per page (1-100) |

**Response:** `PaginatedStoryIds`

**Example Request:**
```bash
GET /api/users/techuser/stories?page=1&pageSize=20
```

---

### Get User Comments

Retrieves a paginated list of comment IDs made by a specific user.

**Endpoint:** `GET /api/users/{username}/comments`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `username` | String | Yes | Case-sensitive username |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | Integer | No | 1 | Page number (1-indexed) |
| `pageSize` | Integer | No | 30 | Number of items per page (1-100) |

**Response:** `PaginatedCommentIds`

**Example Request:**
```bash
GET /api/users/techuser/comments?page=2&pageSize=50
```

---

## Data Models

### Story

Represents a news story, which can be either a link to an external article or a self-post.

**Fields:**

| Field | Type | Required | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | Long | Yes | Unique story identifier | Must be positive |
| `title` | String | Yes | Story title | Cannot be blank |
| `url` | String | No | External article URL | Required if `text` is null |
| `text` | String | No | Self-post content | Required if `url` is null |
| `author` | String | Yes | Username of submitter | Cannot be blank |
| `score` | Integer | Yes | Story score (upvotes) | Cannot be negative |
| `timestamp` | ISO 8601 DateTime | Yes | Creation timestamp | - |
| `commentIds` | Array[Long] | Yes | Top-level comment IDs | - |
| `commentCount` | Integer | Yes | Total comment count | Cannot be negative |
| `tags` | Array[String] | Yes | Associated tags | - |
| `topics` | Array[String] | Yes | Associated topics | - |
| `location` | String | No | Geographical location | - |

**Validation Rules:**
- Either `url` or `text` must be provided (not both null)
- `id` must be a positive integer
- `score` and `commentCount` cannot be negative

---

### Comment

Represents a comment on a story or another comment.

**Fields:**

| Field | Type | Required | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | Long | Yes | Unique comment identifier | Must be positive |
| `author` | String | Yes | Username of commenter | Cannot be blank |
| `text` | String | Yes | HTML content of comment | Cannot be blank |
| `timestamp` | ISO 8601 DateTime | Yes | Creation timestamp | - |
| `parentId` | Long | Yes | Parent item ID (story or comment) | Must be positive |
| `childCommentIds` | Array[Long] | Yes | Direct reply IDs | - |
| `tags` | Array[String] | Yes | Associated tags | - |
| `topics` | Array[String] | Yes | Associated topics | - |
| `location` | String | No | Geographical location | - |

**Validation Rules:**
- `id` and `parentId` must be positive integers
- `text` is HTML-formatted content

---

### User

Represents a user account on the platform.

**Fields:**

| Field | Type | Required | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `id` | String | Yes | Unique, case-sensitive username | Cannot be blank |
| `created` | ISO 8601 DateTime | Yes | Account creation timestamp | - |
| `karma` | Integer | Yes | User's karma score | Cannot be negative |
| `about` | String | No | User bio/description | - |
| `submittedIds` | Array[Long] | Yes | IDs of submitted items | - |
| `location` | String | No | User's location | - |
| `topics` | Array[String] | Yes | User's interests | - |
| `tags` | Array[String] | Yes | User's activity tags | - |

**Validation Rules:**
- `id` (username) is case-sensitive
- `karma` cannot be negative

---

### PaginatedStoryIds

Represents a paginated list of story identifiers.

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `ids` | Array[Long] | List of story IDs for current page |
| `page` | Integer | Current page number (1-indexed) |
| `pageSize` | Integer | Number of items per page |
| `totalCount` | Integer | Total number of stories available |
| `hasMore` | Boolean | Whether additional pages exist |

---

### PaginatedCommentIds

Represents a paginated list of comment identifiers.

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `ids` | Array[Long] | List of comment IDs for current page |
| `page` | Integer | Current page number (1-indexed) |
| `pageSize` | Integer | Number of items per page |
| `totalCount` | Integer | Total number of comments available |
| `hasMore` | Boolean | Whether additional pages exist |

---

### PaginationParams

Parameters for controlling pagination in API requests.

**Fields:**

| Field | Type | Default | Description | Constraints |
|-------|------|---------|-------------|-------------|
| `page` | Integer | 1 | Page number | Must be positive |
| `pageSize` | Integer | 30 | Items per page | Must be between 1 and 100 |

---

### StorySortOrder

Enumeration of available sorting options for stories.

**Values:**

| Value | Description |
|-------|-------------|
| `SCORE` | Sort by score (highest first) |
| `RECENT` | Sort by timestamp (newest first) |
| `COMMENTS` | Sort by comment count (most commented first) |

---

## Error Handling

The API uses standard HTTP status codes to indicate success or failure.

### HTTP Status Codes

| Code | Description | When Used |
|------|-------------|-----------|
| `200 OK` | Request succeeded | Successful GET, POST requests |
| `400 Bad Request` | Invalid request parameters | Validation failures, invalid pagination |
| `404 Not Found` | Resource not found | Story, comment, or user doesn't exist |
| `500 Internal Server Error` | Server error | Unexpected server-side errors |

### Error Response Format

```json
{
  "timestamp": "2024-01-15T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Page must be positive",
  "path": "/api/stories/top"
}
```

### Common Error Scenarios

#### Invalid Pagination
```json
{
  "status": 400,
  "message": "Page size must be between 1 and 100"
}
```

#### Resource Not Found
```json
{
  "status": 404,
  "message": "Story with ID 99999 not found"
}
```

#### Validation Errors
```json
{
  "status": 400,
  "message": "Story ID must be positive"
}
```

---

## Rate Limiting

**Note:** Rate limiting details are not specified in the current implementation. Consider implementing rate limiting for production use.

**Recommended Headers:**
- `X-RateLimit-Limit`: Maximum requests per time window
- `X-RateLimit-Remaining`: Remaining requests in current window
- `X-RateLimit-Reset`: Time when the rate limit resets

---

## Examples

### Example 1: Fetching Top Stories and Their Details

```bash
# Step 1: Get top story IDs
curl -X GET "http://localhost:8080/api/stories/top?page=1&pageSize=5"

# Response:
{
  "ids": [1234, 5678, 9012, 3456, 7890],
  "page": 1,
  "pageSize": 5,
  "totalCount": 150,
  "hasMore": true
}

# Step 2: Get details for first story
curl -X GET "http://localhost:8080/api/stories/1234"

# Response:
{
  "id": 1234,
  "title": "Revolutionary AI Breakthrough",
  "url": "https://example.com/ai-breakthrough",
  "author": "researcher",
  "score": 542,
  "timestamp": "2024-01-15T09:00:00Z",
  "commentIds": [5678, 5679],
  "commentCount": 87,
  "tags": ["ai", "research"],
  "topics": ["technology", "science"],
  "location": "Boston, MA"
}
```

---

### Example 2: Advanced Story Search

```bash
curl -X POST "http://localhost:8080/api/stories/search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "machine learning",
    "tags": ["ai", "ml"],
    "topics": ["technology"],
    "minScore": 100,
    "after": "2024-01-01T00:00:00Z",
    "sortOrder": "SCORE",
    "page": 1,
    "pageSize": 10
  }'

# Response:
{
  "ids": [1234, 5678, 9012],
  "page": 1,
  "pageSize": 10,
  "totalCount": 23,
  "hasMore": true
}
```

---

### Example 3: Fetching User Profile and Activity

```bash
# Get user profile
curl -X GET "http://localhost:8080/api/users/techuser"

# Response:
{
  "id": "techuser",
  "created": "2020-05-10T08:30:00Z",
  "karma": 15420,
  "about": "Software engineer and AI enthusiast",
  "submittedIds": [1234, 5678],
  "location": "San Francisco, CA",
  "topics": ["technology", "ai"],
  "tags": ["developer"]
}

# Get user's stories
curl -X GET "http://localhost:8080/api/users/techuser/stories?page=1&pageSize=10"

# Get user's comments
curl -X GET "http://localhost:8080/api/users/techuser/comments?page=1&pageSize=10"
```

---

### Example 4: Navigating Comment Threads

```bash
# Get story comments
curl -X GET "http://localhost:8080/api/stories/1234/comments?page=1&pageSize=10"

# Response:
{
  "ids": [5678, 5679, 5680],
  "page": 1,
  "pageSize": 10,
  "totalCount": 87,
  "hasMore": true
}

# Get first comment details
curl -X GET "http://localhost:8080/api/comments/5678"

# Response:
{
  "id": 5678,
  "author": "commentuser",
  "text": "<p>Great article! Here are my thoughts...</p>",
  "timestamp": "2024-01-15T10:00:00Z",
  "parentId": 1234,
  "childCommentIds": [5690, 5691],
  "tags": ["insightful"],
  "topics": ["technology"],
  "location": null
}

# Get nested reply
curl -X GET "http://localhost:8080/api/comments/5690"
```

---

### Example 5: Pagination Through Results

```bash
# Page 1
curl -X GET "http://localhost:8080/api/stories/new?page=1&pageSize=20"

# Check hasMore field in response
{
  "ids": [...],
  "page": 1,
  "pageSize": 20,
  "totalCount": 500,
  "hasMore": true
}

# Page 2
curl -X GET "http://localhost:8080/api/stories/new?page=2&pageSize=20"

# Continue until hasMore is false
```

---

## Best Practices

### 1. Pagination
- Always use pagination for list endpoints
- Start with reasonable page sizes (30 is default)
- Check `hasMore` field to determine if more pages exist
- Calculate total pages: `ceil(totalCount / pageSize)`

### 2. Caching
- Cache story and user details when possible
- Use ETags or Last-Modified headers (if implemented)
- Implement client-side caching for frequently accessed data

### 3. Error Handling
- Always check HTTP status codes
- Parse error messages for user-friendly display
- Implement retry logic for 5xx errors
- Validate input before making requests

### 4. Search Optimization
- Use specific filters to narrow results
- Combine multiple search criteria for precision
- Use appropriate sort orders for your use case
- Implement debouncing for search-as-you-type features

### 5. Performance
- Batch requests when fetching multiple items
- Use appropriate page sizes (larger for background jobs, smaller for UI)
- Implement request throttling on client side
- Consider using WebSocket or Server-Sent Events for real-time updates (if available)

---

## Changelog

### Version 1.0.0 (Current)
- Initial API release
- Support for stories, comments, and users
- Advanced search functionality
- Pagination support
- Tag and topic filtering

---

## Support

For API support, bug reports, or feature requests, please contact:
- **Email:** api-support@example.com
- **Documentation:** https://docs.example.com/api
- **Status Page:** https://status.example.com

---

## License

This API documentation is provided for the News API service. All rights reserved.