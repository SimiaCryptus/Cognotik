# Building Your First Kotlin Application with Gradle in IntelliJ IDEA

This tutorial provides a step-by-step guide for beginners to create, configure, and run a "Hello World" application using the Kotlin programming language and the Gradle build system. You will learn how to navigate the IntelliJ IDEA interface and understand the basic structure of a modern JVM (Java Virtual Machine) project.

**⏱️ Estimated Time:** 30 minutes

**🎯 Skill Level:** Beginner

**💻 Platform:** IntelliJ IDEA

---

## What You'll Learn

✓ Initialize a new Kotlin project using the Gradle build tool.
✓ Identify the key components of a Gradle project structure (e.g., build.gradle.kts and source folders).
✓ Write a standard Kotlin entry point function (main).
✓ Compile and execute Kotlin code within the IntelliJ IDEA environment.

---

## Prerequisites

### Required

- **IntelliJ IDEA** (software): Community or Ultimate edition
  - Download: https://www.jetbrains.com/idea/download/
- **Java Development Kit (JDK)** (software): Version 17 or higher (can be installed directly through IntelliJ)
  - Download: https://www.oracle.com/java/technologies/downloads/
- **Basic Computer Familiarity** (knowledge): Navigating folders and typing

### Optional

- **Hardware Requirements** (hardware): A Windows, macOS, or Linux computer with at least 8GB of RAM recommended

---

## Tutorial Steps

### Step 1: Initialize the Project

The first step in creating any application is setting up the environment and the project structure. We will use the IntelliJ Project Wizard, which automates the creation of folders and configuration files. By choosing Gradle as our build system, we ensure that our project can easily manage external libraries and compile our code consistently.

### Actions
1. **Launch IntelliJ IDEA:** Open IntelliJ IDEA and click **New Project** or go to **File > New > Project...**.
2. **Configure Project Details:** Set Name to `HelloWorldKotlin` and Language to **Kotlin**.
3. **Set the Build System and DSL:** Select **Gradle** as the Build System, ensure a **JDK (version 17 or 21)** is selected, and set the Gradle DSL to **Kotlin**.
4. **Finalize:** Leave 'Add sample code' unchecked and click **Create**.

📸 The New Project dialog box with "Kotlin" selected, "Gradle" chosen as the build system, and JDK 17/21 highlighted.

📸 The main IntelliJ window showing the "Background Tasks" progress bar at the bottom right.

**Expected Outcome:** After clicking Create, a new IntelliJ IDEA window will open. You will see a progress bar at the bottom of the screen indicating that Gradle is "syncing" or "importing." During this time, IntelliJ is downloading the necessary Gradle files and setting up the folder structure (like src/main/kotlin).

**Verify Success:**
1. Check the Project View: On the left-hand side, you should see a folder tree. Expand the folders to ensure you see a directory named src.
2. Check the Build Log: Click the Build tab at the bottom of the screen. You should eventually see a message that says 'Build successful' or 'Sync finished.'
3. Verify Configuration Files: Look for a file in the root directory named build.gradle.kts. This file confirms that your Gradle build system is ready.

**⚠️ Common Issues:**
- Slow Syncing: If this is your first time using Gradle, it may take several minutes to download the required components. Do not close the application until the progress bar at the bottom disappears.
- JDK Not Found: If you receive an error saying "JDK not found," go to File > Project Structure > Project and ensure a valid JDK is selected in the "SDK" dropdown.
- Firewall/Proxy Blocks: In some corporate or school environments, a firewall may block Gradle from downloading. If the sync fails with a "Connection Refused" error, you may need to configure your proxy settings in Appearance & Behavior > System Settings > HTTP Proxy.

---

### Step 2: Explore the Project Structure

Now that IntelliJ IDEA has finished initializing your project, it is important to understand how a Kotlin/Gradle project is organized. Gradle follows a 'convention over configuration' approach, meaning it expects files to be in specific places so it knows how to compile and run them automatically.

