package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.test.MetadataStorageInterfaceTest
import java.nio.file.Files

class MetadataStorageTest : MetadataStorageInterfaceTest(MetadataStorage(Files.createTempDirectory("sessionMetadataTest").toFile()))