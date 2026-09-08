package icons

import com.intellij.openapi.util.IconLoader
import kotlin.jvm.java

object MyIcons {

    @JvmField
    val micActive = IconLoader.getIcon("/icons/Microphone_2.svg", MyIcons::class.java)

    @JvmField
    val micListening = IconLoader.getIcon("/icons/Microphone_3.svg", MyIcons::class.java)

    @JvmField
    val micInactive = IconLoader.getIcon("/icons/Microphone_1.svg", MyIcons::class.java)

    @JvmField
    val icon = IconLoader.getIcon("/icons/toolbarIcon.svg", MyIcons::class.java)
    /*
    IconLoader.findIcon(
        url = classLoader.getResource("./icons/toolbarIcon.svg"),
        storeToCache = true
    )
    */
}