Key areas to explore:
1. **Source Code Directory**: Navigate to `src` > `main` > `kotlin`. This is where your application's production code resides.
2. **Build Configuration**: Open `build.gradle.kts`. This file is the 'brain' of your project, defining plugins (like the Kotlin plugin), repositories (where to download libraries), and dependencies.
3. **Project Settings**: Open `settings.gradle.kts`. This file defines high-level settings like the project name and included modules.

Example of the project name definition found in settings.gradle.kts

```kotlin
rootProject.name = "your-project-name"
```

📸 The Project Tool Window expanded to show the src/main/kotlin directory highlighted.

**Expected Outcome:** By the end of this step, you should see several folders and files in your sidebar. Your editor should have build.gradle.kts open, showing the configuration code. You should now know that your actual Kotlin code will eventually be placed inside the src/main/kotlin folder.

**Verify Success:**
1. Verify if the 'kotlin' folder under 'src/main' is colored blue, indicating it is recognized as a Source Root.
2. Check if the 'build' folder is visible (usually orange), which contains automatically managed compiled files.

**⚠️ Common Issues:**
- The src folder is missing: Gradle might still be syncing. Look for a progress bar or go to File > Sync Project with Gradle Files.
- Folders look like a flat list: Click the Gear Icon in the Project Tool Window and uncheck 'Compact Middle Packages'.
- Red text in build.gradle.kts: Click the Gradle Elephant icon in the top right corner to 'Load Gradle Changes'.

---

### Step 3: Create the Main Kotlin File

Now that your project structure is ready, it is time to create the actual file where you will write your code. In a Kotlin project, the source code is stored within the `src/main/kotlin` directory by default. This organization helps the Gradle build system find and compile your code correctly. You will navigate to the `kotlin` source root, create a new Kotlin file named `Main.kt`, and implement the standard entry point function.

The entry point function for the Kotlin application that prints text to the console.

*Run in: `src/main/kotlin`*

```kotlin
fun main() {
    println("Hello World!")
}
```

📸 Right-clicking src/main/kotlin and selecting New > Kotlin Class/File

📸 The IntelliJ editor showing the Main.kt file with the code typed in

**Expected Outcome:** You should now see a file named Main.kt inside your src/main/kotlin folder. In the main editor window, the code you typed should be visible, and you should notice a small green play icon (a triangle) in the "gutter" (the vertical margin) to the left of the line fun main().

**Verify Success:**
1. Check the File Name: Ensure the file is named Main.kt (case-sensitive).
2. Check for Errors: Look at the top right of the editor window. If you see a green checkmark, your code is valid.
3. Verify the Play Button: Confirm that the green play icon appears next to line 1.

**⚠️ Common Issues:**
- Red Code/Unresolved References: If println or fun are red, your project might still be indexing. Wait for the progress bar at the bottom of IntelliJ to finish.
- Missing 'Kotlin Class/File' Option: If you don't see this option when right-clicking, ensure you are right-clicking the kotlin folder specifically.
- Case Sensitivity: Kotlin is case-sensitive. Ensure fun and println are all lowercase.

---

### Step 4: Build and Run the Application

The purpose of this step is to transform the human-readable Kotlin code you wrote into JVM bytecode (a format the computer can understand) and then execute it. This process is called 'Building and Running.' IntelliJ IDEA uses the Gradle build system to automate these tasks, ensuring all necessary libraries are linked correctly. You will locate the green play icon in the gutter next to the main function, execute the run command, and monitor the build process and output in the Run tool window.

Modified print statement for validation

*Run in: `src/main/kotlin/Main.kt`*

```kotlin
println("Kotlin is running!")
```

📸 A close-up of the gutter with the green play icon and the 'Run MainKt' menu option highlighted.

📸 The Run tool window at the bottom of IntelliJ IDEA showing the 'Hello World!' text.

**Expected Outcome:** In the Run tool window, you should see the output 'Hello World!' followed by 'Process finished with exit code 0'. The exit code 0 indicates the program ran successfully.

