# Building Your First Kotlin Application with Gradle in IntelliJ IDEA

This tutorial guides beginners through the process of setting up a professional development environment using IntelliJ IDEA and the Gradle build system. You will learn how to initialize a project, understand the Kotlin project structure, and write a "Hello World" program that runs in the console.

**⏱️ Estimated Time:** 30 minutes

**🎯 Skill Level:** Beginner

**💻 Platform:** IntelliJ IDEA

---

## What You'll Learn

✓ Configure a new Kotlin/JVM project using the Gradle build system.
✓ Navigate the standard Kotlin project directory structure.
✓ Identify the role of the build.gradle.kts file.
✓ Write and execute a standard Kotlin main function.
✓ Utilize IntelliJ IDEA’s built-in tools to compile and run code.

---

## Prerequisites

### Required

- **IntelliJ IDEA** (software): Community or Ultimate edition (Version 2023.1 or newer recommended).
  - Download: https://www.jetbrains.com/idea/download/
- **Java Development Kit (JDK)** (software): Version 11, 17, or 21 (Can be downloaded directly through IntelliJ during the tutorial).
  - Download: https://adoptium.net/
- **Basic Computer Familiarity** (knowledge): Basic familiarity with using a computer (navigating folders and typing). No prior programming experience is required.
  - Download: 

### Optional

- **Hardware Requirements** (hardware): A computer running Windows, macOS, or Linux with at least 8GB of RAM recommended for smooth IDE performance.
  - Download: 

---

## Tutorial Steps

### Step 1: Initialize a New Kotlin Gradle Project

The first step in building any application is setting up the environment and project structure. We will use the IntelliJ Project Wizard, a built-in tool that automatically generates the folders and configuration files required for a Kotlin project. By choosing Gradle as our build system, we ensure that our project can easily manage external libraries and compile consistently across different computers.

### Actions

1. **Launch IntelliJ IDEA:** Click **New Project** or go to **File > New > Project...**.
2. **Configure Project Basics:** Set Name to `KotlinHelloWorld`, Language to **Kotlin**, and Build System to **Gradle**.
3. **Select the Gradle DSL:** Select **Kotlin** in the dropdown to use Kotlin for build settings.
4. **Set up the Project SDK (JDK):** Ensure a JDK (version 17 or higher) is selected. If not, use the **Download JDK...** option.
5. **Finalize Creation:** Uncheck "Add sample code" and click **Create**.

📸 The New Project dialog with Name, Language (Kotlin), and Build System (Gradle) highlighted

📸 The JDK selection dropdown showing the 'Download JDK' option

**Expected Outcome:** IntelliJ IDEA will close the wizard and open a new workspace window. You will see a progress bar at the bottom right labeled "Importing Gradle project" or "Indexing." Once finished, a project file tree will appear on the left side of the screen.

**Verify Success:**
1. Check the Project Tool Window (usually on the left) for the project structure.
2. Verify the existence of a '.gradle' folder.
3. Verify the existence of a 'src' folder.
4. Verify the existence of a file named 'build.gradle.kts'.

**⚠️ Common Issues:**
- Stuck on "Syncing Gradle": This is common during the first setup because IntelliJ is downloading the Gradle software in the background. Depending on your internet speed, this can take several minutes. Do not close the IDE until the progress bar at the bottom disappears.
- Firewall/Proxy Errors: If you are on a restricted corporate or school network, Gradle may fail to download the necessary components. You may need to configure your proxy settings in Appearance & Behavior > System Settings > HTTP Proxy.
- JDK Version Mismatch: If you see an error saying "Unsupported class file major version," it usually means your JDK is too old for the version of Gradle you are using. Ensure you are using at least JDK 17.

---

### Step 2: Explore the Project Structure and Gradle Configuration

Now that IntelliJ IDEA has finished generating your project, it is important to understand how it is organized. Gradle projects follow a standard directory structure. Knowing where your files live and how the build system is configured will help you manage your application as it grows.

In a Gradle-based Kotlin project, src/main/kotlin is the standard location for your production source code. Any Kotlin files you create here will be compiled into your final application. There is also a src/test folder, which is reserved for code that tests your application but isn't included in the final product.

Gradle is the 'build tool' that handles compiling your code, downloading libraries, and running your app. Its instructions are written in a file called build.gradle.kts. The .kts extension stands for 'Kotlin Script,' meaning this configuration file is actually written in Kotlin code!

