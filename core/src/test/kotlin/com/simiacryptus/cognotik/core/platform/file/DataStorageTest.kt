package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.test.StorageInterfaceTest
import java.nio.file.Files

class DataStorageTest : StorageInterfaceTest(DataStorage(Files.createTempDirectory("sessionDataTest").toFile()))