**Verify Success:**
1. Change the text inside the quotation marks in your Main.kt file to: println("Kotlin is running!").
2. Click the green play icon again (or click the smaller green play icon in the top-right toolbar of IntelliJ).
3. Confirm that the Run tool window now displays 'Kotlin is running!'.

**⚠️ Common Issues:**
- Issue: 'No SDK found' or 'Project SDK is not defined' - Fix: Click the 'Setup SDK' link and select an installed JDK (version 11, 17, or 21).
- Issue: The Run icon is greyed out or doesn't appear - Fix: Wait for Gradle to finish syncing or go to File > Invalidate Caches... and restart.
- Issue: 'Build Failed' with red text in the console - Fix: Check for typos, missing parentheses, or misspelled keywords like 'println'.
- Issue: 'Main method not found' - Fix: Ensure the function is named exactly 'main' (lowercase) and is a top-level function.

---

### Step 5: Modify and Re-run

Now that you have successfully executed your first Kotlin program, it is time to practice the most common cycle in software development: the Edit-Run-Repeat cycle. In this step, you will learn how to modify your source code, add new instructions, and use shortcuts to quickly see the results of your changes. You will modify the existing println statement, add a second one to see how Kotlin executes code line-by-line, and use professional shortcuts to rebuild and run the application.

Modify the content of the String passed to the println function.

*Run in: `src/main/kotlin`*

```kotlin
println("Kotlin is awesome!")
```

Add a second println statement on a new line.

*Run in: `src/main/kotlin`*

```kotlin
println("I am learning the development workflow.")
```

The complete Main.kt file after modifications.

*Run in: `src/main/kotlin`*

```kotlin
fun main() {
    println("Kotlin is awesome!")
    println("I am learning the development workflow.")
}
```

**Expected Outcome:** The Run tool window will automatically open at the bottom of your screen. You should see your two custom messages printed on separate lines: 'Kotlin is awesome!' and 'I am learning the development workflow.', followed by 'Process finished with exit code 0'.

**Verify Success:**
1. Output Match: Does the text in the Run window exactly match the text you typed inside the quotation marks?
2. Line Count: Are there two distinct lines of text?
3. Exit Code: Does it still say 'Process finished with exit code 0'?

**⚠️ Common Issues:**
- Red Squiggly Lines: If you see red lines, you likely have a syntax error. Check that every quotation mark and parenthesis has a matching pair.
- Unresolved Reference: If 'println' is red, ensure you haven't accidentally deleted the 'fun main() {' line or the closing brace '}'.
- Shortcut Not Working: If Shift + F10 doesn't work, your keyboard might require you to hold the Fn key as well (Fn + Shift + F10).

---

## Troubleshooting

### 1. Project SDK is Not Defined

**Symptoms:**
- A yellow bar at the top of the editor says 'Project SDK is not defined'
- Error: Cannot solve symbol 'String' or 'println'

**Possible Causes:**
- You haven't installed a JDK
- IntelliJ hasn't been told which installed JDK to use for this specific project

**Solutions:**
1. Go to File > Project Structure (or press Ctrl+Alt+Shift+S)
2. Under Project Settings, click on Project
3. In the SDK dropdown, select an installed JDK (e.g., version 17 or 21)
4. If none are listed, click Add SDK > Download JDK, select a vendor (like Oracle or Corretto), and click Download
5. Click Apply and OK

### 2. The "Run" Button (Green Arrow) is Greyed Out

**Symptoms:**
- The green play button in the top right or next to the main function is disabled or missing

**Possible Causes:**
- The Kotlin file is in the wrong directory (e.g., in src instead of src/main/kotlin)
- The main function signature is incorrect
- The project hasn't finished indexing

**Solutions:**
1. Ensure your .kt file is located inside src/main/kotlin
2. Ensure your function looks exactly like this: fun main() { ... }
3. Wait for indexing to finish (check the bottom status bar)
4. Right-click anywhere inside the main function in the editor and select Run 'FileNameKt'

### 3. Gradle Sync Failed (Connection Issues)

