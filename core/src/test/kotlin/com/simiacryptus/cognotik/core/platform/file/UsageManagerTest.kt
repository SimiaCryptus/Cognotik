package com.simiacryptus.cognotik.core.platform.file

import com.simiacryptus.cognotik.core.platform.test.UsageTest
import java.nio.file.Files

class UsageManagerTest : UsageTest(UsageManager(Files.createTempDirectory("usageManager").toFile()))