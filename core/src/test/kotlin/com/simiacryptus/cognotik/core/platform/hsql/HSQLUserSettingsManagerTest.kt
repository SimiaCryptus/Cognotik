package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.test.UsageTest
import java.nio.file.Files

class HSQLUsageManagerTest : UsageTest(HSQLUsageManager(Files.createTempDirectory("usageManager").toFile()))