The plugins block tells Gradle that this is a Kotlin project intended to run on the Java Virtual Machine (JVM). This enables Kotlin compilation support.

```kotlin
plugins {
    kotlin("jvm") version "1.9.22" // Version number may vary
}
```

This tells Gradle where to look for external libraries. mavenCentral() is the world's largest library of Java and Kotlin code.

```kotlin
repositories {
    mavenCentral()
}
```

This is where you list any "helper" libraries your project needs. By default, IntelliJ adds a dependency for Kotlin testing so you can write unit tests later.

```kotlin
dependencies {
    testImplementation(kotlin("test"))
}
```

📸 The Project tool window on the left, with the src/main/kotlin folder highlighted.

📸 The build.gradle.kts file open in the editor, with red boxes around the plugins and dependencies blocks.

**Expected Outcome:** By the end of this step, you should have the build.gradle.kts file open in your editor. You should recognize that your actual programming will happen inside the src/main/kotlin folder and that the project's "rules" are defined in the Gradle script.

**Verify Success:**
1. Check the Folder: Do you see a blue-colored folder icon for kotlin inside src/main? (IntelliJ colors "Source" folders blue to indicate they contain active code).
2. Check the Script: Does your build.gradle.kts contain the kotlin("jvm") line? If it says java instead of kotlin, the project was not initialized correctly as a Kotlin project.

**⚠️ Common Issues:**
- Missing src/main/kotlin folder: If you only see src/main/java, you can right-click the main folder, select New > Directory, and IntelliJ will often suggest kotlin as an option.
- Red Text in build.gradle.kts: If you see red underlines in the Gradle file, look at the bottom right of IntelliJ. If there is a small Gradle icon with a "refresh" symbol, click it to "Sync" the project. This tells IntelliJ to re-read the configuration and fix any errors.
- Project View is "Flat": If you don't see a tree structure, click the Gear Icon in the Project tool window and ensure "Tree Appearance > Compact Middle Packages" is unchecked to see the full folder path.

---

### Step 3: Create the Main Kotlin File

In a Kotlin Gradle project, the `src/main/kotlin` directory is the designated home for your application's source code. By creating a file here, you are telling the Kotlin compiler where to look for the logic it needs to execute. We are creating a **Kotlin File** (rather than a Class) because a simple "Hello World" application only requires a top-level function to run, and doesn't necessarily need to be wrapped inside a complex class structure.

**Instructions:**
1. **Locate the Source Folder:** In the **Project Tool Window** on the left side of IntelliJ IDEA, expand the folders until you see `src` > `main` > `kotlin`.
2. **Open the New File Menu:** Right-click directly on the `kotlin` folder. From the context menu, select **New > Kotlin Class/File**.
3. **Configure the File:** In the pop-up, type the name `Main`, select **File** from the list of options, and press **Enter**.
4. **Verify File Creation:** IntelliJ IDEA will generate the file and automatically open it in the main editor area.

📸 Right-clicking the src/main/kotlin folder and selecting New > Kotlin Class/File

📸 The 'New Kotlin Class/File' dialog with 'Main' typed in and 'File' highlighted in the list

**Expected Outcome:** You should now see a new file named `Main.kt` inside the `src/main/kotlin` directory in your project tree. The main editor window (the large area on the right) should be open and displaying the contents of `Main.kt`. Since we chose "File" instead of "Class," the file will likely be completely empty or contain only a `package` declaration at the top.

**Verify Success:**
1. Check the Extension: Ensure the file is named Main.kt. If it is just named Main without the .kt icon (a small blue and purple "K"), it may have been created as a plain text file.
2. Check the Location: Ensure Main.kt is nested directly under src/main/kotlin. If it is in src/main or the root project folder, the compiler will not find it correctly later. You can click and drag the file to move it if necessary.

**⚠️ Common Issues:**
- Missing 'Kotlin Class/File' Option: If you do not see "Kotlin Class/File" in the New menu, your IDE might still be "Indexing" or "Syncing" the Gradle project. Look at the bottom right corner of the window; if there is a progress bar, wait for it to finish and try again.
- Accidentally Created a Class: If your file opens and already contains the text `class Main { }`, you selected "Class" instead of "File." While you can still write code here, for this beginner tutorial, it is best to delete the file and try again, ensuring "File" is selected.
- Red Folder Icon: If the `kotlin` folder icon is plain gray instead of blue/green, IntelliJ hasn't recognized it as a "Source Root." Right-click the `kotlin` folder, select Mark Directory as > Sources Root.

