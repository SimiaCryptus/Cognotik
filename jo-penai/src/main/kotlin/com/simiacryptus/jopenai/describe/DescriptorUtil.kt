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

        }

    val Type.isArray: Boolean
        get() {

            return this is Class<*> && this.isArray
        }

    val Type.componentType: Type?
        get() {

            return when (this) {
                is Class<*> -> if (this.isArray) this.componentType else null
                is ParameterizedType -> this.actualTypeArguments.firstOrNull()
                else -> null
            }
        }

    fun resolveMethodReturnType(concreteClass: KClass<*>, methodName: String): KType {


        val method = concreteClass.memberFunctions.firstOrNull { it.name == methodName }
            ?: throw IllegalArgumentException("Method $methodName not found in class $concreteClass")

        var returnType = method.returnType

        if (returnType.classifier is KTypeParameter) {

            returnType = resolveGenericType(concreteClass, returnType)
        }

        return returnType
    }

    fun resolveGenericType(concreteClass: KClass<*>, kType: KType): KType {

        val classifier = kType.classifier

        if (classifier is KTypeParameter) {

            val typeArgument = concreteClass.typeParameters
                .firstOrNull { it.name == classifier.name }
                ?.let { typeParameter ->

                    concreteClass.supertypes.flatMap { it.arguments }.firstOrNull { argument ->
                        argument.type?.classifier == typeParameter
                    }?.type
                }


            return typeArgument ?: kType
        }

        return kType
    }
}