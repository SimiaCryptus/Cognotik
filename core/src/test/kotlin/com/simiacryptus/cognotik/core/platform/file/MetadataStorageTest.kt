package com.simiacryptus.cognotik.core.platform.file

import com.simiacryptus.cognotik.core.platform.test.MetadataStorageInterfaceTest
import java.nio.file.Files

class MetadataStorageTest : MetadataStorageInterfaceTest(MetadataStorage(Files.createTempDirectory("sessionMetadataTest").toFile()))