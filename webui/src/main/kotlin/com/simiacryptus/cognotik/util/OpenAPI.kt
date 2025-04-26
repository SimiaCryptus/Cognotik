package com.simiacryptus.cognotik.util

import com.fasterxml.jackson.annotation.JsonInclude

// OpenAPI root document
data class OpenAPI(
    val openapi: String = "3.0.0",
    val info: Info? = null,
    val paths: Map<String, PathItem>? = emptyMap(),
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val components: Components? = null
)

// Metadata about the API
data class Info(
    val title: String? = null,
    val version: String? = null,
    val description: String? = null,
    val termsOfService: String? = null,
    val contact: Contact? = null,
    val license: License? = null
)

// Contact information
data class Contact(
    val name: String? = null,
    val url: String? = null,
    val email: String? = null
)

// License information
data class License(
    val name: String? = null,
    val url: String? = null
)

// Paths and operations
data class PathItem(
    val get: Operation? = null,
    val put: Operation? = null,
    val post: Operation? = null,
    val delete: Operation? = null,
    val options: Operation? = null,
    val head: Operation? = null,
    val patch: Operation? = null
)

// An API operation
data class Operation(
    val summary: String? = null,
    val description: String? = null,
    val responses: Map<String, Response>? = emptyMap(),
    val parameters: List<Parameter>? = emptyList(),
    val operationId: String? = null,
    val requestBody: RequestBody? = null,
    val security: List<Map<String, List<String>>>? = emptyList(),
    val tags: List<String>? = emptyList(),
    val callbacks: Map<String, Callback>? = emptyMap(),
    val deprecated: Boolean? = null,
)

// Operation response
data class Response(
    val description: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val content: Map<String, MediaType>? = emptyMap()
)

// Components for reusable objects
data class Components(
    val schemas: Map<String, Schema>? = emptyMap(),
    val responses: Map<String, Response>? = emptyMap(),
    val parameters: Map<String, Parameter>? = emptyMap(),
    val examples: Map<String, Example>? = emptyMap(),
    val requestBodies: Map<String, RequestBody>? = emptyMap(),
    val headers: Map<String, Header>? = emptyMap(),
    val securitySchemes: Map<String, SecurityScheme>? = emptyMap(),
    val links: Map<String, Link>? = emptyMap(),
    val callbacks: Map<String, Callback>? = emptyMap()
)

// Simplified examples of component objects
data class Schema(
    val type: String? = null,
    val properties: Map<String, Schema>? = emptyMap(),
    val items: Schema? = null,
    val `$ref`: String? = null,
    val format: String? = null,
    val description: String? = null,

    )

data class Parameter(
    val name: String? = null,
    val `in`: String? = null,
    val description: String? = null,
    val required: Boolean? = null,
    val schema: Schema? = null,
    val content: Map<String, MediaType>? = null,
    val example: Any? = null,
)

data class Example(val summary: String? = null, val description: String? = null)
data class RequestBody(val description: String? = null, val content: Map<String, MediaType>? = null)
data class Header(val description: String? = null)
data class SecurityScheme(val type: String? = null, val description: String? = null)
data class Link(val operationId: String? = null)
data class Callback(val expression: String? = null)
data class MediaType(val schema: Schema? = null)

