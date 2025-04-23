package com.simiacryptus.cognotik.core.platform.file

import com.simiacryptus.cognotik.core.platform.test.StorageInterfaceTest
import java.nio.file.Files

class DataStorageTest : StorageInterfaceTest(DataStorage(Files.createTempDirectory("sessionDataTest").toFile()))

