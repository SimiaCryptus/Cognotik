# Icons Package

This package provides centralized access to the graphical assets used by the IntelliJ plugin. It ensures that icons are loaded correctly with support for high-DPI displays and IDE themes.

## MyIcons

The `MyIcons` object contains static references to SVG icons located in the plugin's resource directory.

### Available Icons

| Property | Resource Path | Description |
| :--- | :--- | :--- |
| `micActive` | `/icons/Microphone_2.svg` | Displayed when the microphone is active and ready for input. |
| `micListening` | `/icons/Microphone_3.svg` | Displayed when the system is actively capturing or processing audio. |
| `micInactive` | `/icons/Microphone_1.svg` | Displayed when the microphone is disabled or in a standby state. |
| `icon` | `/icons/toolbarIcon.svg` | The primary brand icon used for the plugin's toolbar and tool windows. |

### Usage

Icons should be accessed via the `MyIcons` object to ensure consistent resource management:

```kotlin
import icons.MyIcons

// Example: Setting an icon on a label or button
val label = JLabel(MyIcons.micActive)
```

### Implementation Notes

- **Format**: All icons are expected to be in SVG format to support seamless scaling.
- **Loading**: Icons are loaded using `com.intellij.openapi.util.IconLoader`, which handles theme switching (e.g., Darcula vs. Light) automatically if the appropriate resource variants exist.
- **JVM Compatibility**: Fields are annotated with `@JvmField` for direct access from Java code if necessary.