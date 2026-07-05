package com.simiacryptus.cognotik.describe

import java.lang.reflect.Type

interface MethodTypeDescriber {
  fun getMethodTypes(methodName: String): List<Type>
}