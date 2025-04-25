package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.test.UsageTest
import java.nio.file.Files

class UsageManagerTest : UsageTest(UsageManager(Files.createTempDirectory("usageManager").toFile()))