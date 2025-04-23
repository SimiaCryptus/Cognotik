package com.simiacryptus.cognotik.core.platform.hsql

import com.simiacryptus.cognotik.core.platform.test.UsageTest
import java.nio.file.Files

class HSQLUsageManagerTest : UsageTest(HSQLUsageManager(Files.createTempDirectory("usageManager").toFile()))
