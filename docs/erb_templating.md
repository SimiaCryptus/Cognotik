---
documents:
  - ../core/src/test/kotlin/com/simiacryptus/cognotik/util/ErbTemplateEngineTest.kt
  - ../core/src/main/kotlin/com/simiacryptus/cognotik/util/ErbTemplateEngine.kt
specifies: ../site/cognotik.com/erb_templating.html
---

# ERB Template Engine Documentation

## Overview

The `ErbTemplateEngine` is a powerful, Ruby ERB-inspired templating engine written in Kotlin. It provides a flexible way to generate dynamic text content (particularly LaTeX documents) from JSON data. The engine supports variable interpolation, control structures, filters, custom functions, and optional type-safe schema validation.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Basic Syntax](#basic-syntax)
3. [Variable Interpolation](#variable-interpolation)
4. [Filters](#filters)
5. [Control Structures](#control-structures)
6. [Custom Functions](#custom-functions)
7. [Type Schema and Validation](#type-schema-and-validation)
8. [LaTeX-Specific Features](#latex-specific-features)
9. [API Reference](#api-reference)
10. [Best Practices](#best-practices)
11. [Examples](#examples)

---

## Getting Started

### Basic Usage

```kotlin
import com.simiacryptus.cognotik.util.ErbTemplateEngine
import com.google.gson.JsonObject

val engine = ErbTemplateEngine()

val template = "Hello, <%= name %>!"
val data = JsonObject().apply {
    addProperty("name", "World")
}

val result = engine.render(template, data)
// Output: "Hello, World!"
```

### Input Data Format

The engine accepts data as a `JsonObject` from the Gson library. Data can include:

- **Primitives**: strings, numbers, booleans
- **Objects**: nested JSON objects
- **Arrays**: JSON arrays for iteration

```kotlin
val data = JsonObject().apply {
    addProperty("title", "My Document")
    addProperty("count", 42)
    addProperty("active", true)
    add("user", JsonObject().apply {
        addProperty("name", "John")
        addProperty("email", "john@example.com")
    })
    add("items", JsonArray().apply {
        add("Item 1")
        add("Item 2")
        add("Item 3")
    })
}
```

---

## Basic Syntax

The engine uses ERB-style delimiters:

| Syntax | Purpose | Example |
|--------|---------|---------|
| `<%= expr %>` | Output expression | `<%= name %>` |
| `<% code %>` | Control structure | `<% if active %>` |
| `<%# comment %>` | Comment (in preamble) | `<%# @type ... %>` |

### Whitespace Handling

Whitespace inside tags is flexible:

```erb
<%=name%>           <%-- Works --%>
<%= name %>         <%-- Works --%>
<%=    name    %>   <%-- Works --%>
```

---

## Variable Interpolation

### Simple Variables

Access top-level variables directly:

```erb
<%= title %>
<%= count %>
<%= active %>
```

### Nested Object Access

Use dot notation to access nested properties:

```erb
<%= user.name %>
<%= user.email %>
<%= config.settings.theme %>
```

### Missing Variables

Missing variables render as empty strings (no error):

```erb
<%= nonexistent %>  <%-- Outputs: "" --%>
```

---

## Filters

Filters transform values using the pipe (`|`) syntax. Multiple filters can be chained.

### Syntax

```erb
<%= value | filter %>
<%= value | filter:argument %>
<%= value | filter1 | filter2 | filter3 %>
```

### Built-in Filters

#### `escape`
Escapes special LaTeX characters.

```erb
<%= text | escape %>
```

| Input | Output               |
|-------|----------------------|
| `\`   | `\textbackslash{}`   |
| `{`   | `\{`                 |
| `}`   | `\}`                 |
| `$`   | `\$`                 |
| `&`   | `\&`                 |
| `%`   | `\%`                 |
| `#`   | `\#`                 |
| `_`   | `\_`                 |
| `~`   | `\textasciitilde{}`  |
| `^`   | `\textasciicircum{}` |
| `<`   | `\textless{}`        |
| `>`   | `\textgreater{}`     |

#### `markdown`
Converts basic Markdown to LaTeX.

```erb
<%= text | markdown %>
```

| Markdown      | LaTeX              |
|---------------|--------------------|
| `**bold**`    | `\textbf{bold}`    |
| `*italic*`    | `\textit{italic}`  |
| `` `code` ``  | `\texttt{code}`    |
| `[text](url)` | `\href{url}{text}` |

#### `upper`
Converts text to uppercase.

```erb
<%= name | upper %>
<%-- "hello" → "HELLO" --%>
```

#### `lower`
Converts text to lowercase.

```erb
<%= name | lower %>
<%-- "HELLO" → "hello" --%>
```

#### `join`
Joins array elements with a separator.

```erb
<%= items | join %>           <%-- Default: ", " --%>
<%= items | join:' - ' %>     <%-- Custom separator --%>
<%= items | join:'\n' %>      <%-- Newline separator --%>
```

#### `default`
Provides a fallback value for empty or missing values.

```erb
<%= name | default:'Unknown' %>
<%= color | default:"20, 20, 35" %>
```

### Chaining Filters

Filters are applied left to right:

```erb
<%= name | upper | escape %>
<%-- "hello & world" → "HELLO \& WORLD" --%>
```

### Custom Filters

Register custom filters programmatically:

```kotlin
engine.registerFilter("reverse") { value, args ->
    value?.toString()?.reversed() ?: ""
}

engine.registerFilter("truncate") { value, args ->
    val maxLength = args.firstOrNull()?.toIntOrNull() ?: 50
    val text = value?.toString() ?: ""
    if (text.length > maxLength) {
        text.take(maxLength) + "..."
    } else {
        text
    }
}
```

Usage:

```erb
<%= text | reverse %>
<%= description | truncate:100 %>
```

---

## Control Structures

### If Statements

#### Basic If

```erb
<% if condition %>
    Content when true
<% end %>
```

#### If-Else

```erb
<% if condition %>
    Content when true
<% else %>
    Content when false
<% end %>
```

#### Alternative End Tag

```erb
<% if condition %>
    Content
<% endif %>
```

### Truthiness Rules

| Value Type | Truthy        | Falsy        |
|------------|---------------|--------------|
| Boolean    | `true`        | `false`      |
| String     | Non-empty     | Empty `""`   |
| Number     | Always truthy | -            |
| Array      | Non-empty     | Empty `[]`   |
| Object     | Non-empty     | Empty `{}`   |
| Null       | -             | Always falsy |

### Comparison Operators

#### Equality

```erb
<% if status == "active" %>Active<% end %>
<% if status == 'active' %>Active<% end %>  <%-- Single quotes work too --%>
```

#### Inequality

```erb
<% if status != "inactive" %>Not Inactive<% end %>
```

#### Negation

```erb
<% if !hidden %>Visible<% end %>
```

#### Arithmetic in Conditions

Simple arithmetic expressions (like modulo) can be used in conditions:

```erb
<% if loop.index % 2 == 0 %>Even row<% end %>
```

### Nested Conditions

```erb
<% if user %>
    <% if user.admin %>
        Admin: <%= user.name %>
    <% else %>
        User: <%= user.name %>
    <% end %>
<% end %>
```

### For Loops

#### Iterating Over Arrays

```erb
<% for item in items %>
    <%= item %>
<% end %>
```

#### Alternative End Tag

```erb
<% for item in items %>
    <%= item %>
<% endfor %>
```

### Loop Variables

Inside a loop, a `loop` object provides metadata:

| Variable | Description |
|----------|-------------|
| `loop.index` | Current index (0-based) |
| `loop.first` | `true` if first iteration |
| `loop.last` | `true` if last iteration |

```erb
<% for item in items %>
    <%= loop.index %>: <%= item %><% if !loop.last %>, <% end %>
<% end %>
```

#### Using Loop Index in Conditions

The loop index can be used in arithmetic expressions:

```erb
<% for item in items %>
<% if loop.index % 2 == 0 %>Even: <%= item %><% else %>Odd: <%= item %><% end %>
<% end %>
```

#### Iterating Over Objects

When iterating over an object, each entry has `key` and `value` properties:

```erb
<% for entry in settings %>
    <%= entry.key %> = <%= entry.value %>
<% end %>
```

#### Nested Loops

```erb
<% for row in matrix %>
    <% for cell in row %>
        <%= cell %>
    <% end %>
<% end %>
```

### Combining Loops and Conditionals

#### If-Else Inside For Loops

```erb
<% for item in items %>
<% if item.active %>Active: <%= item.name %><% else %>Inactive: <%= item.name %><% end %>
<% end %>
```

#### For Loops Inside Conditionals

```erb
<% if showItems %>
<% for item in items %><%= item %> <% end %>
<% end %>
```

#### Complex Nested Structures

```erb
<% if enabled %>
<% for item in items %>
<% if item.type == "special" %>[<%= item.name %>]<% else %><%= item.name %><% end %>
<% end %>
<% end %>
```

---

## Custom Functions

Define reusable functions using Groovy syntax within templates.

### Defining Functions

```erb
<% def functionName(param1, param2) %>
    // Groovy code here
    return result
<% enddef %>
```

### Basic Function Example

```erb
<% def greet(name) %>
return "Hello, " + name + "!"
<% enddef %>

<%= greet("World") %>
<%-- Output: Hello, World! --%>
```

### Functions with Multiple Parameters

```erb
<% def formatName(first, last) %>
return last + ", " + first
<% enddef %>

<%= formatName("John", "Doe") %>
<%-- Output: Doe, John --%>
```

### Functions with No Parameters

```erb
<% def getCurrentYear() %>
return java.time.Year.now().getValue()
<% enddef %>

Copyright <%= getCurrentYear() %>
```

### Using Functions as Filters

Defined functions are automatically available as filters:

```erb
<% def double(x) %>
return x * 2
<% enddef %>

<%= value | double %>
```

### Functions with Groovy Features

Functions can use full Groovy capabilities:

```erb
<% def capitalize(text) %>
return text.split(" ").collect { it.capitalize() }.join(" ")
<% enddef %>

<%= capitalize("hello world") %>
<%-- Output: Hello World --%>
```

### Conditional Logic in Functions

```erb
<% def grade(score) %>
if (score >= 90) return "A"
else if (score >= 80) return "B"
else if (score >= 70) return "C"
else return "F"
<% enddef %>

Grade: <%= grade(85) %>
<%-- Output: Grade: B --%>
```

### Accessing Global Data from Functions

Functions can access the template's global data through the `data` variable:

```erb
<% def greetWithTitle(name) %>
return data.title + " " + name
<% enddef %>
<%= greetWithTitle("World") %>
<%-- With data.title = "Hello", Output: Hello World --%>
```

Access nested data:

```erb
<% def formatUser(prefix) %>
return prefix + ": " + data.user.firstName + " " + data.user.lastName
<% enddef %>
<%= formatUser("Name") %>
<%-- Output: Name: John Doe --%>
```

Combine with filter values:

```erb
<% def appendSuffix(text) %>
return text + data.suffix
<% enddef %>
<%= name | appendSuffix %>
<%-- With data.suffix = "!", "Hello" becomes "Hello!" --%>
```

### List Operations

```erb
<% def sum(items) %>
return items.sum()
<% enddef %>

Total: <%= numbers | sum %>
```

### Using Functions in For Loops

Functions work seamlessly within iteration contexts:

```erb
<% def format(item) %>
return "[" + item + "]"
<% enddef %>
<% for item in items %><%= item | format %><% if !loop.last %> <% end %><% end %>
<%-- Output: [a] [b] [c] --%>
```

### Chaining Functions with Filters

Custom functions can be chained with built-in filters:

```erb
<% def prefix(text) %>
return "PREFIX_" + text
<% enddef %>
<%= name | prefix | upper %>
<%-- "test" becomes "PREFIX_TEST" --%>
```

---

## Type Schema and Validation

The engine supports TypeScript-style type declarations for documenting and validating template data.

### Schema Preamble Syntax

Place the schema at the beginning of the template:

```erb
---
<%#
@type TemplateData = {
    fieldName: type;
    optionalField?: type;
};
%>
---
Template content here...
```

### Supported Types

| Type             | Description     | Example                   |
|------------------|-----------------|---------------------------|
| `string`         | Text values     | `name: string;`           |
| `number`         | Numeric values  | `age: number;`            |
| `boolean`        | True/false      | `active: boolean;`        |
| `any`            | Any value       | `data: any;`              |
| `type[]`         | Array of type   | `items: string[];`        |
| `Array<type>`    | Array (generic) | `items: Array<number>;`   |
| `{ ... }`        | Nested object   | `user: { name: string };` |
| `type1 \| type2` | Union type      | `id: string \| number;`   |

### Complete Schema Example

```erb
---
<%#
@type TemplateData = {
    // Document metadata
    title: string;
    author?: string;
    date?: string;

    // Content
    sections: Array<{
        title: string;
        content: string;
        subsections?: { title: string; content: string }[];
    }>;

    // Settings
    settings: {
        theme: string;
        fontSize: number;
        showToc: boolean;
    };

    // Tags can be strings or numbers
    tags?: string[];
};
%>
---
\documentclass{article}
\title{<%= title %>}
...
```

### Enabling Strict Validation

By default, validation errors are collected but don't stop rendering. Enable strict mode to throw exceptions:

```kotlin
val engine = ErbTemplateEngine()
engine.strictValidation = true

try {
    engine.render(template, data)
} catch (e: ErbTemplateEngine.TemplateValidationException) {
    println("Validation errors:")
    e.errors.forEach { error ->
        println("  ${error.path}: ${error.message}")
    }
}
```

### Extracting Schema

Extract the schema without rendering:

```kotlin
val schema = engine.extractSchema(template)
if (schema != null) {
    println(schema.toTypeScript())
}
```

### Generated TypeScript Interface

```kotlin
val typescript = schema.toTypeScript()
// Output:
// interface TemplateData {
//   title: string;
//   author?: string;
//   sections: { title: string; content: string }[];
// }
```

---

## LaTeX-Specific Features

### Escaping for LaTeX

Always use the `escape` filter for user-provided content:

```erb
\section{<%= title | escape %>}
<%= content | escape %>
```

### Markdown in LaTeX

Convert Markdown formatting to LaTeX:

```erb
<%= description | markdown %>
```

### Common Patterns

#### Document Structure

```erb
\documentclass{article}
\usepackage{hyperref}

\title{<%= title | escape %>}
\author{<%= author | escape %>}
\date{<%= date | default:'\today' %>}

\begin{document}
\maketitle

<% if showToc %>
\tableofcontents
<% end %>

<% for section in sections %>
\section{<%= section.title | escape %>}
<%= section.content | markdown %>
<% end %>

\end{document}
```

#### Handling LaTeX Environments in Loops

LaTeX `\begin{...}` and `\end{...}` environments work correctly within template loops and conditionals:

```erb
<% for job in experience %>
\begin{infocard}
\textbf{<%= job.position %>}
<% if job.highlights %>
\begin{itemize}
<% for highlight in job.highlights %>
\item <%= highlight %>
<% end %>
\end{itemize}
<% end %>
\end{infocard}
<% end %>
```

The engine correctly distinguishes between LaTeX's `\end{...}` commands and the template's `<% end %>` tags.

#### Tables

```erb
\begin{tabular}{|l|r|}
\hline
\textbf{Name} & \textbf{Value} \\
\hline
<% for row in data %>
<%= row.name | escape %> & <%= row.value | escape %> \\
<% end %>
\hline
\end{tabular}
```

#### Lists

```erb
\begin{itemize}
<% for item in items %>
    \item <%= item | escape %>
<% end %>
\end{itemize}
```

#### Conditional Sections

```erb
<% if abstract %>
\begin{abstract}
<%= abstract | escape %>
\end{abstract}
<% end %>
```

---

## API Reference

### ErbTemplateEngine Class

#### Constructor

```kotlin
val engine = ErbTemplateEngine()
```

#### Properties

| Property           | Type      | Default | Description                          |
|--------------------|-----------|---------|--------------------------------------|
| `strictValidation` | `Boolean` | `false` | Throw exception on validation errors |

#### Methods

##### `render(template: String, data: JsonObject): String`

Renders a template with the provided data.

```kotlin
val result = engine.render(template, data)
```

##### `registerFilter(name: String, filter: (Any?, List<String>) -> String)`

Registers a custom filter.

```kotlin
engine.registerFilter("myFilter") { value, args ->
    // Transform value
    value?.toString() ?: ""
}
```

##### `extractSchema(template: String): TypeSchema?`

Extracts the type schema from a template's preamble.

```kotlin
val schema = engine.extractSchema(template)
```

##### `callFunction(functionName: String, firstArg: Any?, additionalArgs: List<String>): String`

Calls a defined function programmatically.

```kotlin
val result = engine.callFunction("greet", "World", emptyList())
```

### TypeSchema Class

#### Methods

##### `toTypeScript(): String`

Generates a TypeScript interface definition.

```kotlin
val typescript = schema.toTypeScript()
```

### FieldType Sealed Class

Represents field types in the schema:

- `StringType`
- `NumberType`
- `BooleanType`
- `AnyType`
- `ArrayType`
- `ObjectType`
- `UnionType`
- `CustomType`

### ValidationError Data Class

```kotlin
data class ValidationError(
    val path: String,    // e.g., "user.profile.name"
    val message: String  // e.g., "Required field is missing"
)
```

### TemplateValidationException

Thrown when strict validation fails:

```kotlin
class TemplateValidationException(val errors: List<ValidationError>)
```

---

## Best Practices

### 1. Always Escape User Content

```erb
<%-- Good --%>
<%= userInput | escape %>

<%-- Bad - potential LaTeX injection --%>
<%= userInput %>
```

### 2. Use Default Values

```erb
<%-- Good --%>
<%= subtitle | default:'No subtitle' %>

<%-- Risky - may produce empty output --%>
<%= subtitle %>
```

### 3. Define Schemas for Complex Templates

```erb
---
<%#
@type TemplateData = {
    title: string;
    sections: Array<{ title: string; content: string }>;
};
%>
---
```

### 4. Use Functions for Repeated Logic

```erb
<% def formatDate(date) %>
return date.split("-").reverse().join("/")
<% enddef %>

Published: <%= publishDate | formatDate %>
Updated: <%= updateDate | formatDate %>
```

### 5. Handle Empty Collections

```erb
<% if items %>
\begin{itemize}
<% for item in items %>
    \item <%= item | escape %>
<% end %>
\end{itemize}
<% else %>
No items available.
<% end %>
```

### 6. Use Meaningful Variable Names

```erb
<%-- Good --%>
<% for chapter in chapters %>
    <% for section in chapter.sections %>

<%-- Less clear --%>
<% for c in chapters %>
    <% for s in c.sections %>
```

---

## Examples

### Complete LaTeX Document

```erb
---
<%#
@type TemplateData = {
    title: string;
    author: string;
    date?: string;
    abstract?: string;
    chapters: Array<{
        title: string;
        sections: Array<{
            title: string;
            content: string;
        }>;
    }>;
    bibliography?: string[];
};
%>
---
\documentclass[12pt]{report}
\usepackage[utf8]{inputenc}
\usepackage{hyperref}

\title{<%= title | escape %>}
\author{<%= author | escape %>}
\date{<%= date | default:'\today' %>}

\begin{document}

\maketitle

<% if abstract %>
\begin{abstract}
<%= abstract | markdown %>
\end{abstract}
<% end %>

\tableofcontents

<% for chapter in chapters %>
\chapter{<%= chapter.title | escape %>}

<% for section in chapter.sections %>
\section{<%= section.title | escape %>}
<%= section.content | markdown %>

<% end %>
<% end %>

<% if bibliography %>
\begin{thebibliography}{99}
<% for ref in bibliography %>
\bibitem{ref<%= loop.index %>} <%= ref | escape %>
<% end %>
\end{thebibliography}
<% end %>

\end{document}
```

### Invoice Template

```erb
<% def formatCurrency(amount) %>
return String.format("$%.2f", amount)
<% enddef %>

<% def calculateTotal(items) %>
return items.collect { it.quantity * it.price }.sum()
<% enddef %>

\documentclass{article}
\usepackage{booktabs}

\begin{document}

\textbf{INVOICE}

\vspace{1em}

\textbf{To:} <%= customer.name | escape %> \\
<%= customer.address | escape %>

\vspace{1em}

\begin{tabular}{lrrr}
\toprule
\textbf{Item} & \textbf{Qty} & \textbf{Price} & \textbf{Total} \\
\midrule
<% for item in items %>
<%= item.name | escape %> & <%= item.quantity %> & <%= item.price | formatCurrency %> & <%= (item.quantity * item.price) | formatCurrency %> \\
<% end %>
\midrule
\textbf{Total} & & & \textbf{<%= items | calculateTotal | formatCurrency %>} \\
\bottomrule
\end{tabular}

\end{document}
```

### Resume Template

```erb
---
<%#
@type TemplateData = {
    name: string;
    email: string;
    phone?: string;
    summary: string;
    experience: Array<{
        company: string;
        title: string;
        dates: string;
        highlights: string[];
    }>;
    education: Array<{
        school: string;
        degree: string;
        year: number;
    }>;
    skills: string[];
};
%>
---
\documentclass{article}
\usepackage{enumitem}

\begin{document}

\begin{center}
{\LARGE \textbf{<%= name | escape %>}}

<%= email | escape %><% if phone %> | <%= phone | escape %><% end %>
\end{center}

\section*{Summary}
<%= summary | markdown %>

\section*{Experience}
<% for job in experience %>
\textbf{<%= job.title | escape %>} at \textbf{<%= job.company | escape %>} \\
\textit{<%= job.dates | escape %>}
\begin{itemize}[noitemsep]
<% for highlight in job.highlights %>
    \item <%= highlight | escape %>
<% end %>
\end{itemize}
<% if !loop.last %>\vspace{0.5em}<% end %>
<% end %>

\section*{Education}
<% for edu in education %>
\textbf{<%= edu.degree | escape %>} -- <%= edu.school | escape %> (<%= edu.year %>)<% if !loop.last %> \\<% end %>
<% end %>

\section*{Skills}
<%= skills | join:', ' | escape %>

\end{document}
```

---

## Error Handling

### Common Errors

| Error                | Cause                      | Solution                   |
|----------------------|----------------------------|----------------------------|
| Empty output         | Missing variable           | Use `default` filter       |
| Validation exception | Missing required field     | Provide all required data  |
| Function not defined | Calling undefined function | Define function before use |
| Malformed template   | Unclosed tags              | Check `<% %>` matching     |

### Debugging Tips

1. **Check variable paths**: Use simple interpolation to verify data access
2. **Test incrementally**: Build templates piece by piece
3. **Enable strict validation**: Catch data issues early
4. **Log intermediate values**: Use custom filters for debugging

```kotlin
engine.registerFilter("debug") { value, _ ->
    println("DEBUG: $value")
    value?.toString() ?: ""
}
```

```erb
<%= data | debug %>
```

