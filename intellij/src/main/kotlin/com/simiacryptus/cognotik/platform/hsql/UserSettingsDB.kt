package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import java.io.File

/**
 * Shadow class to allow UserSettingsDB to be used in IntelliJ plugin environment (concurrency-safe file-based storage)
 * */
@Suppress("unused") class UserSettingsDB :
    UserSettingsManager(File(System.getProperty("user.home", ".")).resolve(".cognotik"))