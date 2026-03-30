package com.simiacryptus.cognotik.plan.cognitive

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.DynamicEnum

@JsonDeserialize(using = CognitiveModeTypeDeserializer::class)
@JsonSerialize(using = CognitiveModeTypeSerializer::class)
class CognitiveModeType<out U : CognitiveModeConfig>(
  name: String,
  val configClass: Class<out U>,
  val description: String? = null,
  val inputCnt: Int = 1
) : DynamicEnum<CognitiveModeType<*>>(name) {
  companion object {
    @JvmStatic
    val entries: List<CognitiveModeType<*>> get() = values()

    val _constructors =
      mutableMapOf<CognitiveModeType<*>, (OrchestrationConfig, Session, User) -> CognitiveMode<*>>()

    fun <U : CognitiveModeConfig> registerCognitiveMode(
      type: CognitiveModeType<U>,
      constructor: (OrchestrationConfig, Session, User) -> CognitiveMode<U>
    ) {
      _constructors[type] = { config, session, user ->
        constructor(config, session, user)
      }
      register(CognitiveModeType::class.java, type)
    }
    private val constructors by lazy {
//
//      registerCognitiveMode(Chat) { config, session, user -> ConversationalMode(config, session, user) }
//      registerCognitiveMode(Adaptive) { config, session, user -> AdaptivePlanningMode(config, session, user) }
//      registerCognitiveMode(Waterfall) { config, session, user -> WaterfallMode(config, session, user) }
//      registerCognitiveMode(Hierarchical) { config, session, user -> HierarchicalPlanningMode(config, session, user) }
//      registerCognitiveMode(Parallel) { config, session, user -> ParallelMode(config, session, user) }
//      registerCognitiveMode(Protocol) { config, session, user -> ProtocolMode(config, session, user) }
//      registerCognitiveMode(Council) { config, session, user -> CouncilMode(config, session, user) }
//      registerCognitiveMode(PersonaChat) { config, session, user -> PersonaChatMode(config, session, user) }
//      registerCognitiveMode(Coding) { config, session, user -> CodingMode(config, session, user) }
      _constructors
    }

    fun values(): List<CognitiveModeType<*>> {
      @Suppress("SENSELESS_COMPARISON") require(constructors != null) // Trigger lazy init
      return values(CognitiveModeType::class.java)
    }

    fun valueOf(name: String): CognitiveModeType<*> {
      @Suppress("SENSELESS_COMPARISON") require(constructors != null) // Trigger lazy init
      return valueOf(CognitiveModeType::class.java, name)
    }
  }

  fun getImpl(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User
  ) = (constructors[this]?.invoke(orchestrationConfig, session, user)
    ?: throw IllegalStateException("No constructor for cognitive mode ${name}"))

  fun newSettings(): CognitiveModeConfig {
    val instance = configClass.getDeclaredConstructor().newInstance()
    instance.type = this
    return instance
  }
}