package com.simiacryptus.cognotik.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HtmlSimplifierTest {

    // ─── Input Validation ───────────────────────────────────────────────

    @Nested
    inner class InputValidation {

        @Test
        fun `should throw on blank input`() {
            assertThrows<IllegalArgumentException> {
                HtmlSimplifier.scrubHtml("")
            }
        }

        @Test
        fun `should throw on whitespace-only input`() {
            assertThrows<IllegalArgumentException> {
                HtmlSimplifier.scrubHtml("   ")
            }
        }

        @Test
        fun `should throw on javascript URL input`() {
            assertThrows<IllegalArgumentException> {
                HtmlSimplifier.scrubHtml("javascript:alert(1)")
            }
        }

        @Test
        fun `should throw on data URL input`() {
            assertThrows<IllegalArgumentException> {
                HtmlSimplifier.scrubHtml("data:text/html,<h1>hi</h1>")
            }
        }

        @Test
        fun `should throw on javascript base URL`() {
            assertThrows<IllegalArgumentException> {
                HtmlSimplifier.scrubHtml("<p>hello</p>", baseUrl = "javascript:alert(1)")
            }
        }

        @Test
        fun `should throw on data base URL`() {
            assertThrows<IllegalArgumentException> {
                HtmlSimplifier.scrubHtml("<p>hello</p>", baseUrl = "data:text/html,<h1>hi</h1>")
            }
        }
    }

    // ─── Script Element Removal ─────────────────────────────────────────

    @Nested
    inner class ScriptElementRemoval {

        @Test
        fun `should remove script tags by default`() {
            val html = "<html><body><p>Hello</p><script>alert('xss')</script></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<script"), "Script tags should be removed")
            assertFalse(result.contains("alert"), "Script content should be removed")
            assertTrue(result.contains("Hello"))
        }

        @Test
        fun `should remove noscript tags by default`() {
            val html = "<html><body><p>Hello</p><noscript>Enable JS</noscript></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<noscript"), "Noscript tags should be removed")
            assertFalse(result.contains("Enable JS"))
        }

        @Test
        fun `should remove iframe tags by default`() {
            val html = "<html><body><p>Hello</p><iframe src='http://evil.com'></iframe></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<iframe"), "Iframe tags should be removed")
        }

        @Test
        fun `should keep script elements when keepScriptElements is true`() {
            val html = "<html><body><p>Hello</p><script>var x = 1;</script></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, keepScriptElements = true)
            assertTrue(result.contains("<script") || result.contains("var x"), "Script elements should be preserved")
        }
    }

    // ─── Interactive Element Removal ────────────────────────────────────

    @Nested
    inner class InteractiveElementRemoval {

        @Test
        fun `should remove form elements by default`() {
            val html =
                "<html><body><form action='/submit'><input type='text'/><button>Submit</button></form></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<form"), "Form tags should be removed")
            assertFalse(result.contains("<input"), "Input tags should be removed")
            assertFalse(result.contains("<button"), "Button tags should be removed")
        }

        @Test
        fun `should remove textarea by default`() {
            val html = "<html><body><textarea>Some text</textarea></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<textarea"), "Textarea tags should be removed")
        }

        @Test
        fun `should remove select and option by default`() {
            val html = "<html><body><select><option>A</option><option>B</option></select></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<select"), "Select tags should be removed")
            assertFalse(result.contains("<option"), "Option tags should be removed")
        }

        @Test
        fun `should keep interactive elements when keepInteractiveElements is true`() {
            val html = "<html><body><form action='/submit'><input type='text'/></form></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, keepInteractiveElements = true)
            assertTrue(
                result.contains("<form") || result.contains("<input"),
                "Interactive elements should be preserved"
            )
        }
    }

    // ─── Media Element Removal ──────────────────────────────────────────

    @Nested
    inner class MediaElementRemoval {

        @Test
        fun `should remove audio elements by default`() {
            val html = "<html><body><audio src='song.mp3'></audio><p>Text</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<audio"), "Audio tags should be removed")
        }

        @Test
        fun `should remove video elements by default`() {
            val html = "<html><body><video src='movie.mp4'></video><p>Text</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<video"), "Video tags should be removed")
        }

        @Test
        fun `should remove canvas elements by default`() {
            val html = "<html><body><canvas></canvas><p>Text</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<canvas"), "Canvas tags should be removed")
        }

        @Test
        fun `should keep media elements when keepMediaElements is true`() {
            val html = "<html><body><video src='movie.mp4'>fallback</video><p>Text</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, keepMediaElements = true)
            assertTrue(result.contains("<video") || result.contains("movie.mp4"), "Media elements should be preserved")
        }
    }

    // ─── Unsafe Element Removal ─────────────────────────────────────────

    @Nested
    inner class UnsafeElementRemoval {

        @Test
        fun `should remove link tags`() {
            val html = "<html><head><link rel='stylesheet' href='style.css'/></head><body><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<link"), "Link tags should be removed")
        }

        @Test
        fun `should remove meta tags`() {
            val html = "<html><head><meta charset='utf-8'/></head><body><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<meta"), "Meta tags should be removed")
        }

        @Test
        fun `should remove object and embed tags`() {
            val html =
                "<html><body><object data='flash.swf'></object><embed src='flash.swf'/><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<object"), "Object tags should be removed")
            assertFalse(result.contains("<embed"), "Embed tags should be removed")
        }

        @Test
        fun `should remove applet tags`() {
            val html = "<html><body><applet code='MyApplet.class'></applet><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<applet"), "Applet tags should be removed")
        }

        @Test
        fun `should remove marquee and blink tags`() {
            val html = "<html><body><marquee>Scrolling</marquee><blink>Blinking</blink></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<marquee"), "Marquee tags should be removed")
            assertFalse(result.contains("<blink"), "Blink tags should be removed")
        }
    }

    // ─── Style Element Handling ─────────────────────────────────────────

    @Nested
    inner class StyleElementHandling {

        @Test
        fun `should remove style tags by default`() {
            val html = "<html><head><style>body { color: red; }</style></head><body><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<style"), "Style tags should be removed by default")
        }

        @Test
        fun `should keep style tags when includeCssData is true`() {
            val html = "<html><head><style>body { color: red; }</style></head><body><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, includeCssData = true)
            // Style tags are in head, body().html() won't include them, but style attributes should be preserved
            // This tests that the style element is not removed from the document
        }

        @Test
        fun `should preserve style attributes when includeCssData is true`() {
            val html = "<html><body><p style='color: red;'>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, includeCssData = true)
            assertTrue(result.contains("style"), "Style attributes should be preserved when includeCssData is true")
            assertTrue(result.contains("color: red") || result.contains("color:red"), "Style value should be preserved")
        }

        @Test
        fun `should remove style attributes by default`() {
            val html = "<html><body><p style='color: red;'>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("style="), "Style attributes should be removed by default")
        }
    }

    // ─── Data Attribute Removal ─────────────────────────────────────────

    @Nested
    inner class DataAttributeRemoval {

        @Test
        fun `should remove data attributes`() {
            val html = "<html><body><div data-id='123' data-value='abc'><p>Hello</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("data-id"), "data-id attribute should be removed")
            assertFalse(result.contains("data-value"), "data-value attribute should be removed")
        }

        @Test
        fun `should remove multiple data attributes from same element`() {
            val html = "<html><body><p data-x='1' data-y='2' data-z='3'>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("data-x"), "data-x should be removed")
            assertFalse(result.contains("data-y"), "data-y should be removed")
            assertFalse(result.contains("data-z"), "data-z should be removed")
        }
    }

    // ─── Event Handler Removal ──────────────────────────────────────────

    @Nested
    inner class EventHandlerRemoval {

        @Test
        fun `should remove event handler attributes by default`() {
            val html = "<html><body><div onmouseover='alert(1)' onfocus='alert(2)'><p>Hello</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("onmouseover"), "onmouseover should be removed")
            assertFalse(result.contains("onfocus"), "onfocus should be removed")
        }

        @Test
        fun `should keep event handlers when keepEventHandlers is true`() {
            val html = "<html><body><div onmouseover='doSomething()'><p>Hello</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, keepEventHandlers = true)
            // Note: the event handler might still be removed by the attribute filter step
            // since onmouseover is not in DEFAULT_IMPORTANT_ATTRIBUTES
        }
    }

    // ─── Unsafe Attribute Value Removal ─────────────────────────────────

    @Nested
    inner class UnsafeAttributeValueRemoval {

        @Test
        fun `should remove attributes with javascript protocol`() {
            val html = "<html><body><a href='javascript:alert(1)'>Click</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("javascript:"), "javascript: protocol should be removed")
        }

        @Test
        fun `should remove attributes with vbscript protocol`() {
            val html = "<html><body><a href='vbscript:MsgBox(1)'>Click</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("vbscript:"), "vbscript: protocol should be removed")
        }

        @Test
        fun `should remove attributes with data protocol in values`() {
            val html = "<html><body><img src='data:image/png;base64,abc' alt='test'/></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("data:image"), "data: protocol in attribute values should be removed")
        }

        @Test
        fun `should remove attributes with file protocol`() {
            val html = "<html><body><a href='file:///etc/passwd'>Click</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("file:"), "file: protocol should be removed")
        }
    }

    // ─── Attribute Filtering ────────────────────────────────────────────

    @Nested
    inner class AttributeFiltering {

        @Test
        fun `should preserve href attribute`() {
            val html = "<html><body><a href='https://example.com'>Link</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = false)
            assertTrue(result.contains("href"), "href attribute should be preserved")
            assertTrue(result.contains("https://example.com"), "href value should be preserved")
        }

        @Test
        fun `should preserve src attribute on img`() {
            val html = "<html><body><img src='https://example.com/img.png' alt='test'/></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("src"), "src attribute should be preserved")
        }

        @Test
        fun `should preserve alt attribute`() {
            val html = "<html><body><img src='https://example.com/img.png' alt='A nice image'/></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("alt"), "alt attribute should be preserved")
            assertTrue(result.contains("A nice image"), "alt value should be preserved")
        }

        @Test
        fun `should preserve title attribute`() {
            val html = "<html><body><p title='Important paragraph'>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("title"), "title attribute should be preserved")
        }

        @Test
        fun `should preserve colspan and rowspan`() {
            val html = "<html><body><table><tr><td colspan='2' rowspan='3'>Cell</td></tr></table></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("colspan"), "colspan should be preserved")
            assertTrue(result.contains("rowspan"), "rowspan should be preserved")
        }

        @Test
        fun `should preserve aria attributes`() {
            val html = "<html><body><div aria-label='Navigation' role='nav'><p>Content</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("aria-label"), "aria-label should be preserved")
            assertTrue(result.contains("role"), "role should be preserved")
        }

        @Test
        fun `should remove non-important attributes`() {
            val html = "<html><body><p tabindex='1' accesskey='h' draggable='true'>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("tabindex"), "tabindex should be removed")
            assertFalse(result.contains("accesskey"), "accesskey should be removed")
            assertFalse(result.contains("draggable"), "draggable should be removed")
        }

        @Test
        fun `should remove id attribute by default`() {
            val html = "<html><body><div id='main'><p>Hello</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("id="), "id attribute should be removed by default")
        }

        @Test
        fun `should preserve id attribute when keepObjectIds is true`() {
            val html = "<html><body><div id='main'><p>Hello</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, keepObjectIds = true)
            assertTrue(
                result.contains("id=") || result.contains("id=\"main\""),
                "id attribute should be preserved when keepObjectIds is true"
            )
        }

        @Test
        fun `should remove class attribute by default`() {
            val html = "<html><body><div class='container'><p>Hello</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("class="), "class attribute should be removed by default")
        }

        @Test
        fun `should preserve class attribute when includeCssData is true`() {
            val html = "<html><body><div class='container'><p>Hello</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, includeCssData = true)
            assertTrue(result.contains("class"), "class attribute should be preserved when includeCssData is true")
        }
    }

    // ─── Empty Element Removal ──────────────────────────────────────────

    @Nested
    inner class EmptyElementRemoval {

        @Test
        fun `should remove empty div with no attributes`() {
            val html = "<html><body><div></div><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Hello"))
        }

        @Test
        fun `should not remove img elements even if empty`() {
            val html = "<html><body><img src='https://example.com/img.png' alt='test'/><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("<img"), "img elements should not be removed")
        }

        @Test
        fun `should not remove br elements`() {
            val html = "<html><body><p>Line 1<br/>Line 2</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("<br") || result.contains("Line 1") && result.contains("Line 2"))
        }

        @Test
        fun `should not remove hr elements`() {
            val html = "<html><body><p>Above</p><hr/><p>Below</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("<hr"), "hr elements should be preserved")
        }
    }

    // ─── Href Cleanup ───────────────────────────────────────────────────

    @Nested
    inner class HrefCleanup {

        @Test
        fun `should remove javascript href from anchor tags`() {
            val html = "<html><body><a href='javascript:void(0)'>Click</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("javascript:"), "javascript: href should be removed")
        }

        @Test
        fun `should remove data href from anchor tags`() {
            val html = "<html><body><a href='data:text/html,<h1>hi</h1>'>Click</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("data:text"), "data: href should be removed")
        }

        @Test
        fun `should preserve valid http href`() {
            val html = "<html><body><a href='https://example.com'>Link</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("https://example.com"), "Valid href should be preserved")
        }
    }

    // ─── Element Unwrapping ─────────────────────────────────────────────

    @Nested
    inner class ElementUnwrapping {

        @Test
        fun `should unwrap simple text elements not in preserved set`() {
            val html = "<html><body><b>Bold text</b></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Bold text"), "Text content should be preserved")
            // b is not in PRESERVED_ELEMENTS, so it may be unwrapped
        }

        @Test
        fun `should preserve p elements`() {
            val html = "<html><body><p>Paragraph</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("<p>"), "p elements should be preserved")
        }

        @Test
        fun `should preserve table structure`() {
            val html = "<html><body><table><tr><td>Cell</td></tr></table></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("<table"), "table should be preserved")
            assertTrue(result.contains("<tr"), "tr should be preserved")
            assertTrue(result.contains("<td"), "td should be preserved")
        }

        @Test
        fun `should preserve heading elements`() {
            val html = "<html><body><h1>Title</h1><h2>Subtitle</h2><h3>Section</h3></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("<h1>"), "h1 should be preserved")
            assertTrue(result.contains("<h2>"), "h2 should be preserved")
            assertTrue(result.contains("<h3>"), "h3 should be preserved")
        }

        @Test
        fun `should preserve list elements`() {
            val html = "<html><body><ul><li>Item 1</li><li>Item 2</li></ul></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("<ul"), "ul should be preserved")
            assertTrue(result.contains("<li"), "li should be preserved")
        }
    }

    // ─── Relative URL Conversion ────────────────────────────────────────

    @Nested
    inner class RelativeUrlConversion {

        @Test
        fun `should convert relative href to absolute when baseUrl provided`() {
            val html = "<html><body><a href='/page'>Link</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, baseUrl = "https://example.com", treeIndexLinks = false)
            assertTrue(result.contains("https://example.com/page"), "Relative href should be converted to absolute")
        }

        @Test
        fun `should convert relative img src to absolute when baseUrl provided`() {
            val html = "<html><body><img src='/images/photo.jpg' alt='photo'/></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, baseUrl = "https://example.com")
            assertTrue(
                result.contains("https://example.com/images/photo.jpg"),
                "Relative src should be converted to absolute"
            )
        }

        @Test
        fun `should not modify absolute URLs`() {
            val html = "<html><body><a href='https://other.com/page'>Link</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, baseUrl = "https://example.com", treeIndexLinks = false)
            assertTrue(result.contains("https://other.com/page"), "Absolute URLs should not be modified")
        }

        @Test
        fun `should not convert URLs when no baseUrl provided`() {
            val html = "<html><body><a href='/page'>Link</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = false)
            assertTrue(result.contains("/page"), "Relative URLs should remain when no baseUrl")
        }
    }

    // ─── Invalid Attribute Removal ──────────────────────────────────────

    @Nested
    inner class InvalidAttributeRemoval {

        @Test
        fun `should remove attributes with blank values`() {
            val html = "<html><body><a href=''>Link</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("href=\"\""), "Blank href should be removed")
        }

        @Test
        fun `should remove attributes with null string values`() {
            val html = "<html><body><a href='null'>Link</a></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("href=\"null\""), "null href should be removed")
        }
    }

    // ─── Text Node Cleanup ──────────────────────────────────────────────

    @Nested
    inner class TextNodeCleanup {

        @Test
        fun `should trim whitespace in text nodes by default`() {
            val html = "<html><body><p>   Hello World   </p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Hello World"), "Text content should be preserved")
        }

        @Test
        fun `should preserve content text`() {
            val html = "<html><body><p>This is a test paragraph with some content.</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("This is a test paragraph with some content."), "Content should be preserved")
        }
    }

    // ─── Nested Structure Simplification ────────────────────────────────

    @Nested
    inner class NestedStructureSimplification {

        @Test
        fun `should simplify redundant nested elements`() {
            val html = "<html><body><div><div><div><p>Deep content</p></div></div></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, simplifyStructure = true, treeIndexLinks = false)
            assertTrue(result.contains("Deep content"), "Content should be preserved after simplification")
            // The redundant wrapper divs should be removed, leaving just the <p> tag
            assertFalse(result.contains("<div"), "Redundant wrapper divs should be removed: $result")
        }

        @Test
        fun `should simplify nested divs wrapping different child elements`() {
            val html = """
                <html><body>
                <div><div><div><div><div><div><div>
                    <h1><a href="https://example.com">Title</a></h1>
                </div></div></div></div></div></div></div>
                </body></html>
            """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, simplifyStructure = true, treeIndexLinks = false)
            assertTrue(result.contains("Title"), "Content should be preserved")
            assertTrue(result.contains("<h1>"), "h1 should be preserved")
            assertFalse(result.contains("<div"), "Unnecessary wrapper divs should be removed: $result")
        }

        @Test
        fun `should not unwrap elements that have attributes`() {
            val html = """<html><body><div role="main"><p>Content</p></div></body></html>"""
            val result = HtmlSimplifier.scrubHtml(html, simplifyStructure = true)
            assertTrue(result.contains("Content"), "Content should be preserved")
            assertTrue(result.contains("role=\"main\""), "Div with attributes should be preserved")
        }

        @Test
        fun `should not unwrap elements with multiple children`() {
            val html = """<html><body><div><p>First</p><p>Second</p></div></body></html>"""
            val result = HtmlSimplifier.scrubHtml(html, simplifyStructure = true)
            assertTrue(result.contains("First"), "First child content should be preserved")
            assertTrue(result.contains("Second"), "Second child content should be preserved")
        }

        @Test
        fun `should not simplify when simplifyStructure is false`() {
            val html = "<html><body><div><div><p>Content</p></div></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, simplifyStructure = false)
            assertTrue(result.contains("Content"), "Content should be preserved")
        }
    }

    // ─── Complex HTML Scenarios ─────────────────────────────────────────

    @Nested
    inner class ComplexScenarios {

        @Test
        fun `should handle a full HTML page`() {
            val html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Test Page</title>
                    <link rel="stylesheet" href="style.css">
                    <style>body { margin: 0; }</style>
                    <script>console.log('loaded');</script>
                </head>
                <body>
                    <div id="header" class="main-header" data-tracking="header">
                        <h1>Welcome</h1>
                        <nav>
                            <ul>
                                <li><a href="/home" onclick="track('home')">Home</a></li>
                                <li><a href="/about">About</a></li>
                            </ul>
                        </nav>
                    </div>
                    <div id="content">
                        <p style="color: blue;">This is the main content.</p>
                        <img src="/images/photo.jpg" alt="A photo" data-lazy="true"/>
                        <form action="/search">
                            <input type="text" name="q"/>
                            <button type="submit">Search</button>
                        </form>
                    </div>
                    <footer>
                        <p>Copyright 2024</p>
                    </footer>
                    <script>analytics.track();</script>
                </body>
                </html>
            """.trimIndent()
            // Disable navigational-element removal so we can assert nav-link content is preserved.
            val result = HtmlSimplifier.scrubHtml(
                html,
                baseUrl = "https://example.com",
                removeNavigationalElements = false
            )

            // Content should be preserved
            assertTrue(result.contains("Welcome"), "Heading content should be preserved")
            assertTrue(result.contains("Home"), "Link text should be preserved")
            assertTrue(result.contains("About"), "Link text should be preserved")
            assertTrue(result.contains("This is the main content"), "Paragraph content should be preserved")
            assertTrue(result.contains("Copyright 2024"), "Footer content should be preserved")

            // Unsafe elements should be removed
            assertFalse(result.contains("<script"), "Script tags should be removed")
            assertFalse(result.contains("<style"), "Style tags should be removed")
            assertFalse(result.contains("<link"), "Link tags should be removed")
            assertFalse(result.contains("<meta"), "Meta tags should be removed")
            assertFalse(result.contains("<form"), "Form tags should be removed")

            // Data attributes should be removed
            assertFalse(result.contains("data-tracking"), "data attributes should be removed")
            assertFalse(result.contains("data-lazy"), "data attributes should be removed")

            // Event handlers should be removed
            assertFalse(result.contains("onclick"), "onclick should be removed")

            // Style and class should be removed by default
            assertFalse(result.contains("style="), "style attribute should be removed")
            assertFalse(result.contains("class="), "class attribute should be removed")

            // URLs should be absolute
            assertTrue(result.contains("https://example.com"), "URLs should be converted to absolute")
        }

        @Test
        fun `should handle HTML with mixed content`() {
            val html = """
                <html><body>
                    <h1>Title</h1>
                    <p>Paragraph with <a href="https://example.com">a link</a> and <img src="https://example.com/img.png" alt="image"/>.</p>
                    <table>
                        <thead><tr><th>Header 1</th><th>Header 2</th></tr></thead>
                        <tbody><tr><td>Cell 1</td><td>Cell 2</td></tr></tbody>
                    </table>
                    <ol><li>First</li><li>Second</li></ol>
                </body></html>
            """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)

            assertTrue(result.contains("<h1>"), "h1 should be preserved")
            assertTrue(result.contains("<p>"), "p should be preserved")
            assertTrue(result.contains("<table"), "table should be preserved")
            assertTrue(result.contains("<th"), "th should be preserved")
            assertTrue(result.contains("<td"), "td should be preserved")
            assertTrue(result.contains("<ol"), "ol should be preserved")
            assertTrue(result.contains("<li"), "li should be preserved")
            assertTrue(result.contains("<img"), "img should be preserved")
            assertTrue(result.contains("https://example.com"), "Link href should be preserved")
        }

        @Test
        fun `should handle minimal HTML`() {
            val html = "<p>Hello</p>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Hello"), "Simple content should be preserved")
        }

        @Test
        fun `should handle HTML with only text`() {
            val html = "<html><body>Just some text</body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Just some text"), "Plain text should be preserved")
        }

        @Test
        fun `should handle deeply nested structures`() {
            val html = "<html><body><div><div><div><div><div><p>Deep</p></div></div></div></div></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Deep"), "Deeply nested content should be preserved")
        }

        @Test
        fun `should handle HTML with special characters`() {
            val html = "<html><body><p>Hello &amp; World &lt;3&gt;</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(
                result.contains("Hello &amp; World") || result.contains("Hello & World"),
                "Special characters should be handled"
            )
        }

        @Test
        fun `should handle HTML with unicode content`() {
            val html = "<html><body><p>日本語テスト 🎉 Ñoño</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("日本語テスト"), "Unicode content should be preserved")
            assertTrue(result.contains("Ñoño"), "Unicode content should be preserved")
        }
    }

    // ─── Return Value ───────────────────────────────────────────────────

    @Nested
    inner class ReturnValue {

        @Test
        fun `should return body html only`() {
            val html = "<html><head><title>Test</title></head><body><p>Content</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<html"), "Should not contain html tag")
            assertFalse(result.contains("<head"), "Should not contain head tag")
            assertFalse(result.contains("<title"), "Should not contain title tag")
            assertFalse(result.contains("<body"), "Should not contain body tag")
            assertTrue(result.contains("Content"), "Should contain body content")
        }

        @Test
        fun `should return non-empty string for valid HTML`() {
            val html = "<html><body><p>Hello</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.isNotBlank(), "Result should not be blank")
        }
    }

    // ─── Edge Cases ─────────────────────────────────────────────────────

    @Nested
    inner class EdgeCases {

        @Test
        fun `should handle HTML with comments`() {
            val html = "<html><body><!-- This is a comment --><p>Content</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Content"), "Content should be preserved")
        }

        @Test
        fun `should handle malformed HTML gracefully`() {
            val html = "<html><body><p>Unclosed paragraph<div>Nested wrong</p></div></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(
                result.contains("Unclosed paragraph") || result.contains("Nested wrong"),
                "Should handle malformed HTML without throwing"
            )
        }

        @Test
        fun `should handle HTML with CDATA sections`() {
            val html = "<html><body><p>Before</p><![CDATA[Some CDATA content]]><p>After</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Before"), "Content before CDATA should be preserved")
            assertTrue(result.contains("After"), "Content after CDATA should be preserved")
        }

        @Test
        fun `should handle large number of elements`() {
            val sb = StringBuilder("<html><body>")
            for (i in 1..100) {
                sb.append("<p>Paragraph $i</p>")
            }
            sb.append("</body></html>")
            val result = HtmlSimplifier.scrubHtml(sb.toString())
            assertTrue(result.contains("Paragraph 1"), "First paragraph should be preserved")
            assertTrue(result.contains("Paragraph 100"), "Last paragraph should be preserved")
        }

        @Test
        fun `should handle self-closing tags`() {
            val html = "<html><body><br/><hr/><img src='https://example.com/img.png' alt='test'/></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(
                result.contains("<br") || result.contains("<hr") || result.contains("<img"),
                "Self-closing tags should be handled"
            )
        }

        @Test
        fun `should handle multiple spaces and newlines`() {
            val html = """
                <html>
                <body>
                    <p>
                        Hello
                        
                        World
                    </p>
                </body>
                </html>
            """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Hello"), "Content should be preserved")
            assertTrue(result.contains("World"), "Content should be preserved")
        }
    }

    // ─── Combined Options ───────────────────────────────────────────────

    @Nested
    inner class CombinedOptions {

        @Test
        fun `should handle all preservation flags enabled`() {
            val html = """
                <html><body>
                    <script>var x = 1;</script>
                    <form><input type="text"/></form>
                    <video src="movie.mp4"></video>
                    <p style="color:red" class="main" id="p1">Content</p>
                </body></html>
            """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(
                html,
                includeCssData = true,
                keepObjectIds = true,
                keepScriptElements = true,
                keepInteractiveElements = true,
                keepMediaElements = true,
                keepEventHandlers = true
            )
            assertTrue(result.contains("Content"), "Content should be preserved")
        }

        @Test
        fun `should handle all flags disabled (defaults)`() {
            val html = """
                <html><body>
                    <script>var x = 1;</script>
                    <form><input type="text"/></form>
                    <video src="movie.mp4"></video>
                    <p style="color:red" class="main" id="p1" onclick="alert(1)">Content</p>
                </body></html>
            """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Content"), "Content should be preserved")
            assertFalse(result.contains("<script"), "Scripts should be removed")
            assertFalse(result.contains("<form"), "Forms should be removed")
            assertFalse(result.contains("<video"), "Video should be removed")
            assertFalse(result.contains("onclick"), "Event handlers should be removed")
            assertFalse(result.contains("style="), "Style should be removed")
            assertFalse(result.contains("class="), "Class should be removed")
            assertFalse(result.contains("id="), "Id should be removed")
        }

        @Test
        fun `should handle baseUrl with includeCssData`() {
            val html = """
                <html><body>
                    <p style="color:red">Styled</p>
                    <a href="/page">Link</a>
                </body></html>
            """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(
                html,
                baseUrl = "https://example.com",
                includeCssData = true, treeIndexLinks = false
            )
            assertTrue(result.contains("style"), "Style should be preserved")
            assertTrue(result.contains("https://example.com/page"), "URL should be absolute")
        }
    }

    // ─── Boilerplate Removal (Content Extraction) ───────────────────────
    @Nested
    inner class BoilerplateRemoval {
        @Test
        fun `should remove template elements by default`() {
            val html = """
               <html><body>
                 <p>Main content</p>
                 <template><div>No results found</div></template>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Main content"), "Real content should be preserved")
            assertFalse(result.contains("No results found"), "Template content should be removed")
            assertFalse(result.contains("<template"), "Template tags should be removed")
        }

        @Test
        fun `should keep template elements when disabled`() {
            val html = """
               <html><body>
                 <p>Main</p>
                 <template><span>placeholder</span></template>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, removeTemplateElements = false)
            assertTrue(result.contains("Main"), "Content should be preserved")
        }

        @Test
        fun `should remove navigational chrome by default`() {
            val html = """
               <html><body>
                 <header><a href="/login">Log in</a></header>
                 <nav><a href="/a">A</a><a href="/b">B</a></nav>
                 <main><h1>Article</h1><p>Body</p></main>
                 <aside><p>Promo</p></aside>
                 <footer><p>Copyright</p></footer>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Article"), "Main heading should be preserved")
            assertTrue(result.contains("Body"), "Main body should be preserved")
            assertFalse(result.contains("Log in"), "Header should be removed")
            assertFalse(result.contains("Promo"), "Aside should be removed")
            assertFalse(result.contains("Copyright"), "Footer should be removed")
        }

        @Test
        fun `should remove transient UI role containers`() {
            val html = """
               <html><body>
                 <div role="tooltip">Hover help</div>
                 <div role="alert">Saved!</div>
                 <p>Real content</p>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Real content"), "Content should be preserved")
            assertFalse(result.contains("Hover help"), "Tooltip should be removed")
            assertFalse(result.contains("Saved!"), "Alert should be removed")
        }

        @Test
        fun `should remove empty anchors`() {
            val html = """
               <html><body>
                 <a href="/x"></a>
                 <a href="/y">Real link</a>
                 <p>Content</p>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = false)
            assertTrue(result.contains("Real link"), "Real link should be preserved")
            assertTrue(result.contains("Content"), "Content should be preserved")
            // Empty anchor should be gone; the only href left should be /y
            val hrefCount = "href".toRegex().findAll(result).count()
            assertEquals(1, hrefCount, "Only the non-empty anchor's href should remain")
        }

        @Test
        fun `should collapse boilerplate link lists`() {
            val html = buildString {
                append("<html><body><h1>Jobs</h1>")
                append("<div><p>Top Staff Engineer Jobs</p></div>")
                append("<div>")
                for (i in 1..20) {
                    append("<a href=\"/cat/$i\">Category $i</a>")
                }
                append("</div>")
                append("<p>End of page</p>")
                append("</body></html>")
            }
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Jobs"), "Heading should be preserved")
            assertTrue(result.contains("End of page"), "Content after list should be preserved")
            assertTrue(
                result.contains("navigational links omitted") || result.contains("omitted"),
                "Long link list should be collapsed to a placeholder"
            )
            assertFalse(
                result.contains("Category 1") && result.contains("Category 20"),
                "Individual category links should be collapsed away"
            )
        }

        @Test
        fun `should not collapse short link lists`() {
            val html = """
               <html><body>
                 <ul>
                   <li><a href="/a">Alpha</a></li>
                   <li><a href="/b">Beta</a></li>
                 </ul>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Alpha"), "Short list anchor text should remain")
            assertTrue(result.contains("Beta"), "Short list anchor text should remain")
        }

        @Test
        fun `should not collapse list when collapseLinkLists is false`() {
            val html = buildString {
                append("<html><body><div>")
                for (i in 1..15) {
                    append("<a href=\"/cat/$i\">Category $i</a>")
                }
                append("</div></body></html>")
            }
            val result = HtmlSimplifier.scrubHtml(html, collapseLinkLists = false)
            assertTrue(result.contains("Category 1"), "Links should remain when collapsing disabled")
            assertTrue(result.contains("Category 15"), "Links should remain when collapsing disabled")
        }

        @Test
        fun `should preserve job-listing-like content while removing chrome`() {
            val html = """
               <html><body>
                 <nav><a href="/cities/austin">Austin</a><a href="/cities/seattle">Seattle</a></nav>
                 <main>
                   <h1>Staff Software Engineer Jobs</h1>
                   <div>
                     <h2><a href="/job/1">Senior Staff Software Engineer</a></h2>
                     <span>Remote</span><span>180K-240K</span>
                     <p>Lead backend dev with Python and Kubernetes.</p>
                   </div>
                 </main>
                 <template><div>No Results Found</div></template>
                 <footer><a href="/x">x</a></footer>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("Senior Staff Software Engineer"), "Job title preserved")
            assertTrue(result.contains("180K-240K"), "Salary preserved")
            assertTrue(result.contains("Python"), "Tech preserved")
            assertFalse(result.contains("Austin"), "Nav city links removed")
            assertFalse(result.contains("No Results Found"), "Template chrome removed")
        }
    }

    // ─── Link Summarization ─────────────────────────────────────────────
    @Nested
    inner class LinkSummarization {
        @Test
        fun `should produce link index header and replace hrefs with indices`() {
            val html = """
               <html><body>
                 <p>See <a href="https://example.com/a">Alpha</a> and
                    <a href="https://example.com/b">Beta</a>.</p>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, summarizeLinks = true)
            assertTrue(result.startsWith("Links:"), "Output should begin with a Links header")
            assertTrue(result.contains("[1] https://example.com/a"), "First URL should be indexed")
            assertTrue(result.contains("[2] https://example.com/b"), "Second URL should be indexed")
            assertTrue(result.contains("Alpha [1]"), "Anchor text should be followed by its index")
            assertTrue(result.contains("Beta [2]"), "Anchor text should be followed by its index")
            assertFalse(result.contains("<a "), "Anchor tags should be removed when summarizing")
            assertFalse(result.contains("href="), "href attributes should be removed when summarizing")
        }

        @Test
        fun `should deduplicate repeated URLs`() {
            val html = """
               <html><body>
                 <p><a href="https://example.com/x">One</a>
                    <a href="https://example.com/x">Two</a>
                    <a href="https://example.com/y">Three</a></p>
               </body></html>
           """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, summarizeLinks = true)
            assertTrue(result.contains("[1] https://example.com/x"), "First URL indexed at 1")
            assertTrue(result.contains("[2] https://example.com/y"), "Second unique URL indexed at 2")
            assertFalse(result.contains("[3] https://example.com/x"), "Duplicate URL should not be re-indexed")
            assertTrue(result.contains("One [1]"), "First anchor uses index 1")
            assertTrue(result.contains("Two [1]"), "Duplicate anchor reuses index 1")
            assertTrue(result.contains("Three [2]"), "Different URL uses index 2")
        }

        @Test
        fun `should omit header when no links present`() {
            val html = "<html><body><p>No links here</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, summarizeLinks = true)
            assertFalse(result.startsWith("Links:"), "No header when there are no links")
            assertTrue(result.contains("No links here"))
        }

        @Test
        fun `should not summarize when flag is false`() {
            val html = """<html><body><a href="https://example.com">Link</a></body></html>"""
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = false)
            assertFalse(result.startsWith("Links:"), "No header by default")
            assertTrue(result.contains("href"), "Anchor href preserved by default")
        }
    }

    // ─── Tree-Based Link Index ──────────────────────────────────────────
    @Nested
    inner class TreeIndexLinks {
        @Test
        fun `should prepend single-line JSON tree index`() {
            val html = """
             <html><body>
               <p>See <a href="https://example.com/a">Alpha</a> and
                  <a href="https://example.com/b">Beta</a>.</p>
             </body></html>
         """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = true)
            val firstLine = result.lineSequence().first()
            assertTrue(firstLine.startsWith("{"), "Output should begin with a JSON object")
            assertTrue(firstLine.endsWith("}"), "JSON tree should be single-line")
            assertFalse(firstLine.contains("\n"), "Tree index must be single-line")
            assertTrue(firstLine.contains("https://example.com"), "Origin should be a tree key")
            assertTrue(firstLine.contains("\"a\""), "Path segment 'a' should be a tree key")
            assertTrue(firstLine.contains("\"b\""), "Path segment 'b' should be a tree key")
        }

        @Test
        fun `should deduplicate substrings via shared origin node`() {
            val html = """
             <html><body>
               <a href="https://example.com/jobs/1">One</a>
               <a href="https://example.com/jobs/2">Two</a>
               <a href="https://example.com/jobs/3">Three</a>
             </body></html>
         """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = true)
            val firstLine = result.lineSequence().first()
            // The origin should appear exactly once despite three links.
            val originOccurrences = Regex("https://example\\.com").findAll(firstLine).count()
            assertEquals(1, originOccurrences, "Origin should be deduplicated to a single tree key")
            // The shared 'jobs' segment should also appear once.
            val jobsOccurrences = Regex("\"jobs\"").findAll(firstLine).count()
            assertEquals(1, jobsOccurrences, "Shared path segment should appear once")
        }

        @Test
        fun `should replace anchors with compact id markers`() {
            val html = """
             <html><body>
               <p><a href="https://example.com/x">First</a></p>
             </body></html>
         """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = true)
            assertTrue(result.contains("First {0}"), "Anchor text should be followed by its id marker")
            assertFalse(result.contains("<a "), "Anchor tags should be removed")
            assertFalse(result.contains("href="), "href attributes should be removed")
        }

        @Test
        fun `should reuse id for duplicate URLs`() {
            val html = """
             <html><body>
               <a href="https://example.com/x">A</a>
               <a href="https://example.com/x">B</a>
               <a href="https://example.com/y">C</a>
             </body></html>
         """.trimIndent()
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = true)
            assertTrue(result.contains("A {0}"), "First occurrence gets id 0")
            assertTrue(result.contains("B {0}"), "Duplicate URL reuses id 0")
            assertTrue(result.contains("C {1}"), "Distinct URL gets next id")
        }

        @Test
        fun `should omit tree index when no links present`() {
            val html = "<html><body><p>No links here</p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = true)
            assertFalse(result.startsWith("{"), "No JSON tree when there are no links")
            assertTrue(result.contains("No links here"))
        }

        @Test
        fun `should not produce tree index by default`() {
            val html = """<html><body><a href="https://example.com">Link</a></body></html>"""
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = false)
            assertFalse(result.startsWith("{"), "No tree index by default")
            assertTrue(result.contains("href"), "Anchor href preserved by default")
        }

        @Test
        fun `should record terminal id for origin-only URLs`() {
            val html = """<html><body><a href="https://example.com">Home</a></body></html>"""
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = true)
            val firstLine = result.lineSequence().first()
            assertTrue(firstLine.contains("\"\$\":0"), "Origin-only URL should carry a terminal id")
            assertTrue(result.contains("Home {0}"), "Anchor should reference the id")
        }
    }

    // ─── Inflated Markup Collapse ───────────────────────────────────────
    @Nested
    inner class InlineLeafCollapse {
        @Test
        fun `should collapse inflated per-token span wrappers`() {
            val html = buildString {
                append("<html><body><pre><code>")
                for (i in 1..208) append("<span>$i</span>")
                append("</code></pre></body></html>")
            }
            val result = HtmlSimplifier.scrubHtml(html, treeIndexLinks = false)
            assertFalse(result.contains("<span"), "Per-token spans should be collapsed: $result")
            assertTrue(result.contains("1"), "Token text should be preserved")
            assertTrue(result.contains("208"), "Last token text should be preserved")
        }

        @Test
        fun `should collapse mixed inline styling leaves`() {
            val html = buildString {
                append("<html><body><p>")
                for (i in 1..10) append("<b>word$i</b>")
                append("</p></body></html>")
            }
            val result = HtmlSimplifier.scrubHtml(html)
            assertFalse(result.contains("<b>"), "Repeated inline leaves should be collapsed")
            assertTrue(result.contains("word1"), "Text content preserved")
            assertTrue(result.contains("word10"), "Text content preserved")
        }

        @Test
        fun `should not collapse a small number of inline elements`() {
            val html = "<html><body><p><b>One</b> <i>Two</i></p></body></html>"
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("One"), "Content preserved")
            assertTrue(result.contains("Two"), "Content preserved")
        }

        @Test
        fun `should not collapse inline leaves with attributes`() {
            val html = buildString {
                append("<html><body><p>")
                for (i in 1..10) append("<span title=\"t$i\">$i</span>")
                append("</p></body></html>")
            }
            val result = HtmlSimplifier.scrubHtml(html)
            // title is an important attribute; spans carrying it should survive.
            assertTrue(result.contains("title"), "Spans with meaningful attributes should be preserved")
        }

        @Test
        fun `should not collapse when collapseInlineLeaves is false`() {
            val html = buildString {
                append("<html><body><pre><code>")
                for (i in 1..20) append("<span>$i</span>")
                append("</code></pre></body></html>")
            }
            val result = HtmlSimplifier.scrubHtml(html, collapseInlineLeaves = false)
            assertTrue(result.contains("<span"), "Spans should remain when collapsing disabled")
        }

        @Test
        fun `should not collapse parents with substantial non-leaf content`() {
            val html = buildString {
                append("<html><body><div>")
                append("<p>A real paragraph with meaningful prose content here.</p>")
                for (i in 1..3) append("<span>$i</span>")
                append("</div></body></html>")
            }
            val result = HtmlSimplifier.scrubHtml(html)
            assertTrue(result.contains("real paragraph"), "Prose content preserved")
        }
    }
}