---

### Step 4: Write the 'Hello World' Code

In this step, you will write the actual logic for your application. Every program needs an entry point—a specific place where the computer starts executing instructions. In Kotlin, this entry point is a special function called main. You will also write a command to display text on the screen.

Define the Main Function

```kotlin
fun main() {

}
```

Add the Print Statement

```kotlin
fun main() {
    println("Hello, World!")
}
```

📸 A close-up of the IntelliJ IDEA editor showing the Main.kt file with the completed code. Highlight the green "Play" arrow icon that appears in the "gutter" (the vertical margin) next to line 1.

**Expected Outcome:** You should see a syntactically correct Kotlin file. In IntelliJ IDEA, if you have typed everything correctly, a small green play button (triangle) will appear in the left-hand margin (the gutter) next to the line that says fun main(). This indicates that IntelliJ recognizes this as a runnable application.

**Verify Success:**
1. Check for Red Text: Ensure there are no red wavy underlines in your code. Red underlines indicate a 'syntax error' (a typo that prevents the code from working).
2. Check the Gutter: Look at the line numbers on the left. Do you see a green play icon next to fun main()? If yes, your entry point is correctly defined.
3. Check the File Name: Ensure the tab at the top of the editor still says Main.kt.

**⚠️ Common Issues:**
- Case Sensitivity: Kotlin is case-sensitive. If you type Fun instead of fun or Println instead of println, the code will not work. Ensure everything is lowercase as shown.
- Missing Quotes: If you forget the double quotes around Hello, World!, Kotlin will think you are trying to use a variable name instead of plain text, and you will see a red error.
- Missing Braces: Ensure every opening brace { has a matching closing brace }. If you delete one by accident, the program will be "incomplete."
- Wrong File Location: If you don't see the green play button, double-check that your Main.kt file is located inside src/main/kotlin. If it is in the "root" folder or the resources folder, it may not be recognized as source code.

---

### Step 5: Build and Run the Application

Now that you have written the code, it is time to transform that human-readable text into something the computer can execute. In this step, you will use Gradle (the build tool) to compile your Kotlin code into JVM bytecode and run it. IntelliJ IDEA recognizes the main function as the entry point and provides a green play arrow in the gutter. Clicking this allows you to run the program, which triggers the build process and opens the Run tool window to display the output.

📸 A close-up of the editor window showing the green play arrow in the gutter next to the fun main() declaration.

📸 The context menu appearing after clicking the play button, with 'Run MainKt' highlighted.

📸 The Run tool window at the bottom of IntelliJ IDEA showing the 'Hello, World!' output and the exit code 0.

**Expected Outcome:** The Run tool window displays 'Hello, World!' followed by 'Process finished with exit code 0'.

**Verify Success:**
1. Visual Check: Do you see the exact phrase 'Hello, World!' in the bottom panel?
2. Status Check: Does the text appear in white or black (standard) rather than red (error)?
3. File Check: If you look at your project folder now, you might notice a new 'build' folder.

**⚠️ Common Issues:**
- "Conflicting overloads" error: You might have two files with the same function name. Ensure you only have one Main.kt file with a main function.
- Red text in the Run window: This usually indicates a "Runtime Error" or a syntax mistake. Double-check your code for typos, especially missing quotes or parentheses.
- Play button is grayed out: Gradle might still be "syncing" or indexing your project. Wait for the progress bar at the bottom right to finish.
- "JDK not found": IntelliJ doesn't know which version of Java to use. Go to File > Project Structure > Project and ensure a Project SDK is selected.

---

## Troubleshooting

### 1. "Project SDK is not defined" or Red Code Everywhere

**Symptoms:**
- You see a gold bar at the top of the editor saying "Project SDK is not defined"
- Basic keywords like 'fun' and 'println' are underlined in red

**Possible Causes:**
- IntelliJ IDEA doesn't know which Java Development Kit (JDK) to use to compile your code

