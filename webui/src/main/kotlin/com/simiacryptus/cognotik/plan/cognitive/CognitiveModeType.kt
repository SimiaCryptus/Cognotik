package com.simiacryptus.cognotik.plan.cognitive

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.DynamicEnum
import com.simiacryptus.cognotik.webui.session.SessionTask

@JsonDeserialize(using = CognitiveModeTypeDeserializer::class)
@JsonSerialize(using = CognitiveModeTypeSerializer::class)
class CognitiveModeType<out U : CognitiveModeConfig>(
    name: String,
    val configClass: Class<out U>,
    val description: String? = null,
    val inputCnt: Int = 1
) : DynamicEnum<CognitiveModeType<*>>(name) {
    companion object {
        val entries: List<CognitiveModeType<*>> get() = values()
        val Chat = CognitiveModeType("Chat", ConversationalModeConfig::class.java, inputCnt = ConversationalMode.inputCnt)
        val Adaptive = CognitiveModeType("Adaptive", AdaptivePlanningConfig::class.java, inputCnt = AdaptivePlanningMode.inputCnt)
        val Waterfall = CognitiveModeType("Waterfall", WaterfallMode.WaterfallModeConfig::class.java, inputCnt = WaterfallMode.inputCnt)
        val Hierarchical = CognitiveModeType("Hierarchical", CognitiveModeConfig::class.java, inputCnt = HierarchicalPlanningMode.inputCnt)
        val Parallel = CognitiveModeType("Parallel", ParallelModeConfig::class.java, inputCnt = ParallelMode.inputCnt)
        val Session = CognitiveModeType("Session", SessionModeConfig::class.java, inputCnt = SessionMode.inputCnt)
        val Protocol = CognitiveModeType("Protocol", ProtocolModeConfig::class.java, inputCnt = ProtocolMode.inputCnt)
        val Council = CognitiveModeType("Council", CouncilModeConfig::class.java, inputCnt = CouncilMode.inputCnt)
        val PrePlanned = CognitiveModeType("PrePlanned", PrePlannedModeConfig::class.java, inputCnt = PrePlannedMode.inputCnt)

        private val constructors by lazy {
            val map = mutableMapOf<CognitiveModeType<*>, (SessionTask, OrchestrationConfig, Session, User) -> CognitiveMode<*>>()
            fun <U : CognitiveModeConfig> register(
                type: CognitiveModeType<U>,
                constructor: (SessionTask, OrchestrationConfig, Session, User) -> CognitiveMode<U>
            ) {
                map[type] = { task, config, session, user ->
                    constructor(task, config, session, user)
                }
                register(CognitiveModeType::class.java, type)
            }

            register(Chat) { task, config, session, user -> ConversationalMode(task, config, session, user) }
            register(Adaptive) { task, config, session, user -> AdaptivePlanningMode(task, config, session, user, cognitiveStrategy = ProjectManagerStrategy()) }
            register(Waterfall) { task, config, session, user -> WaterfallMode(task, config, session, user) }
            register(Hierarchical) { task, config, session, user -> HierarchicalPlanningMode(task, config, session, user) }
            register(Parallel) { task, config, session, user -> ParallelMode(task, config, session, user) }
            register(Session) { task, config, session, user -> SessionMode(task, config, session, user) }
            register(Protocol) { task, config, session, user -> ProtocolMode(task, config, session, user) }
            register(Council) { task, config, session, user -> CouncilMode(task, config, session, user) }
            register(PrePlanned) { task, config, session, user -> PrePlannedMode(task, config, session, user) }
            map
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
        task: SessionTask,
        orchestrationConfig: OrchestrationConfig,
        session: Session,
        user: User
    ) = (constructors[this]?.invoke(task, orchestrationConfig, session, user)
     ?: throw IllegalStateException("No constructor for cognitive mode ${name}"))

    fun newSettings(): CognitiveModeConfig {
        val instance = configClass.getDeclaredConstructor().newInstance()
        instance.type = this
        return instance
    }
}