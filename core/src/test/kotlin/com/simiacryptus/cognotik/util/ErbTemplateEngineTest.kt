package com.simiacryptus.cognotik.util

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ErbTemplateEngineTest {

  private lateinit var engine: ErbTemplateEngine

  @BeforeEach
  fun setUp() {
    engine = ErbTemplateEngine()
  }

  @Nested
  inner class GroovyFunctions {
    @Test
    fun `should define and call simple function`() {
      val template = """
<% def greet(name) %>
return "Hello, " + name + "!"
<% enddef %>
<%= greet("World") %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("Hello, World!", engine.render(template, data).trim())
    }

    @Test
    fun `should define function with multiple parameters`() {
      val template = """
<% def add(a, b) %>
return a + b
<% enddef %>
<%= add(3, 5) %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("8", engine.render(template, data).trim())
    }

    @Test
    fun `should use function as filter`() {
      val template = """
<% def doubleIt(x) %>
return x * 2
<% enddef %>
<%= value | doubleIt %>
      """.trimIndent()
      val data = JsonObject().apply {
        addProperty("value", 5)
      }
      assertEquals("10", engine.render(template, data).trim())
    }

    @Test
    fun `should define function with no parameters`() {
      val template = """
<% def getGreeting() %>
return "Hello!"
<% enddef %>
<%= getGreeting() %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("Hello!", engine.render(template, data).trim())
    }

    @Test
    fun `should use function with string manipulation`() {
      val template = """
<% def wrap(text, wrapper) %>
return wrapper + text + wrapper
<% enddef %>
<%= wrap("hello", "**") %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("**hello**", engine.render(template, data).trim())
    }

    @Test
    fun `should use function with template variables`() {
      val template = """
<% def formatName(first, last) %>
return last + ", " + first
<% enddef %>
<%= formatName(firstName, lastName) %>
      """.trimIndent()
      val data = JsonObject().apply {
        addProperty("firstName", "John")
        addProperty("lastName", "Doe")
      }
      assertEquals("Doe, John", engine.render(template, data).trim())
    }

    @Test
    fun `should define multiple functions`() {
      val template = """
<% def square(x) %>
return x * x
<% enddef %>
<% def cube(x) %>
return x * x * x
<% enddef %>
Square: <%= square(3) %>, Cube: <%= cube(2) %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("Square: 9, Cube: 8", engine.render(template, data).trim())
    }

    @Test
    fun `should use groovy list operations in function`() {
      val template = """
<% def sumList(items) %>
return items.sum()
<% enddef %>
<%= items | sumList %>
      """.trimIndent()
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add(1)
          add(2)
          add(3)
          add(4)
        })
      }
      assertEquals("10", engine.render(template, data).trim())
    }

    @Test
    fun `should use groovy string methods in function`() {
      val template = """
<% def capitalize(text) %>
return text.split(" ").collect { it.capitalize() }.join(" ")
<% enddef %>
<%= capitalize("hello world") %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("Hello World", engine.render(template, data).trim())
    }

    @Test
    fun `should chain function with other filters`() {
      val template = """
<% def prefix(text) %>
return "PREFIX_" + text
<% enddef %>
<%= name | prefix | upper %>
      """.trimIndent()
      val data = JsonObject().apply {
        addProperty("name", "test")
      }
      assertEquals("PREFIX_TEST", engine.render(template, data).trim())
    }

    @Test
    fun `should use function in for loop`() {
      val template = """
<% def format(item) %>
return "[" + item + "]"
<% enddef %>
<% for item in items %><%= item | format %><% if !loop.last %> <% end %><% end %>
      """.trimIndent()
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("a")
          add("b")
          add("c")
        })
      }
      assertEquals("[a] [b] [c]", engine.render(template, data).trim())
    }

    @Test
    fun `should use function with conditional logic`() {
      val template = """
<% def classify(score) %>
if (score >= 90) return "A"
else if (score >= 80) return "B"
else if (score >= 70) return "C"
else return "F"
<% enddef %>
Grade: <%= classify(85) %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("Grade: B", engine.render(template, data).trim())
    }

    @Test
    fun `should handle function with numeric operations`() {
      val template = """
<% def percentage(value, total) %>
return Math.round((value / total) * 100) + "%"
<% enddef %>
<%= percentage(75, 100) %>
      """.trimIndent()
      val data = JsonObject()
      assertEquals("75%", engine.render(template, data).trim())
    }
  }

  @Nested
  inner class TypeSchemaPreamble {
    @Test
    fun `should parse simple string field`() {
      val template = """
---
<%#
@type TemplateData = {
  name: string;
};
%>
---
Hello, <%= name %>!
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      assertEquals(1, schema!!.fields.size)
      assertTrue(schema.fields["name"] is ErbTemplateEngine.FieldType.StringType)
      assertFalse(schema.fields["name"]!!.optional)
    }

    @Test
    fun `should parse optional field`() {
      val template = """
---
<%#
@type TemplateData = {
  name?: string;
};
%>
---
Hello!
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      assertTrue(schema!!.fields["name"]!!.optional)
    }

    @Test
    fun `should parse multiple fields`() {
      val template = """
---
<%#
@type TemplateData = {
  name: string;
  age: number;
  active: boolean;
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      assertEquals(3, schema!!.fields.size)
      assertTrue(schema.fields["name"] is ErbTemplateEngine.FieldType.StringType)
      assertTrue(schema.fields["age"] is ErbTemplateEngine.FieldType.NumberType)
      assertTrue(schema.fields["active"] is ErbTemplateEngine.FieldType.BooleanType)
    }

    @Test
    fun `should parse array type`() {
      val template = """
---
<%#
@type TemplateData = {
  items: string[];
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      val itemsType = schema!!.fields["items"]
      assertTrue(itemsType is ErbTemplateEngine.FieldType.ArrayType)
      assertTrue((itemsType as ErbTemplateEngine.FieldType.ArrayType).elementType is ErbTemplateEngine.FieldType.StringType)
    }

    @Test
    fun `should parse Array generic syntax`() {
      val template = """
---
<%#
@type TemplateData = {
  items: Array<number>;
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      val itemsType = schema!!.fields["items"]
      assertTrue(itemsType is ErbTemplateEngine.FieldType.ArrayType)
      assertTrue((itemsType as ErbTemplateEngine.FieldType.ArrayType).elementType is ErbTemplateEngine.FieldType.NumberType)
    }

    @Test
    fun `should parse nested object type`() {
      val template = """
---
<%#
@type TemplateData = {
  user: { name: string; age: number };
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      val userType = schema!!.fields["user"]
      assertTrue(userType is ErbTemplateEngine.FieldType.ObjectType)
      val objectType = userType as ErbTemplateEngine.FieldType.ObjectType
      assertEquals(2, objectType.fields.size)
      assertTrue(objectType.fields["name"] is ErbTemplateEngine.FieldType.StringType)
      assertTrue(objectType.fields["age"] is ErbTemplateEngine.FieldType.NumberType)
    }

    @Test
    fun `should parse union type`() {
      val template = """
---
<%#
@type TemplateData = {
  status: string | number;
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      val statusType = schema!!.fields["status"]
      assertTrue(statusType is ErbTemplateEngine.FieldType.UnionType)
      assertEquals(listOf("string", "number"), (statusType as ErbTemplateEngine.FieldType.UnionType).types)
    }

    @Test
    fun `should parse any type`() {
      val template = """
---
<%#
@type TemplateData = {
  data: any;
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      assertTrue(schema!!.fields["data"] is ErbTemplateEngine.FieldType.AnyType)
    }

    @Test
    fun `should render template with preamble`() {
      val template = """
---
<%#
@type TemplateData = {
  name: string;
};
%>
---
Hello, <%= name %>!
      """.trimIndent()
      val data = JsonObject().apply {
        addProperty("name", "World")
      }
      assertEquals("Hello, World!", engine.render(template, data))
    }

    @Test
    fun `should return null schema for template without preamble`() {
      val template = "Hello, <%= name %>!"
      val schema = engine.extractSchema(template)
      assertNull(schema)
    }

    @Test
    fun `should ignore comments in preamble`() {
      val template = """
---
<%#
@type TemplateData = {
  // This is a comment
  name: string;
  /* Another comment */
  age: number;
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      assertEquals(2, schema!!.fields.size)
    }

    @Test
    fun `should generate TypeScript interface from schema`() {
      val template = """
---
<%#
@type TemplateData = {
  name: string;
  age?: number;
};
%>
---
Template body
      """.trimIndent()
      val schema = engine.extractSchema(template)
      val typescript = schema!!.toTypeScript()
      assertTrue(typescript.contains("interface TemplateData"))
      assertTrue(typescript.contains("name: string"))
      assertTrue(typescript.contains("age?: number"))
    }
  }

  @Nested
  inner class DataValidation {
    @Test
    fun `should validate required string field`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  name: string;
};
%>
---
Hello, <%= name %>!
      """.trimIndent()
      val validData = JsonObject().apply {
        addProperty("name", "World")
      }


      // Should not throw
      assertEquals("Hello, World!", engine.render(template, validData))
    }

    @Test
    fun `should fail validation for missing required field`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  name: string;
};
%>
---
Hello!
      """.trimIndent()

      val invalidData = JsonObject()

      assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        engine.render(template, invalidData)
      }
    }

    @Test
    fun `should pass validation for missing optional field`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  name?: string;
};
%>
---
Hello!
      """.trimIndent()

      val data = JsonObject()

      // Should not throw
      assertEquals("Hello!", engine.render(template, data))
    }

    @Test
    fun `should fail validation for wrong type`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  count: number;
};
%>
---
Count: <%= count %>
      """.trimIndent()

      val invalidData = JsonObject().apply {
        addProperty("count", "not a number")
      }

      assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        engine.render(template, invalidData)
      }
    }

    @Test
    fun `should validate array elements`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  items: number[];
};
%>
---
Items: <%= items | join %>
      """.trimIndent()

      val validData = JsonObject().apply {
        add("items", JsonArray().apply {
          add(1)
          add(2)
          add(3)
        })
      }

      // Should not throw
      assertEquals("Items: 1, 2, 3", engine.render(template, validData))
    }

    @Test
    fun `should fail validation for invalid array element`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  items: number[];
};
%>
---
Items
      """.trimIndent()

      val invalidData = JsonObject().apply {
        add("items", JsonArray().apply {
          add(1)
          add("not a number")
          add(3)
        })
      }

      assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        engine.render(template, invalidData)
      }
    }

    @Test
    fun `should validate nested object fields`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  user: { name: string; age: number };
};
%>
---
User: <%= user.name %>
      """.trimIndent()

      val validData = JsonObject().apply {
        add("user", JsonObject().apply {
          addProperty("name", "John")
          addProperty("age", 30)
        })
      }

      // Should not throw
      assertEquals("User: John", engine.render(template, validData))
    }

    @Test
    fun `should fail validation for missing nested field`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  user: { name: string; age: number };
};
%>
---
User
      """.trimIndent()

      val invalidData = JsonObject().apply {
        add("user", JsonObject().apply {
          addProperty("name", "John")
          // missing age
        })
      }

      assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        engine.render(template, invalidData)
      }
    }

    @Test
    fun `should not throw when strict validation is disabled`() {
      engine.strictValidation = false
      val template = """
---
<%#
@type TemplateData = {
  name: string;
};
%>
---
Hello!
      """.trimIndent()

      val invalidData = JsonObject()

      // Should not throw even with missing required field
      assertEquals("Hello!", engine.render(template, invalidData))
    }

    @Test
    fun `validation error should include field path`() {
      engine.strictValidation = true
      val template = """
---
<%#
@type TemplateData = {
  user: { profile: { name: string } };
};
%>
---
Hello
      """.trimIndent()

      val invalidData = JsonObject().apply {
        add("user", JsonObject().apply {
          add("profile", JsonObject())
        })
      }

      val exception = assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        engine.render(template, invalidData)
      }

      assertTrue(exception.errors.any { it.path.contains("user.profile.name") })
    }
  }

  @Nested
  inner class BasicInterpolation {

    @Test
    fun `should render simple variable`() {
      val template = "Hello, <%= name %>!"
      val data = JsonObject().apply {
        addProperty("name", "World")
      }
      assertEquals("Hello, World!", engine.render(template, data))
    }

    @Test
    fun `should render multiple variables`() {
      val template = "<%= greeting %>, <%= name %>!"
      val data = JsonObject().apply {
        addProperty("greeting", "Hello")
        addProperty("name", "World")
      }
      assertEquals("Hello, World!", engine.render(template, data))
    }

    @Test
    fun `should handle nested object access`() {
      val template = "Name: <%= user.name %>, Age: <%= user.age %>"
      val data = JsonObject().apply {
        add("user", JsonObject().apply {
          addProperty("name", "John")
          addProperty("age", 30)
        })
      }
      assertEquals("Name: John, Age: 30", engine.render(template, data))
    }

    @Test
    fun `should handle missing variables gracefully`() {
      val template = "Value: <%= missing %>"
      val data = JsonObject()
      assertEquals("Value: ", engine.render(template, data))
    }

    @Test
    fun `should preserve literal text`() {
      val template = "This is literal text without any variables."
      val data = JsonObject()
      assertEquals("This is literal text without any variables.", engine.render(template, data))
    }
  }

  @Nested
  inner class Filters {

    @Test
    fun `should apply escape filter`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "Hello & World")
      }
      assertEquals("Hello \\& World", engine.render(template, data))
    }

    @Test
    fun `should apply upper filter`() {
      val template = "<%= name | upper %>"
      val data = JsonObject().apply {
        addProperty("name", "hello")
      }
      assertEquals("HELLO", engine.render(template, data))
    }

    @Test
    fun `should apply lower filter`() {
      val template = "<%= name | lower %>"
      val data = JsonObject().apply {
        addProperty("name", "HELLO")
      }
      assertEquals("hello", engine.render(template, data))
    }

    @Test
    fun `should apply join filter with default separator`() {
      val template = "<%= items | join %>"
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("a")
          add("b")
          add("c")
        })
      }
      assertEquals("a, b, c", engine.render(template, data))
    }

    @Test
    fun `should apply join filter with custom separator`() {
      val template = "<%= items | join:' - ' %>"
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("a")
          add("b")
          add("c")
        })
      }
      assertEquals("a - b - c", engine.render(template, data))
    }

    @Test
    fun `should apply default filter when value is empty`() {
      val template = "<%= name | default:'Unknown' %>"
      val data = JsonObject().apply {
        addProperty("name", "")
      }
      assertEquals("Unknown", engine.render(template, data))
    }

    @Test
    fun `should apply default filter when value is missing`() {
      val template = "<%= name | default:'Unknown' %>"
      val data = JsonObject()
      assertEquals("Unknown", engine.render(template, data))
    }

    @Test
    fun `should apply default filter with spaces in value`() {
      val template = """<%= color | default:"20, 20, 35" %>"""
      val data = JsonObject()
      assertEquals("20, 20, 35", engine.render(template, data))
    }

    @Test
    fun `should apply default filter with spaces when value is missing in context`() {
      val template = """\definecolor{headercolor}{RGB}{ <%= metadata.theme.secondary | default:"20, 20, 35" %> }"""
      val data = JsonObject().apply {
        add("metadata", JsonObject().apply {
          add("theme", JsonObject())
        })
      }
      assertEquals("""\definecolor{headercolor}{RGB}{ 20, 20, 35 }""", engine.render(template, data))
    }

    @Test
    fun `should apply default filter with commas in value`() {
      val template = """<%= items | default:"a, b, c" %>"""
      val data = JsonObject()
      assertEquals("a, b, c", engine.render(template, data))
    }


    @Test
    fun `should chain multiple filters`() {
      val template = "<%= name | upper | escape %>"
      val data = JsonObject().apply {
        addProperty("name", "hello & world")
      }
      assertEquals("HELLO \\& WORLD", engine.render(template, data))
    }

    @Test
    fun `should register and use custom filter`() {
      engine.registerFilter("reverse") { v, _ ->
        v?.toString()?.reversed() ?: ""
      }
      val template = "<%= text | reverse %>"
      val data = JsonObject().apply {
        addProperty("text", "hello")
      }
      assertEquals("olleh", engine.render(template, data))
    }
  }

  @Nested
  inner class ForLoops {

    @Test
    fun `should iterate over array`() {
      val template = "<% for item in items %><%= item %> <% end %>"
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("a")
          add("b")
          add("c")
        })
      }
      assertEquals("a b c ", engine.render(template, data))
    }

    @Test
    fun `should provide loop index`() {
      val template = "<% for item in items %><%= loop.index %>:<%= item %> <% end %>"
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("a")
          add("b")
          add("c")
        })
      }
      assertEquals("0:a 1:b 2:c ", engine.render(template, data))
    }

    @Test
    fun `should provide loop first and last flags`() {
      val template = "<% for item in items %><%= item %><% if !loop.last %>, <% end %><% end %>"
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("a")
          add("b")
          add("c")
        })
      }
      assertEquals("a, b, c", engine.render(template, data))
    }

    @Test
    fun `should iterate over object entries`() {
      val template = "<% for entry in obj %><%= entry.key %>=<%= entry.value %> <% end %>"
      val data = JsonObject().apply {
        add("obj", JsonObject().apply {
          addProperty("a", "1")
          addProperty("b", "2")
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("a=1"))
      assertTrue(result.contains("b=2"))
    }

    @Test
    fun `should handle nested for loops`() {
      val template = "<% for row in matrix %><% for cell in row %><%= cell %> <% end %>\n<% end %>"
      val data = JsonObject().apply {
        add("matrix", JsonArray().apply {
          add(JsonArray().apply { add("1"); add("2") })
          add(JsonArray().apply { add("3"); add("4") })
        })
      }
      assertEquals("1 2 \n3 4 \n", engine.render(template, data))
    }

    @Test
    fun `should handle empty array`() {
      val template = "<% for item in items %><%= item %><% end %>"
      val data = JsonObject().apply {
        add("items", JsonArray())
      }
      assertEquals("", engine.render(template, data))
    }

    @Test
    fun `should support endfor keyword`() {
      val template = "<% for item in items %><%= item %> <% endfor %>"
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("a")
          add("b")
        })
      }
      assertEquals("a b ", engine.render(template, data))
    }
  }

  @Nested
  inner class IfStatements {

    @Test
    fun `should render if block when condition is true`() {
      val template = "<% if show %>Visible<% end %>"
      val data = JsonObject().apply {
        addProperty("show", true)
      }
      assertEquals("Visible", engine.render(template, data))
    }

    @Test
    fun `should not render if block when condition is false`() {
      val template = "<% if show %>Visible<% end %>"
      val data = JsonObject().apply {
        addProperty("show", false)
      }
      assertEquals("", engine.render(template, data))
    }

    @Test
    fun `should render else block when condition is false`() {
      val template = "<% if show %>Yes<% else %>No<% end %>"
      val data = JsonObject().apply {
        addProperty("show", false)
      }
      assertEquals("No", engine.render(template, data))
    }

    @Test
    fun `should handle negation with exclamation mark`() {
      val template = "<% if !hidden %>Visible<% end %>"
      val data = JsonObject().apply {
        addProperty("hidden", false)
      }
      assertEquals("Visible", engine.render(template, data))
    }

    @Test
    fun `should handle equality comparison`() {
      val template = "<% if status == \"active\" %>Active<% else %>Inactive<% end %>"
      val data = JsonObject().apply {
        addProperty("status", "active")
      }
      assertEquals("Active", engine.render(template, data))
    }

    @Test
    fun `should handle inequality comparison`() {
      val template = "<% if status != \"active\" %>Not Active<% end %>"
      val data = JsonObject().apply {
        addProperty("status", "inactive")
      }
      assertEquals("Not Active", engine.render(template, data))
    }

    @Test
    fun `should treat non-empty string as truthy`() {
      val template = "<% if name %>Has name<% end %>"
      val data = JsonObject().apply {
        addProperty("name", "John")
      }
      assertEquals("Has name", engine.render(template, data))
    }

    @Test
    fun `should treat empty string as falsy`() {
      val template = "<% if name %>Has name<% else %>No name<% end %>"
      val data = JsonObject().apply {
        addProperty("name", "")
      }
      assertEquals("No name", engine.render(template, data))
    }

    @Test
    fun `should treat non-empty array as truthy`() {
      val template = "<% if items %>Has items<% end %>"
      val data = JsonObject().apply {
        add("items", JsonArray().apply { add("a") })
      }
      assertEquals("Has items", engine.render(template, data))
    }

    @Test
    fun `should treat empty array as falsy`() {
      val template = "<% if items %>Has items<% else %>No items<% end %>"
      val data = JsonObject().apply {
        add("items", JsonArray())
      }
      assertEquals("No items", engine.render(template, data))
    }

    @Test
    fun `should handle nested if statements`() {
      val template = "<% if a %><% if b %>Both<% else %>Only A<% end %><% end %>"
      val data = JsonObject().apply {
        addProperty("a", true)
        addProperty("b", true)
      }
      assertEquals("Both", engine.render(template, data))
    }

    @Test
    fun `should support endif keyword`() {
      val template = "<% if show %>Visible<% endif %>"
      val data = JsonObject().apply {
        addProperty("show", true)
      }
      assertEquals("Visible", engine.render(template, data))
    }
  }

  @Nested
  inner class LatexEscaping {

    @Test
    fun `should escape backslash`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "a\\b")
      }
      assertEquals("a\\textbackslash{}b", engine.render(template, data))
    }

    @Test
    fun `should escape curly braces`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "{hello}")
      }
      assertEquals("\\{hello\\}", engine.render(template, data))
    }

    @Test
    fun `should escape dollar sign`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "$100")
      }
      assertEquals("\\$100", engine.render(template, data))
    }

    @Test
    fun `should escape ampersand`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "A & B")
      }
      assertEquals("A \\& B", engine.render(template, data))
    }

    @Test
    fun `should escape percent`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "50%")
      }
      assertEquals("50\\%", engine.render(template, data))
    }

    @Test
    fun `should escape hash`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "#1")
      }
      assertEquals("\\#1", engine.render(template, data))
    }

    @Test
    fun `should escape underscore`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "hello_world")
      }
      assertEquals("hello\\_world", engine.render(template, data))
    }

    @Test
    fun `should escape tilde`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "~user")
      }
      assertEquals("\\textasciitilde{}user", engine.render(template, data))
    }

    @Test
    fun `should escape caret`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "x^2")
      }
      assertEquals("x\\textasciicircum{}2", engine.render(template, data))
    }

    @Test
    fun `should escape angle brackets`() {
      val template = "<%= text | escape %>"
      val data = JsonObject().apply {
        addProperty("text", "<html>")
      }
      assertEquals("\\textless{}html\\textgreater{}", engine.render(template, data))
    }
  }

  @Nested
  inner class MarkdownToLatex {

    @Test
    fun `should convert bold markdown to latex`() {
      val template = "<%= text | markdown %>"
      val data = JsonObject().apply {
        addProperty("text", "This is **bold** text")
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\textbf{bold}"))
    }

    @Test
    fun `should convert italic markdown to latex`() {
      val template = "<%= text | markdown %>"
      val data = JsonObject().apply {
        addProperty("text", "This is *italic* text")
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\textit{italic}"))
    }

    @Test
    fun `should convert inline code to latex`() {
      val template = "<%= text | markdown %>"
      val data = JsonObject().apply {
        addProperty("text", "Use `code` here")
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\texttt{code}"))
    }

    @Test
    fun `should convert links to latex href`() {
      val template = "<%= text | markdown %>"
      val data = JsonObject().apply {
        addProperty("text", "Visit [Google](https://google.com)")
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\href{https://google.com}{Google}"))
    }
  }

  @Nested
  inner class ComplexTemplates {

    @Test
    fun `should render document with sections`() {
      val template = """
                \documentclass{article}
                \begin{document}
                \title{<%= title %>}
                <% for section in sections %>
                \section{<%= section.title %>}
                <%= section.content %>
                <% end %>
                \end{document}
            """.trimIndent()

      val data = JsonObject().apply {
        addProperty("title", "My Document")
        add("sections", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("title", "Introduction")
            addProperty("content", "This is the intro.")
          })
          add(JsonObject().apply {
            addProperty("title", "Conclusion")
            addProperty("content", "This is the conclusion.")
          })
        })
      }

      val result = engine.render(template, data)
      assertTrue(result.contains("\\title{My Document}"))
      assertTrue(result.contains("\\section{Introduction}"))
      assertTrue(result.contains("\\section{Conclusion}"))
    }

    @Test
    fun `should handle conditional sections`() {
      val template = """
                <% if showAbstract %>
                \begin{abstract}
                <%= abstract %>
                \end{abstract}
                <% end %>
            """.trimIndent()

      val dataWithAbstract = JsonObject().apply {
        addProperty("showAbstract", true)
        addProperty("abstract", "This is the abstract.")
      }

      val dataWithoutAbstract = JsonObject().apply {
        addProperty("showAbstract", false)
      }

      val resultWith = engine.render(template, dataWithAbstract)
      val resultWithout = engine.render(template, dataWithoutAbstract)

      assertTrue(resultWith.contains("\\begin{abstract}"))
      assertFalse(resultWithout.contains("\\begin{abstract}"))
    }

    @Test
    fun `should render table with data`() {
      val template = """
                \begin{tabular}{|c|c|}
                \hline
                Name & Value \\
                \hline
                <% for row in data %>
                <%= row.name %> & <%= row.value %> \\
                <% end %>
                \hline
                \end{tabular}
            """.trimIndent()

      val data = JsonObject().apply {
        add("data", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("name", "A")
            addProperty("value", "1")
          })
          add(JsonObject().apply {
            addProperty("name", "B")
            addProperty("value", "2")
          })
        })
      }

      val result = engine.render(template, data)
      assertTrue(result.contains("A & 1"))
      assertTrue(result.contains("B & 2"))
    }
  }

  @Nested
  inner class EdgeCases {

    @Test
    fun `should handle null values`() {
      val template = "<%= value %>"
      val data = JsonObject().apply {
        add("value", JsonNull.INSTANCE)
      }
      assertEquals("", engine.render(template, data))
    }

    @Test
    fun `should handle deeply nested paths`() {
      val template = "<%= a.b.c.d %>"
      val data = JsonObject().apply {
        add("a", JsonObject().apply {
          add("b", JsonObject().apply {
            add("c", JsonObject().apply {
              addProperty("d", "deep")
            })
          })
        })
      }
      assertEquals("deep", engine.render(template, data))
    }

    @Test
    fun `should handle whitespace in tags`() {
      val template = "<%=    name    %>"
      val data = JsonObject().apply {
        addProperty("name", "test")
      }
      assertEquals("test", engine.render(template, data))
    }

    @Test
    fun `should handle empty template`() {
      val template = ""
      val data = JsonObject()
      assertEquals("", engine.render(template, data))
    }

    @Test
    fun `should handle template with only literal text`() {
      val template = "Just some text"
      val data = JsonObject()
      assertEquals("Just some text", engine.render(template, data))
    }

    @Test
    fun `should handle single quotes in equality comparison`() {
      val template = "<% if status == 'active' %>Active<% end %>"
      val data = JsonObject().apply {
        addProperty("status", "active")
      }
      assertEquals("Active", engine.render(template, data))
    }
  }

  @Nested
  inner class EndBlockIssues {

    @Test
    fun `should handle end block with latex begin-end environment`() {
      // This tests if \end{...} in LaTeX is confused with <% end %>
      val template = """<% if show %>\begin{itemize}
\item Test
\end{itemize}<% end %>"""
      val data = JsonObject().apply {
        addProperty("show", true)
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\begin{itemize}"))
      assertTrue(result.contains("\\end{itemize}"))
    }

    @Test
    fun `should handle multiple latex end environments in for loop`() {
      val template = """<% for item in items %>\begin{tabular}{c}
<%= item %>
\end{tabular}
<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
      }
      val result = engine.render(template, data)
      assertEquals(2, result.split("\\end{tabular}").size - 1)
    }

    @Test
    fun `should handle nested latex environments with conditionals`() {
      val template = """<% if outer %>\begin{document}
<% if inner %>\begin{section}
Content
\end{section}<% end %>
\end{document}<% end %>"""
      val data = JsonObject().apply {
        addProperty("outer", true)
        addProperty("inner", true)
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\begin{document}"))
      assertTrue(result.contains("\\end{document}"))
      assertTrue(result.contains("\\begin{section}"))
      assertTrue(result.contains("\\end{section}"))
    }

    @Test
    fun `should handle infocard end in for loop`() {
      // Simulating the resume template pattern
      val template = """<% for job in experience %>
\begin{infocard}
Position: <%= job.position %>
\end{infocard}
<% end %>"""
      val data = JsonObject().apply {
        add("experience", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("position", "Engineer")
          })
          add(JsonObject().apply {
            addProperty("position", "Manager")
          })
        })
      }
      val result = engine.render(template, data)
      assertEquals(2, result.split("\\begin{infocard}").size - 1)
      assertEquals(2, result.split("\\end{infocard}").size - 1)
    }

    @Test
    fun `should handle for loop with nested if and latex end`() {
      val template = """<% for job in jobs %>
\begin{infocard}
<% if job.highlights %>
\begin{itemize}
<% for h in job.highlights %>
\item <%= h %>
<% end %>
\end{itemize}
<% end %>
\end{infocard}
<% end %>"""
      val data = JsonObject().apply {
        add("jobs", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("title", "Job1")
            add("highlights", JsonArray().apply {
              add("H1")
              add("H2")
            })
          })
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\begin{infocard}"))
      assertTrue(result.contains("\\end{infocard}"))
      assertTrue(result.contains("\\begin{itemize}"))
      assertTrue(result.contains("\\end{itemize}"))
      assertTrue(result.contains("\\item H1"))
      assertTrue(result.contains("\\item H2"))
    }

    @Test
    fun `should handle consecutive for loops with latex environments`() {
      val template = """<% for item in list1 %>
\begin{box}
<%= item %>
\end{box}
<% end %>
<% for item in list2 %>
\begin{card}
<%= item %>
\end{card}
<% end %>"""
      val data = JsonObject().apply {
        add("list1", JsonArray().apply {
          add("A")
          add("B")
        })
        add("list2", JsonArray().apply {
          add("X")
          add("Y")
        })
      }
      val result = engine.render(template, data)
      assertEquals(2, result.split("\\begin{box}").size - 1)
      assertEquals(2, result.split("\\end{box}").size - 1)
      assertEquals(2, result.split("\\begin{card}").size - 1)
      assertEquals(2, result.split("\\end{card}").size - 1)
    }

    @Test
    fun `should handle deeply nested for loops with latex`() {
      val template = """<% for section in sections %>
\begin{section}
<% for item in section.items %>
\begin{item}
<%= item.name %>
\end{item}
<% end %>
\end{section}
<% end %>"""
      val data = JsonObject().apply {
        add("sections", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("title", "S1")
            add("items", JsonArray().apply {
              add(JsonObject().apply { addProperty("name", "I1") })
              add(JsonObject().apply { addProperty("name", "I2") })
            })
          })
          add(JsonObject().apply {
            addProperty("title", "S2")
            add("items", JsonArray().apply {
              add(JsonObject().apply { addProperty("name", "I3") })
            })
          })
        })
      }
      val result = engine.render(template, data)
      assertEquals(2, result.split("\\begin{section}").size - 1)
      assertEquals(2, result.split("\\end{section}").size - 1)
      assertEquals(3, result.split("\\begin{item}").size - 1)
      assertEquals(3, result.split("\\end{item}").size - 1)
    }

    @Test
    fun `should handle if-else with latex end environments`() {
      val template = """<% if hasItems %>
\begin{itemize}
\item Has items
\end{itemize}
<% else %>
\begin{center}
No items
\end{center}
<% end %>"""
      val dataWithItems = JsonObject().apply {
        addProperty("hasItems", true)
      }
      val dataWithoutItems = JsonObject().apply {
        addProperty("hasItems", false)
      }

      val resultWith = engine.render(template, dataWithItems)
      assertTrue(resultWith.contains("\\begin{itemize}"))
      assertTrue(resultWith.contains("\\end{itemize}"))
      assertFalse(resultWith.contains("\\begin{center}"))

      val resultWithout = engine.render(template, dataWithoutItems)
      assertTrue(resultWithout.contains("\\begin{center}"))
      assertTrue(resultWithout.contains("\\end{center}"))
      assertFalse(resultWithout.contains("\\begin{itemize}"))
    }

    @Test
    fun `should handle tabularx end in for loop`() {
      val template = """<% for job in experience %>
\begin{tabularx}{\linewidth}{@{}X r@{}}
\textbf{<%= job.position %>} & <%= job.date %> \\
\end{tabularx}
<% end %>"""
      val data = JsonObject().apply {
        add("experience", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("position", "Engineer")
            addProperty("date", "2020")
          })
          add(JsonObject().apply {
            addProperty("position", "Manager")
            addProperty("date", "2021")
          })
        })
      }
      val result = engine.render(template, data)
      assertEquals(2, result.split("\\begin{tabularx}").size - 1)
      assertEquals(2, result.split("\\end{tabularx}").size - 1)
    }

    @Test
    fun `should handle multicolumn end in conditional`() {
      val template = """<% if showSkills %>
\begin{multicols}{2}
<% for skill in skills %>
\item <%= skill %>
<% end %>
\end{multicols}
<% end %>"""
      val data = JsonObject().apply {
        addProperty("showSkills", true)
        add("skills", JsonArray().apply {
          add("Java")
          add("Kotlin")
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\begin{multicols}"))
      assertTrue(result.contains("\\end{multicols}"))
    }

    @Test
    fun `should handle end with special characters nearby`() {
      val template = """<% for item in items %>\end{box} & \end{card}<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
      }
      val result = engine.render(template, data)
      assertEquals(2, result.split("\\end{box}").size - 1)
      assertEquals(2, result.split("\\end{card}").size - 1)
    }

    @Test
    fun `should handle end on same line as erb end tag`() {
      val template = """<% for item in items %>\begin{x}<%= item %>\end{x}<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
      }
      val result = engine.render(template, data)
      assertEquals("\\begin{x}A\\end{x}\\begin{x}B\\end{x}", result)
    }

    @Test
    fun `should handle complex resume-like template structure`() {
      val template = """<% if experience %>
\section{Experience}
<% for job in experience %>
\begin{infocard}
\begin{tabularx}{\linewidth}{@{}X r@{}}
\textbf{<%= job.position %>} & <%= job.startDate %> - <%= job.endDate | default:'Present' %> \\
\end{tabularx}
<% if job.highlights %>
\begin{itemize}
<% for highlight in job.highlights %>
\item <%= highlight %>
<% end %>
\end{itemize}
<% end %>
\end{infocard}
<% end %>
<% end %>"""
      val data = JsonObject().apply {
        add("experience", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("position", "Senior Engineer")
            addProperty("startDate", "2020")
            addProperty("endDate", "2023")
            add("highlights", JsonArray().apply {
              add("Built systems")
              add("Led team")
            })
          })
          add(JsonObject().apply {
            addProperty("position", "Junior Engineer")
            addProperty("startDate", "2018")
            addProperty("endDate", "2020")
            add("highlights", JsonArray().apply {
              add("Learned stuff")
            })
          })
        })
      }
      val result = engine.render(template, data)

      // Verify structure
      assertTrue(result.contains("\\section{Experience}"))
      assertEquals(2, result.split("\\begin{infocard}").size - 1)
      assertEquals(2, result.split("\\end{infocard}").size - 1)
      assertEquals(2, result.split("\\begin{tabularx}").size - 1)
      assertEquals(2, result.split("\\end{tabularx}").size - 1)
      assertEquals(2, result.split("\\begin{itemize}").size - 1)
      assertEquals(2, result.split("\\end{itemize}").size - 1)

      // Verify content
      assertTrue(result.contains("Senior Engineer"))
      assertTrue(result.contains("Junior Engineer"))
      assertTrue(result.contains("Built systems"))
      assertTrue(result.contains("Led team"))
      assertTrue(result.contains("Learned stuff"))
    }

    @Test
    fun `should handle empty for loop body with latex end`() {
      val template = """<% for item in items %>\end{x}<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
          add("C")
        })
      }
      val result = engine.render(template, data)
      assertEquals("\\end{x}\\end{x}\\end{x}", result)
    }

    @Test
    fun `should handle for loop immediately followed by if`() {
      val template = """<% for item in items %><%= item %><% end %><% if show %>SHOWN<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
        addProperty("show", true)
      }
      val result = engine.render(template, data)
      assertEquals("ABSHOWN", result)
    }

    @Test
    fun `should handle if immediately followed by for loop`() {
      val template = """<% if show %>SHOWN<% end %><% for item in items %><%= item %><% end %>"""
      val data = JsonObject().apply {
        addProperty("show", true)
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
      }
      val result = engine.render(template, data)
      assertEquals("SHOWNAB", result)
    }

    @Test
    fun `should handle newlines around end blocks`() {
      val template = """<% for item in items %>
<%= item %>
<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("A"))
      assertTrue(result.contains("B"))
    }

    @Test
    fun `should handle end with varying whitespace`() {
      val template1 = "<% for item in items %><%= item %><%end%>"
      val template2 = "<% for item in items %><%= item %><% end %>"
      val template3 = "<% for item in items %><%= item %><%  end  %>"

      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
      }

      assertEquals("AB", engine.render(template1, data))
      assertEquals("AB", engine.render(template2, data))
      assertEquals("AB", engine.render(template3, data))
    }

    @Test
    fun `should handle latex document end vs erb end`() {
      val template = """<% if show %>\documentclass{article}
\begin{document}
Content
\end{document}<% end %>"""
      val data = JsonObject().apply {
        addProperty("show", true)
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\documentclass{article}"))
      assertTrue(result.contains("\\begin{document}"))
      assertTrue(result.contains("\\end{document}"))
    }

    @Test
    fun `should handle multiple end keywords on same line`() {
      val template = """<% for a in items %><% for b in items %><%= a %><%= b %><% end %><% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("X")
          add("Y")
        })
      }
      val result = engine.render(template, data)
      assertEquals("XXXYYXYY", result)
    }
  }

  @Nested
  inner class LoopsWithIfElse {
    @Test
    fun `should handle if-else inside for loop`() {
      val template =
        """<% for item in items %><% if item.active %>Active: <%= item.name %><% else %>Inactive: <%= item.name %><% end %>
<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("name", "A")
            addProperty("active", true)
          })
          add(JsonObject().apply {
            addProperty("name", "B")
            addProperty("active", false)
          })
          add(JsonObject().apply {
            addProperty("name", "C")
            addProperty("active", true)
          })
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("Active: A"))
      assertTrue(result.contains("Inactive: B"))
      assertTrue(result.contains("Active: C"))
    }

    @Test
    fun `should handle for loop inside if block`() {
      val template = """<% if showItems %><% for item in items %><%= item %> <% end %><% end %>"""
      val data = JsonObject().apply {
        addProperty("showItems", true)
        add("items", JsonArray().apply {
          add("A")
          add("B")
          add("C")
        })
      }
      assertEquals("A B C ", engine.render(template, data))
    }

    @Test
    fun `should handle for loop inside else block`() {
      val template = """<% if showPrimary %>Primary<% else %><% for item in items %><%= item %> <% end %><% end %>"""
      val data = JsonObject().apply {
        addProperty("showPrimary", false)
        add("items", JsonArray().apply {
          add("X")
          add("Y")
        })
      }
      assertEquals("X Y ", engine.render(template, data))
    }

    @Test
    fun `should handle nested if-else inside nested for loops`() {
      val template =
        """<% for section in sections %>[<%= section.name %>: <% for item in section.items %><% if item.important %>*<%= item.value %>*<% else %><%= item.value %><% end %><% if !loop.last %>, <% end %><% end %>]
<% end %>"""
      val data = JsonObject().apply {
        add("sections", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("name", "S1")
            add("items", JsonArray().apply {
              add(JsonObject().apply {
                addProperty("value", "a")
                addProperty("important", true)
              })
              add(JsonObject().apply {
                addProperty("value", "b")
                addProperty("important", false)
              })
            })
          })
          add(JsonObject().apply {
            addProperty("name", "S2")
            add("items", JsonArray().apply {
              add(JsonObject().apply {
                addProperty("value", "c")
                addProperty("important", false)
              })
            })
          })
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("[S1: *a*, b]"))
      assertTrue(result.contains("[S2: c]"))
    }

    @Test
    fun `should handle multiple if-else blocks inside for loop`() {
      val template =
        """<% for item in items %><% if item.type == "A" %>TypeA<% else %>Other<% end %>-<% if item.active %>On<% else %>Off<% end %>
<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("type", "A")
            addProperty("active", true)
          })
          add(JsonObject().apply {
            addProperty("type", "B")
            addProperty("active", false)
          })
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("TypeA-On"))
      assertTrue(result.contains("Other-Off"))
    }

    @Test
    fun `should handle for loop with if checking loop variables`() {
      val template =
        """<% for item in items %><% if loop.first %>[<% end %><%= item %><% if loop.last %>]<% else %>, <% end %><% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
          add("C")
        })
      }
      assertEquals("[A, B, C]", engine.render(template, data))
    }

    @Test
    fun `should handle if-else with different for loops in each branch`() {
      val template =
        """<% if useNumbers %><% for n in numbers %><%= n %> <% end %><% else %><% for l in letters %><%= l %> <% end %><% end %>"""
      val dataNumbers = JsonObject().apply {
        addProperty("useNumbers", true)
        add("numbers", JsonArray().apply {
          add(1)
          add(2)
          add(3)
        })
        add("letters", JsonArray().apply {
          add("a")
          add("b")
        })
      }
      val dataLetters = JsonObject().apply {
        addProperty("useNumbers", false)
        add("numbers", JsonArray().apply {
          add(1)
          add(2)
          add(3)
        })
        add("letters", JsonArray().apply {
          add("a")
          add("b")
        })
      }
      assertEquals("1 2 3 ", engine.render(template, dataNumbers))
      assertEquals("a b ", engine.render(template, dataLetters))
    }

    @Test
    fun `should handle empty array with if-else fallback`() {
      val template = """<% if items %><% for item in items %><%= item %><% end %><% else %>No items<% end %>"""
      val dataWithItems = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
        })
      }
      val dataEmpty = JsonObject().apply {
        add("items", JsonArray())
      }
      assertEquals("A", engine.render(template, dataWithItems))
      assertEquals("No items", engine.render(template, dataEmpty))
    }

    @Test
    fun `should handle for loop with conditional content and latex`() {
      val template = """<% for item in items %>
\begin{box}
<% if item.highlight %>\textbf{<%= item.text %>}<% else %><%= item.text %><% end %>
\end{box}
<% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("text", "Important")
            addProperty("highlight", true)
          })
          add(JsonObject().apply {
            addProperty("text", "Normal")
            addProperty("highlight", false)
          })
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("\\textbf{Important}"))
      assertTrue(result.contains("Normal"))
      assertFalse(result.contains("\\textbf{Normal}"))
      assertEquals(2, result.split("\\begin{box}").size - 1)
      assertEquals(2, result.split("\\end{box}").size - 1)
    }

    @Test
    fun `should handle deeply nested if inside for inside if`() {
      val template =
        """<% if showSection %><% for item in items %><% if item.visible %><%= item.name %><% end %><% end %><% end %>"""
      val data = JsonObject().apply {
        addProperty("showSection", true)
        add("items", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("name", "A")
            addProperty("visible", true)
          })
          add(JsonObject().apply {
            addProperty("name", "B")
            addProperty("visible", false)
          })
          add(JsonObject().apply {
            addProperty("name", "C")
            addProperty("visible", true)
          })
        })
      }
      assertEquals("AC", engine.render(template, data))
    }

    @Test
    fun `should handle for loop with alternating if-else based on index`() {
      val template =
        """<% for item in items %><% if loop.index % 2 == 0 %>Even:<%= item %><% else %>Odd:<%= item %><% end %> <% end %>"""
      val data = JsonObject().apply {
        add("items", JsonArray().apply {
          add("A")
          add("B")
          add("C")
          add("D")
        })
      }
      val result = engine.render(template, data)
      assertTrue(result.contains("Even:A"))
      assertTrue(result.contains("Odd:B"))
      assertTrue(result.contains("Even:C"))
      assertTrue(result.contains("Odd:D"))
    }

    @Test
    fun `should handle consecutive for loops each with if-else`() {
      val template =
        """<% for a in listA %><% if a.show %><%= a.val %><% else %>-<% end %><% end %>|<% for b in listB %><% if b.show %><%= b.val %><% else %>-<% end %><% end %>"""
      val data = JsonObject().apply {
        add("listA", JsonArray().apply {
          add(JsonObject().apply { addProperty("val", "1"); addProperty("show", true) })
          add(JsonObject().apply { addProperty("val", "2"); addProperty("show", false) })
        })
        add("listB", JsonArray().apply {
          add(JsonObject().apply { addProperty("val", "X"); addProperty("show", false) })
          add(JsonObject().apply { addProperty("val", "Y"); addProperty("show", true) })
        })
      }
      assertEquals("1-|-Y", engine.render(template, data))
    }

    @Test
    fun `should handle if-else wrapping entire for loop with latex environments`() {
      val template = """<% if useList %>
\begin{itemize}
<% for item in items %>
\item <%= item %>
<% end %>
\end{itemize}
<% else %>
\begin{description}
<% for item in items %>
\item[<%= item %>] Description
<% end %>
\end{description}
<% end %>"""
      val dataList = JsonObject().apply {
        addProperty("useList", true)
        add("items", JsonArray().apply {
          add("A")
          add("B")
        })
      }
      val dataDesc = JsonObject().apply {
        addProperty("useList", false)
        add("items", JsonArray().apply {
          add("X")
          add("Y")
        })
      }
      val resultList = engine.render(template, dataList)
      assertTrue(resultList.contains("\\begin{itemize}"))
      assertTrue(resultList.contains("\\end{itemize}"))
      assertFalse(resultList.contains("\\begin{description}"))
      val resultDesc = engine.render(template, dataDesc)
      assertTrue(resultDesc.contains("\\begin{description}"))
      assertTrue(resultDesc.contains("\\end{description}"))
      assertFalse(resultDesc.contains("\\begin{itemize}"))
    }

    @Test
    fun `should handle triple nested structure - if containing for containing if-else`() {
      val template =
        """<% if enabled %><% for item in items %><% if item.type == "special" %>[<%= item.name %>]<% else %><%= item.name %><% end %><% end %><% end %>"""
      val data = JsonObject().apply {
        addProperty("enabled", true)
        add("items", JsonArray().apply {
          add(JsonObject().apply {
            addProperty("name", "A")
            addProperty("type", "special")
          })
          add(JsonObject().apply {
            addProperty("name", "B")
            addProperty("type", "normal")
          })
          add(JsonObject().apply {
            addProperty("name", "C")
            addProperty("type", "special")
          })
        })
      }
      assertEquals("[A]B[C]", engine.render(template, data))
    }
  }
}