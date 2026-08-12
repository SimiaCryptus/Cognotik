package com.simiacryptus.cognotik.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ErbTemplateEngineTest {

  private val engine = ErbTemplateEngine()

  private fun data(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

  @Nested
  inner class Output {
    @Test
    fun `renders a simple substitution`() {
      assertEquals("Hello World!", engine.render("Hello <%= name %>!", data("""{"name":"World"}""")))
    }

    @Test
    fun `renders literal text without expressions`() {
      assertEquals("Nothing to do", engine.render("Nothing to do", JsonObject()))
    }

    @Test
    fun `unknown variables render as empty`() {
      assertEquals("[]", engine.render("[<%= missing %>]", JsonObject()))
    }

    @Test
    fun `renders nested object properties`() {
      assertEquals("Bob", engine.render("<%= user.name %>", data("""{"user":{"name":"Bob"}}""")))
    }

    @Test
    fun `renders numbers and booleans`() {
      assertEquals("42 true", engine.render("<%= n %> <%= b %>", data("""{"n":42,"b":true}""")))
    }
  }

  @Nested
  inner class Filters {
    @Test
    fun `upper and lower`() {
      assertEquals("ABC abc", engine.render("<%= v | upper %> <%= v | lower %>", data("""{"v":"aBc"}""")))
    }

    @Test
    fun `escape escapes latex special characters`() {
      val out = engine.render("<%= v | escape %>", data("""{"v":"50% & #1_x"}"""))
      assertTrue(out.contains("\\%"), out)
      assertTrue(out.contains("\\&"), out)
      assertTrue(out.contains("\\#"), out)
      assertTrue(out.contains("\\_"), out)
    }

    @Test
    fun `markdown converts bold italic and code`() {
      val out = engine.render("<%= v | markdown %>", data("""{"v":"**b** *i* `c`"}"""))
      assertTrue(out.contains("\\textbf{b}"), out)
      assertTrue(out.contains("\\textit{i}"), out)
      assertTrue(out.contains("\\texttt{c}"), out)
    }

    @Test
    fun `join uses the supplied separator`() {
      assertEquals("a, b, c", engine.render("""<%= tags | join:", " %>""", data("""{"tags":["a","b","c"]}""")))
    }

    @Test
    fun `default supplies a fallback`() {
      assertEquals("N/A", engine.render("""<%= missing | default:"N/A" %>""", JsonObject()))
    }

    @Test
    fun `filters can be chained`() {
      assertEquals("AB", engine.render("<%= v | lower | upper %>", data("""{"v":"aB"}""")))
    }

    @Test
    fun `unknown filters degrade to toString`() {
      assertEquals("x", engine.render("<%= v | nosuchfilter %>", data("""{"v":"x"}""")))
    }

    @Test
    fun `custom filters can be registered`() {
      engine.registerFilter("exclaim") { v, _ -> "${v}!" }
      assertEquals("hi!", engine.render("<%= v | exclaim %>", data("""{"v":"hi"}""")))
    }
  }

  @Nested
  inner class Loops {
    @Test
    fun `iterates arrays`() {
      assertEquals(
        "a,b,c,",
        engine.render("<% for i in items %><%= i %>,<% end %>", data("""{"items":["a","b","c"]}"""))
      )
    }

    @Test
    fun `exposes loop metadata`() {
      val out = engine.render(
        "<% for i in items %><%= loop.index %>:<%= i %>;<% end %>",
        data("""{"items":["a","b"]}""")
      )
      assertEquals("0:a;1:b;", out)
    }

    @Test
    fun `iterates object entries`() {
      val out = engine.render(
        "<% for e in map %><%= e.key %>=<%= e.value %>;<% end %>",
        data("""{"map":{"a":1,"b":2}}""")
      )
      assertEquals("a=1;b=2;", out)
    }

    @Test
    fun `missing collections render nothing`() {
      assertEquals("[]", engine.render("[<% for i in missing %><%= i %><% end %>]", JsonObject()))
    }

    @Test
    fun `empty collections render nothing`() {
      assertEquals("[]", engine.render("[<% for i in items %><%= i %><% end %>]", data("""{"items":[]}""")))
    }

    @Test
    fun `nested loops are supported`() {
      val out = engine.render(
        "<% for row in rows %><% for c in row %><%= c %><% end %>|<% end %>",
        data("""{"rows":[["a","b"],["c"]]}""")
      )
      assertEquals("ab|c|", out)
    }
  }

  @Nested
  inner class Conditionals {
    @Test
    fun `true branch is rendered`() {
      assertEquals("yes", engine.render("<% if flag %>yes<% else %>no<% end %>", data("""{"flag":true}""")))
    }

    @Test
    fun `false branch is rendered`() {
      assertEquals("no", engine.render("<% if flag %>yes<% else %>no<% end %>", data("""{"flag":false}""")))
    }

    @Test
    fun `missing values are falsy`() {
      assertEquals("no", engine.render("<% if missing %>yes<% else %>no<% end %>", JsonObject()))
    }

    @Test
    fun `empty strings and arrays are falsy`() {
      assertEquals("no", engine.render("<% if s %>yes<% else %>no<% end %>", data("""{"s":""}""")))
      assertEquals("no", engine.render("<% if a %>yes<% else %>no<% end %>", data("""{"a":[]}""")))
    }

    @Test
    fun `negation is supported`() {
      assertEquals("yes", engine.render("<% if !flag %>yes<% else %>no<% end %>", data("""{"flag":false}""")))
    }

    @Test
    fun `equality comparison against a literal`() {
      assertEquals(
        "admin",
        engine.render("""<% if role == "admin" %>admin<% else %>user<% end %>""", data("""{"role":"admin"}"""))
      )
      assertEquals(
        "user",
        engine.render("""<% if role == "admin" %>admin<% else %>user<% end %>""", data("""{"role":"guest"}"""))
      )
    }

    @Test
    fun `inequality comparison against a literal`() {
      assertEquals(
        "other",
        engine.render("""<% if role != "admin" %>other<% else %>admin<% end %>""", data("""{"role":"guest"}"""))
      )
    }

    @Test
    fun `if without else renders nothing when false`() {
      assertEquals("[]", engine.render("[<% if flag %>yes<% end %>]", data("""{"flag":false}""")))
    }

    @Test
    fun `modulo arithmetic in comparisons`() {
      assertEquals("even", engine.render("<% if n % 2 == 0 %>even<% else %>odd<% end %>", data("""{"n":4}""")))
      assertEquals("odd", engine.render("<% if n % 2 == 0 %>even<% else %>odd<% end %>", data("""{"n":5}""")))
    }
  }

  @Nested
  inner class Functions {
    @Test
    fun `functions can be defined and called`() {
      val template = """<% def greet(name) %>return "Hello, " + name<% enddef %><%= greet("Bob") %>"""
      assertEquals("Hello, Bob", engine.render(template, JsonObject()))
    }

    @Test
    fun `functions are usable as filters`() {
      val template = """<% def shout(s) %>return s.toUpperCase()<% enddef %><%= name | shout %>"""
      assertEquals("WORLD", engine.render(template, data("""{"name":"World"}""")))
    }

    @Test
    fun `function definitions are removed from the output`() {
      val template = """<% def id(x) %>return x<% enddef %>[<%= id("a") %>]"""
      val out = engine.render(template, JsonObject())
      assertFalse(out.contains("def"), out)
      assertEquals("[a]", out)
    }

    @Test
    fun `calling an undefined function throws`() {
      assertThrows(IllegalArgumentException::class.java) {
        engine.callFunction("nope", "x")
      }
    }
  }

  @Nested
  inner class Schema {
    private val template = """
        ---
        <%#
        @type Data = {
          name: string;
          age?: number;
        };
        %>
        ---
        Name: <%= name %>
      """.trimIndent()

    @Test
    fun `extracts declared fields`() {
      val schema = engine.extractSchema(template)
      assertNotNull(schema)
      assertEquals(setOf("name", "age"), schema!!.fields.keys)
      assertFalse(schema.fields["name"]!!.optional)
      assertTrue(schema.fields["age"]!!.optional)
    }

    @Test
    fun `renders typescript definitions`() {
      val ts = engine.extractSchema(template)!!.toTypeScript()
      assertTrue(ts.contains("interface TemplateData {"), ts)
      assertTrue(ts.contains("name: string;"), ts)
      assertTrue(ts.contains("age?: number;"), ts)
    }

    @Test
    fun `templates without a preamble have no schema`() {
      assertNull(engine.extractSchema("Hello <%= name %>"))
    }

    @Test
    fun `preamble is stripped from the rendered output`() {
      assertEquals("Name: Bob", engine.render(template, data("""{"name":"Bob"}""")).trim())
    }

    @Test
    fun `strict validation rejects a missing required field`() {
      val strict = ErbTemplateEngine().apply { strictValidation = true }
      val e = assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        strict.render(template, data("""{"age":3}"""))
      }
      assertTrue(e.errors.any { it.path == "name" }, e.message)
    }

    @Test
    fun `strict validation rejects a wrong type`() {
      val strict = ErbTemplateEngine().apply { strictValidation = true }
      val e = assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        strict.render(template, data("""{"name":123}"""))
      }
      assertTrue(e.errors.any { it.path == "name" }, e.message)
    }

    @Test
    fun `strict validation accepts valid data`() {
      val strict = ErbTemplateEngine().apply { strictValidation = true }
      assertEquals("Name: Bob", strict.render(template, data("""{"name":"Bob","age":3}""")).trim())
    }

    @Test
    fun `lenient validation does not throw`() {
      assertEquals("Name:", engine.render(template, JsonObject()).trim())
    }

    @Test
    fun `array and object types are parsed`() {
      val complex = """
          ---
          <%#
          @type Data = {
            tags: string[];
            user: {
              name: string;
            };
            items: Array<number>;
          };
          %>
          ---
          ok
        """.trimIndent()
      val schema = engine.extractSchema(complex)!!
      assertTrue(schema.fields["tags"] is ErbTemplateEngine.FieldType.ArrayType)
      assertTrue(schema.fields["user"] is ErbTemplateEngine.FieldType.ObjectType)
      assertTrue(schema.fields["items"] is ErbTemplateEngine.FieldType.ArrayType)
    }

    @Test
    fun `strict validation validates nested arrays`() {
      val complex = """
          ---
          <%#
          @type Data = {
            tags: string[];
          };
          %>
          ---
          ok
        """.trimIndent()
      val strict = ErbTemplateEngine().apply { strictValidation = true }
      val bad = JsonObject().apply {
        add("tags", JsonArray().apply { add(1) })
      }
      val e = assertThrows(ErbTemplateEngine.TemplateValidationException::class.java) {
        strict.render(complex, bad)
      }
      assertTrue(e.errors.any { it.path == "tags[0]" }, e.message)
    }
  }
}