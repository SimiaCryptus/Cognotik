package com.simiacryptus.jopenai.proxy

import com.simiacryptus.util.JsonUtil.toJson
import org.slf4j.LoggerFactory
import kotlin.reflect.full.memberProperties

interface ValidatedObject {
    fun validate(): String? = validateFields(this)

    class ValidationError(message: String, val obj: Any) : RuntimeException(
        " Error validating object: \n ```text\n${message}\n```\n\n```json\n${toJson(obj)}\n```"
    )

    companion object {
        private val log = LoggerFactory.getLogger(ValidatedObject::class.java)

        fun validateFields(obj: Any): String? {
            log.debug("Starting validation for object: ${obj.javaClass.name}")
            obj.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                val value = field.get(obj)
                log.debug("Validating field: ${field.name} with value: $value")
                if (value is ValidatedObject) {
                    val validate = value.validate()
                    log.warn("Validation failed for field: ${field.name} with message: $validate")
                    if (null != validate) return validate
                }

                if (value is List<*>) {
                    value.forEach {
                        log.debug("Validating list element: $it")
                        if (it is ValidatedObject) {
                            val validate = it.validate()
                            log.warn("Validation failed for list element with message: $validate")
                            if (null != validate) return validate
                        }
                    }
                }
            }
            obj.javaClass.kotlin.memberProperties.forEach { property ->
                val value = property.getter.call(obj)
                log.debug("Validating property: ${property.name} with value: $value")
                if (value is ValidatedObject) {
                    val validate = value.validate()
                    log.warn("Validation failed for property: ${property.name} with message: $validate")
                    if (null != validate) return validate
                }

                if (value is List<*>) {
                    value.forEach {
                        log.debug("Validating list element: $it")
                        if (it is ValidatedObject) {
                            val validate = it.validate()
                            log.warn("Validation failed for list element with message: $validate")
                            if (null != validate) return validate
                        }
                    }
                }
            }
            log.debug("Validation completed successfully for object: ${obj.javaClass.name}")
            return null
        }
    }
}