package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.util.DynamicEnum
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class CognitiveConfigDialog(
    project: Project?,
    private val modeType: CognitiveModeType<*>,
    private val config: CognitiveModeConfig
) : DialogWrapper(project) {

    private val configFields = mutableMapOf<String, JComponent>()

    init {
        init()
        title = "Configure ${modeType.name} Mode"
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = panel {
            val kClass = config::class
            val properties = kClass.memberProperties
                .filter { it.name !in setOf("type", "name") }
                .sortedBy { it.name }

            if (properties.isEmpty()) {
                row {
                    text("No configurable settings for this mode.")
                }
            } else {
                for (prop in properties) {
                    val name = prop.name
                    val label = name
                        .replace(Regex("([^_ ])_([^_ ])"), "$1 $2")
                        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                        .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
                        .split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

                    val description = prop.findAnnotation<Description>()?.value
                    val returnType = prop.returnType
                    val classifier = returnType.classifier as? KClass<*>

                    prop.isAccessible = true
                    val currentValue = try {
                        prop.getter.call(config)
                    } catch (e: Exception) {
                        null
                    }

                    if (classifier == Boolean::class) {
                        row {
                            val checkBox = JCheckBox(label, currentValue as? Boolean ?: false)
                            if (description != null) checkBox.toolTipText = description
                            cell(checkBox).comment(description)
                            configFields[name] = checkBox
                        }
                    } else if (classifier == String::class) {
                        row(label + ":") {
                            val isTextArea = name.contains("prompt", ignoreCase = true) ||
                                    name.contains("description", ignoreCase = true)
                            if (isTextArea) {
                                val textArea = JBTextArea(currentValue as? String ?: "", 5, 40)
                                textArea.lineWrap = true
                                textArea.wrapStyleWord = true
                                if (description != null) textArea.toolTipText = description
                                cell(JScrollPane(textArea)).align(Align.FILL).comment(description)
                                configFields[name] = textArea
                            } else {
                                val textField = JBTextField(currentValue as? String ?: "")
                                if (description != null) textField.toolTipText = description
                                cell(textField).align(Align.FILL).comment(description)
                                configFields[name] = textField
                            }
                        }
                    } else if (classifier == Int::class || classifier == Long::class || classifier == Double::class) {
                        row(label + ":") {
                            val textField = JBTextField(currentValue?.toString() ?: "")
                            if (description != null) textField.toolTipText = description
                            cell(textField).comment(description)
                            configFields[name] = textField
                        }
                    } else if (classifier?.java?.isEnum == true) {
                        row(label + ":") {
                            val enumConstants = classifier.java.enumConstants
                            val items = enumConstants.map { it.toString() }.toTypedArray()
                            val comboBox = ComboBox(items)
                            comboBox.selectedItem = currentValue?.toString()
                            if (description != null) comboBox.toolTipText = description
                            cell(comboBox).comment(description)
                            configFields[name] = comboBox
                        }
                    } else if (classifier != null && DynamicEnum::class.java.isAssignableFrom(classifier.java)) {
                        row(label + ":") {
                            val companion = classifier.java.getDeclaredField("Companion").get(null)
                            val valuesMethod = companion.javaClass.getMethod("values")
                            val values = valuesMethod.invoke(companion) as List<DynamicEnum<*>>
                            val items = values.map { it.name }.toTypedArray()
                            val comboBox = ComboBox(items)
                            comboBox.selectedItem = (currentValue as? DynamicEnum<*>)?.name
                            if (description != null) comboBox.toolTipText = description
                            cell(comboBox).comment(description)
                            configFields[name] = comboBox
                        }
                    }
                }
            }
        }

        return JBScrollPane(dialogPanel).apply {
            preferredSize = Dimension(600, 500)
            border = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
    }

    fun getConfig(): CognitiveModeConfig {
        val kClass = config::class
        val properties = kClass.memberProperties
        for (prop in properties) {
            if (prop.name in configFields) {
                val component = configFields[prop.name]
                val value: Any? = when (component) {
                    is JCheckBox -> component.isSelected
                    is JBTextField -> {
                        val text = component.text.trim()
                        when (prop.returnType.classifier) {
                            Int::class -> text.toIntOrNull()
                            Long::class -> text.toLongOrNull()
                            Double::class -> text.toDoubleOrNull()
                            else -> text.ifEmpty { null }
                        }
                    }
                    is JBTextArea -> component.text.trim()
                    is ComboBox<*> -> {
                        val selected = component.selectedItem as? String
                        val paramClass = prop.returnType.classifier as? KClass<*>
                        if (selected != null && paramClass?.java?.isEnum == true) {
                            paramClass.java.enumConstants.find { it.toString() == selected }
                        } else if (selected != null && paramClass != null && DynamicEnum::class.java.isAssignableFrom(paramClass.java)) {
                            val companion = paramClass.java.getDeclaredField("Companion").get(null)
                            val valueOfMethod = companion.javaClass.getMethod("valueOf", String::class.java)
                            try {
                                valueOfMethod.invoke(companion, selected)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                
                if (prop is KMutableProperty<*>) {
                    try {
                        prop.setter.call(config, value)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
        return config
    }
}