**Symptoms:**
- Error messages like 'Could not resolve all dependencies'
- 'Connection refused'
- 'Build failed' during the initial setup

**Possible Causes:**
- A firewall or proxy is blocking Gradle
- You are working offline without the necessary files cached

**Solutions:**
1. Ensure you have a stable internet connection for the first build
2. Open the Gradle Tool Window and click the 'Reload All Gradle Projects' icon (two circling arrows)
3. Go to File > Settings > Appearance & Behavior > System Settings > HTTP Proxy and enter your network's proxy details

### 4. "Unresolved Reference" Errors (Red Text Everywhere)

**Symptoms:**
- The editor shows red squiggly lines under basic Kotlin keywords
- The project won't compile

**Possible Causes:**
- The IntelliJ cache is corrupted
- The Gradle sync is out of date

**Solutions:**
1. Click the 'Reload All Gradle Projects' button in the Gradle side panel
2. Go to File > Invalidate Caches...
3. Check all boxes and click Invalidate and Restart

### 5. Permission Denied / "Access is Denied"

**Symptoms:**
- java.io.IOException: Access is denied
- Failed to create directory in the Build output

**Possible Causes:**
- The project is saved in a protected system folder (like C:\Program Files)
- An Antivirus software is locking the build folder

**Solutions:**
1. Move your project folder to a user-accessible location like C:\Users\YourName\Documents\Projects
2. Add your project root folder to your Antivirus 'Exclusions' or 'Allowed' list
3. Restart IntelliJ as Administrator (Windows only)

### 6. Kotlin Version Mismatch

**Symptoms:**
- Error: The binary version of its metadata is 1.9.0, expected version is 1.8.0

**Possible Causes:**
- You have an old version of the Kotlin plugin installed in IntelliJ, but your build.gradle.kts file specifies a newer version

**Solutions:**
1. Go to File > Settings > Plugins, search for 'Kotlin' and click Update if available
2. Open your build.gradle.kts file and ensure the kotlin("jvm") version matches a version compatible with your IDE

### 7. "Could not find or load main class"

**Symptoms:**
- The console shows: Error: Could not find or load main class MainKt

**Possible Causes:**
- The build output folder is out of sync
- The package name in your Kotlin file doesn't match the folder structure

**Solutions:**
1. Open the Gradle Tool Window, go to Tasks > build, and double-click clean. Then run your application again
2. Ensure the file location matches the package declaration (e.g., package com.example must be in src/main/kotlin/com/example/)
3. Try removing the package line entirely and keep the file directly in src/main/kotlin

---

## Next Steps

🎉 Congratulations on completing this tutorial!

### Try These Next
- Make it Interactive: Modify your main function to ask for the user's name using readLine() and print a personalized greeting.
- Add Logic: Use an if-else statement or a when expression to print different messages based on the time of day or the length of the user's name.
- Create a Calculator: Write a program that takes two numbers as input and performs basic math (addition, subtraction, etc.).
- Add an External Library: Open your build.gradle.kts file and add a dependency like Clikt to learn how Gradle manages external code.

### Related Resources
- Kotlin Koans: Interactive exercises to familiarize you with Kotlin syntax (https://play.kotlinlang.org/koans/overview)
- Kotlin Official Documentation (Getting Started): Learn about the Standard Library (https://kotlinlang.org/docs/getting-started.html)
- Gradle Guides: Building Kotlin Applications: Deep dive into project structures and build tasks (https://docs.gradle.org/current/samples/sample_building_kotlin_applications.html)
- JetBrains Academy (Hyperskill): Project-based learning platform for Kotlin (https://hyperskill.org/tracks/18)

### Advanced Topics
- Null Safety: Eliminating NullPointerExceptions using nullable types and safe call operators.
- Object-Oriented Programming (OOP): Creating classes, interfaces, and data classes.
- Functional Programming: Using Lambdas and Higher-Order Functions like .map, .filter, and .fold.
- Coroutines: Kotlin's feature for asynchronous programming and concurrency.