**Solutions:**
1. Set the Project SDK: Go to File > Project Structure > Project. Under SDK, select a version (e.g., JDK 17 or 21). If none are listed, click Add SDK > Download JDK.
2. Sync Gradle: Click the "Reload All Gradle Projects" icon in the Gradle tool window.

### 2. Gradle Sync Fails (Network or Proxy Errors)

**Symptoms:**
- The "Build" output window shows errors like 'Could not resolve all dependencies'
- Connection refused
- Unknown host

**Possible Causes:**
- Your computer is behind a corporate firewall/proxy
- Your internet connection was interrupted while Gradle was trying to download its necessary components

**Solutions:**
1. Check Internet: Ensure you have a stable connection for the initial setup.
2. Configure Proxy: Go to Settings (Ctrl+Alt+S) > Appearance & Behavior > System Settings > HTTP Proxy and select "Auto-detect" or "Manual proxy configuration".
3. Toggle Offline Mode: Ensure Gradle isn't stuck in "Offline Mode" in the Gradle sidebar.

### 3. "Permission Denied" when running ./gradlew (macOS/Linux)

**Symptoms:**
- bash: ./gradlew: Permission denied

**Possible Causes:**
- The Gradle wrapper script (gradlew) lost its "executable" file permission during project creation

**Solutions:**
1. Grant Permissions: Open the terminal at the bottom of IntelliJ.
2. Type 'chmod +x gradlew' and press Enter.
3. Try running your command again (e.g., ./gradlew run).

### 4. "Main method not found" or Run Button Greyed Out

**Symptoms:**
- You cannot find the green "Play" icon next to your code
- Clicking Run results in an error saying the main class could not be found

**Possible Causes:**
- The Kotlin file is in the wrong folder
- IntelliJ hasn't finished indexing the project

**Solutions:**
1. Check File Location: Ensure your Main.kt file is located inside src/main/kotlin.
2. Wait for Indexing: Look at the bottom status bar and wait for "Indexing..." to finish.
3. Manual Run: Right-click anywhere inside the fun main() function and select Run 'MainKt'.

### 5. Kotlin Version Mismatch

**Symptoms:**
- Error message: The Kotlin version used in the build script (1.x.x) is newer than the IDE plugin (1.x.y).

**Possible Causes:**
- Your IntelliJ IDEA Kotlin plugin is outdated compared to the version Gradle is trying to use

**Solutions:**
1. Update Plugin: Go to Settings > Plugins. Search for "Kotlin" and click Update if available.
2. Match Versions: Open your build.gradle.kts file and change the kotlin("jvm") version to match the version supported by your IDE.

### 6. "Could not find or load main class" (Build Folder Corruption)

**Symptoms:**
- The project compiles, but when you run it, it fails immediately with a ClassNotFoundException

**Possible Causes:**
- The build cache has become corrupted or out of sync with your changes

**Solutions:**
1. Clean Project: Go to the Gradle sidebar, expand Tasks > build, and double-click clean.
2. Invalidate Caches: Go to File > Invalidate Caches..., check all boxes, and click Invalidate and Restart.

### 7. High Memory Usage / Slow Performance

**Symptoms:**
- IntelliJ IDEA feels sluggish
- You see "Low Memory" warnings while Gradle is syncing

**Possible Causes:**
- The default memory allocation for IntelliJ or the Gradle Daemon is too low for your system

**Solutions:**
1. Increase IDE Memory: Go to Help > Change Memory Settings and increase the "Maximum Heap Size" to at least 2048 MB.
2. Gradle Properties: Create a file named gradle.properties in your project root and add: org.gradle.jvmargs=-Xmx2048m

---

## Next Steps

🎉 Congratulations on completing this tutorial!

### Try These Next
- Make the application interactive using the readln() function
- Add logic using if/else statements or when expressions
- Modularize the code by creating separate functions
- Add an external library like kotlinx-datetime via Gradle
- Create a Calculator or To-Do List console application

### Related Resources
- Kotlin Koans (interactive exercises)
- Kotlin Docs: Basic Syntax
- Gradle Build Language Reference
- JetBrains Academy (Hyperskill) Kotlin Basics track

### Advanced Topics
- Null Safety (nullable types and safe call operators)
- Object-Oriented Programming (Classes, Data Classes, and Inheritance)
- Functional Programming & Collections (.map, .filter, .reduce)
- Coroutines for asynchronous tasks

