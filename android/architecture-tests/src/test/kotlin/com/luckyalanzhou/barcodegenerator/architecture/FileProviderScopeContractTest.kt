package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FileProvider 仅分享短期缓存文件，不应映射持久的局域网文件目录。 */
class FileProviderScopeContractTest {
    @Test
    fun providerPolicyRejectsBroadRootsEvenWhenCacheRootIsPresent() {
        listOf("root-path", "external-path", "files-path", "external-files-path").forEach { root ->
            val xml = """
                <paths>
                    <cache-path name="cache" path="." />
                    <$root name="broad" path="." />
                </paths>
            """.trimIndent()

            assertFalse("$root must not expand FileProvider beyond cache", providerPathsAreCacheScoped(xml))
        }
    }

    @Test
    fun providerPolicyRejectsEntireCacheDirectory() {
        val xml = """
            <paths>
                <cache-path name="cache" path="." />
            </paths>
        """.trimIndent()

        assertFalse("FileProvider must not expose the complete cache directory", providerPathsAreCacheScoped(xml))
    }

    @Test
    fun providerPolicyAllowsDedicatedShareCacheSubtree() {
        val xml = """
            <paths>
                <cache-path name="shared" path="share/" />
            </paths>
        """.trimIndent()

        assertTrue(providerPathsAreCacheScoped(xml))
    }

    @Test
    fun sharedUriConfigurationDoesNotExposePersistentAppFiles() {
        val paths = File("app/src/main/res/xml/file_paths.xml").readText()

        assertTrue("FileProvider 仅允许映射应用内部缓存目录", providerPathsAreCacheScoped(paths))
    }
}

private fun providerPathsAreCacheScoped(xml: String): Boolean =
    DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))
        .let { document ->
            if (document.documentElement.tagName != "paths") return false
            val nodes = document.getElementsByTagName("*")
            val entries = (0 until nodes.length).mapNotNull { index ->
                (nodes.item(index) as? Element)?.takeIf { it.tagName.endsWith("-path") }
                    ?.let { it.tagName to it.getAttribute("path").trim().trim('/') }
            }
            entries.isNotEmpty() && entries.all { (tag, path) -> tag == "cache-path" && path == "share" }
        }
