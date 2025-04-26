package com.simiacryptus.jopenai.describe

import org.slf4j.LoggerFactory

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.memberFunctions

object DescriptorUtil {
    private val log = LoggerFactory.getLogger(DescriptorUtil::class.java)

    fun getAllAnnotations(
        rawType: Class<in Nothing>,
        property: KProperty1<out Any, *>,
    ): List<Annotation> =
        property.annotations + (rawType.kotlin.constructors.firstOrNull()?.parameters?.find { x -> x.name == property.name }?.annotations
            ?: listOf()).also {
//            log.info("Collected annotations for property '${property.name}' in class '${rawType.name}': $it")
        }

    val Type.isArray: Boolean
        get() {
//            log.trace("Checking if type '$this' is an array")
            return this is Class<*> && this.isArray
        }

    val Type.componentType: Type?
        get() {
//            log.trace("Getting component type for type '$this'")
            return when (this) {
                is Class<*> -> if (this.isArray) this.componentType else null
                is ParameterizedType -> this.actualTypeArguments.firstOrNull()
                else -> null
            }
        }


    fun resolveMethodReturnType(concreteClass: KClass<*>, methodName: String): KType {
//        log.info("Resolving return type for method '$methodName' in class '${concreteClass.simpleName}'")
        // Get the method from the class by name
        val method = concreteClass.memberFunctions.firstOrNull { it.name == methodName }
            ?: throw IllegalArgumentException("Method $methodName not found in class $concreteClass")

        // Start with the return type of the method
        var returnType = method.returnType

        // If the return type is a type parameter, try to resolve it
        if (returnType.classifier is KTypeParameter) {
//            log.info("Return type is a type parameter, attempting to resolve for method '$methodName'")
            returnType = resolveGenericType(concreteClass, returnType)
        }
//        log.info("Resolved return type for method '$methodName': $returnType")
        return returnType
    }

    fun resolveGenericType(concreteClass: KClass<*>, kType: KType): KType {
//        log.trace("Resolving generic type for '$kType' in class '${concreteClass.simpleName}'")
        val classifier = kType.classifier
        // Only proceed if the classifier is a type parameter
        if (classifier is KTypeParameter) {
            // Find the type parameter in the concrete class that matches by name
            val typeArgument = concreteClass.typeParameters
                .firstOrNull { it.name == classifier.name }
                ?.let { typeParameter ->
                    // Find the corresponding argument from the concrete class
                    concreteClass.supertypes.flatMap { it.arguments }.firstOrNull { argument ->
                        argument.type?.classifier == typeParameter
                    }?.type
                }
            // Return the found type argument, or the original type if not found
//            log.info("Resolved type argument for '$kType': ${typeArgument ?: kType}")
            return typeArgument ?: kType
        }
//        log.trace("No resolution needed for '$kType'")
        return kType
    }
}