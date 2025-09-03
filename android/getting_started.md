# Android Development Setup Guide for Linux

This guide will walk you through setting up an Android development environment on Linux, creating an emulator, and building/testing the Cognotik Android application.

## Prerequisites

- Linux distribution (Ubuntu/Debian/Fedora/etc.)
- At least 8GB RAM (16GB recommended)
- 20GB+ free disk space
- Hardware virtualization support (Intel VT-x or AMD-V)

## Step 1: Install Java Development Kit (JDK)

The project requires Java 17. Install it using your package manager:

### Ubuntu/Debian:
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

### Fedora:
```bash
sudo dnf install java-17-openjdk-devel
```

### Arch Linux:
```bash
sudo pacman -S jdk17-openjdk
```

Verify installation:
```bash
java -version
javac -version
```

## Step 2: Install Android Studio

### Option A: Using Snap (Recommended)
```bash
sudo snap install android-studio --classic
```

### Option B: Manual Installation
1. Download Android Studio from [developer.android.com](https://developer.android.com/studio)
2. Extract the archive:
```bash
tar -xzf android-studio-*.tar.gz
sudo mv android-studio /opt/
```
3. Add to PATH in `~/.bashrc` or `~/.zshrc`:
```bash
export PATH="/opt/android-studio/bin:$PATH"
```
4. Launch Android Studio:
```bash
studio.sh
```

## Step 3: Configure Android Studio and SDK

1. **Launch Android Studio** and complete the setup wizard
2. **Install SDK components**:
    - Go to `File > Settings > Appearance & Behavior > System Settings > Android SDK`
    - Install the following:
        - Android SDK Platform 35 (API level 35)
        - Android SDK Platform 34 (API level 34)
        - Android SDK Build-Tools (latest version)
        - Android Emulator
        - Android SDK Platform-Tools
        - Intel x86 Emulator Accelerator (HAXM) or KVM

3. **Set up environment variables** in `~/.bashrc` or `~/.zshrc`:
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
```

4. **Reload your shell**:
```bash
source ~/.bashrc  # or ~/.zshrc
```

## Step 4: Enable Hardware Acceleration

### For Intel processors (HAXM):
```bash
# Check if virtualization is enabled
egrep -c '(vmx|svm)' /proc/cpuinfo
# Should return a number > 0

# Install KVM (alternative to HAXM on Linux)
sudo apt install qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils
sudo usermod -aG kvm $USER
sudo usermod -aG libvirt $USER
```

### For AMD processors:
```bash
# Install KVM
sudo apt install qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils
sudo usermod -aG kvm $USER
sudo usermod -aG libvirt $USER
```

**Log out and log back in** for group changes to take effect.

## Step 5: Create Android Virtual Device (AVD)

1. **Open AVD Manager** in Android Studio:
    - `Tools > AVD Manager`

2. **Create a new AVD**:
    - Click "Create Virtual Device"
    - Select a device (e.g., "Pixel 7" or "Pixel 4")
    - Choose a system image:
        - API Level 35 (Android 15) - matches `compileSdk`
        - Or API Level 34 (Android 14)
        - Select x86_64 for better performance
    - Configure AVD settings:
        - RAM: 4GB minimum
        - Internal Storage: 8GB minimum
        - Enable hardware acceleration

3. **Start the emulator**:
    - Click the play button next to your AVD
    - Wait for the emulator to boot completely

## Step 6: Clone and Build the Project

1. **Clone the repository**:
```bash
git clone https://github.com/SimiaCryptus/Cognotik.git
cd Cognotik
```

2. **Create local.properties file**:
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

3. **Make gradlew executable**:
```bash
chmod +x gradlew
```

4. **Build the project**:
```bash
./gradlew :android:assembleDebug
```

This will:
- Download all dependencies
- Compile the Android application
- Create an APK file in `android/build/outputs/apk/debug/`

## Step 7: Install and Test the App

### Option A: Using Android Studio
1. Open the project in Android Studio
2. Select your emulator from the device dropdown
3. Click the "Run" button (green triangle)

### Option B: Using Command Line
1. **Ensure emulator is running**:
```bash
adb devices
# Should show your emulator listed
```

2. **Install the APK**:
```bash
adb install android/build/outputs/apk/debug/android-debug.apk
```

3. **Launch the app**:
```bash
adb shell am start -n com.simiacryptus.cognotik.android/.MainActivity
```

## Step 8: Testing and Debugging

### View logs:
```bash
adb logcat | grep "Cognotik"
```

### Clear app data (if needed):
```bash
adb shell pm clear com.simiacryptus.cognotik.android
```

### Uninstall app:
```bash
adb uninstall com.simiacryptus.cognotik.android
```

### Rebuild and reinstall:
```bash
./gradlew :android:assembleDebug
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

## Troubleshooting

### Common Issues:

1. **"SDK location not found"**:
    - Ensure `local.properties` exists with correct `sdk.dir`
    - Verify `ANDROID_HOME` environment variable

2. **Emulator won't start**:
    - Check hardware virtualization is enabled in BIOS
    - Ensure KVM is properly installed and user is in kvm group
    - Try creating a new AVD with different settings

3. **Build fails with dependency issues**:
    - Clean and rebuild: `./gradlew clean :android:assembleDebug`
    - Check internet connection for dependency downloads

4. **App crashes on startup**:
    - Check logcat for error messages
    - Ensure minimum SDK version (26) is met by your AVD

5. **Performance issues**:
    - Allocate more RAM to emulator
    - Use x86_64 system images instead of ARM
    - Enable hardware acceleration

### Useful Commands:

```bash
# List all AVDs
emulator -list-avds

# Start specific emulator
emulator -avd <avd-name>

# Check connected devices
adb devices

# View detailed logs
adb logcat -v time

# Monitor system resources
adb shell top
```

## Next Steps

Once the app is running successfully:
- Test all features and UI components
- Monitor performance and memory usage
- Test on different screen sizes and orientations
- Consider testing on physical devices for real-world performance

The app should now be running on your Android emulator, ready for testing and development!
