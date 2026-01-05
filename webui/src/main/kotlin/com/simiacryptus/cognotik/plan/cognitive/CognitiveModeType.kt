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
        val entries: List<CognitiveModeType<*>> get() = values()
        val Chat = CognitiveModeType("Chat", ConversationalModeConfig::class.java, inputCnt = ConversationalMode.inputCnt)
        val Adaptive = CognitiveModeType("Adaptive", AdaptivePlanningConfig::class.java, inputCnt = AdaptivePlanningMode.inputCnt)
        val Waterfall = CognitiveModeType("Waterfall", WaterfallMode.WaterfallModeConfig::class.java, inputCnt = WaterfallMode.inputCnt)
        val Hierarchical = CognitiveModeType("Hierarchical", CognitiveModeConfig::class.java, inputCnt = HierarchicalPlanningMode.inputCnt)
        val Parallel = CognitiveModeType("Parallel", ParallelModeConfig::class.java, inputCnt = ParallelMode.inputCnt)
        val Protocol = CognitiveModeType("Protocol", ProtocolModeConfig::class.java, inputCnt = ProtocolMode.inputCnt)
        val Council = CognitiveModeType("Council", CouncilModeConfig::class.java, inputCnt = CouncilMode.inputCnt)
        val PersonaChat = CognitiveModeType("PersonaChat", PersonaChatConfig::class.java, inputCnt = PersonaChatMode.inputCnt)
        val Coding = CognitiveModeType("Coding", CodingModeConfig::class.java)

        private val constructors by lazy {
            val map =
                mutableMapOf<CognitiveModeType<*>, (OrchestrationConfig, Session, User) -> CognitiveMode<*>>()

            fun <U : CognitiveModeConfig> register(
                type: CognitiveModeType<U>,
                constructor: (OrchestrationConfig, Session, User) -> CognitiveMode<U>
            ) {
                map[type] = { config, session, user ->
                    constructor(config, session, user)
                }
                register(CognitiveModeType::class.java, type)
            }

            register(Chat) {  config, session, user -> ConversationalMode(config, session, user) }
            register(Adaptive) {  config, session, user -> AdaptivePlanningMode(config, session, user) }
            register(Waterfall) {  config, session, user -> WaterfallMode(config, session, user) }
            register(Hierarchical) {  config, session, user -> HierarchicalPlanningMode(config, session, user) }
            register(Parallel) {  config, session, user -> ParallelMode(config, session, user) }
            register(Protocol) {  config, session, user -> ProtocolMode(config, session, user) }
            register(Council) {  config, session, user -> CouncilMode(config, session, user) }
            register(PersonaChat) {  config, session, user -> PersonaChatMode(config, session, user) }
            register(Coding) {  config, session, user -> CodingMode(config, session, user) }
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