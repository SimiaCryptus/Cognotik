package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties

class CognitiveConfigServlet : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val cognitiveModes = CognitiveModeType.values().map { modeType ->
            mapOf(
                "id" to modeType.name,
                "name" to (modeType.name.replace(Regex("([a-z])([A-Z]+)"), "$1 $2") + " Mode"),
                "description" to (modeType.description ?: ""),
                "configFields" to getConfigFields(modeType.configClass.kotlin)
            )
        }

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(JsonUtil.toJson(cognitiveModes))
    }

    private fun getConfigFields(kClass: KClass<out CognitiveModeConfig>): List<Map<String, Any>> {
        val instance = try {
            kClass.createInstance()
        } catch (e: Throwable) {
            null
        }
        return kClass.memberProperties
            .filter { it.name !in setOf("type") }
            .mapNotNull { prop ->
                val description = prop.annotations.filterIsInstance<Description>().firstOrNull()?.value
                val type = when (prop.returnType.classifier) {
                    Boolean::class -> "checkbox"
                    Int::class, Long::class, Double::class -> "number"
                    String::class -> if (prop.name.contains("code", true) || prop.name.contains(
                            "prompt",
                            true
                        )
                    ) "textarea" else "text"

                    else -> {
                        if ((prop.returnType.classifier as? KClass<*>)?.java?.isEnum == true) {
                            "select"
                        } else if (DynamicEnum::class.java.isAssignableFrom((prop.returnType.classifier as? KClass<*>)?.java)) {
                            "select"
                        } else {
                            null
                        }
                    }
                }

                if (type != null) {
                    val field = mutableMapOf<String, Any>(
                        "id" to prop.name,
                        "label" to prop.name
                            .replace(Regex("([^_ ])_([^_ ])"), "$1 $2")
                            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
                            .split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) },
                        "type" to type
                    )
                    if (description != null) field["tooltip"] = description

                    if (type == "select") {
                        val enumClass = (prop.returnType.classifier as KClass<*>).java
                        val enumConstants = enumClass.enumConstants
                        if (null != enumConstants && enumConstants.isNotEmpty()) {
                            field["options"] = enumConstants.map { it.toString() }
                            field["default"] = enumConstants.firstOrNull()?.toString() ?: ""
                        } else if (DynamicEnum::class.java.isAssignableFrom(enumClass)) {
                            val dynamicEnumCompanion = enumClass.getDeclaredField("Companion").get(null)
                            val valuesMethod = dynamicEnumCompanion.javaClass.getMethod("values")
                            val dynamicEnumValues = valuesMethod.invoke(dynamicEnumCompanion) as List<DynamicEnum<*>>
                            field["options"] = dynamicEnumValues.map { it.name }
                            field["default"] = dynamicEnumValues.firstOrNull()?.name ?: ""
                        }
                    }
                    if (instance != null) {
                        try {
                            val value = prop.getter.call(instance)
                            if (value != null) {
                                field["default"] = if (type == "select") value.toString() else value
                            }
                        } catch (e: Throwable) {
                            // Ignore
                        }
                    }

                    field
                } else null
            }
    }
}