package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.file.AuthenticationManager
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.h2.AuthenticationDB
import com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.h2.MetadataStorageDB
import com.simiacryptus.cognotik.platform.h2.UsageDB
import com.simiacryptus.cognotik.platform.h2.UserSettingsDB
import com.simiacryptus.cognotik.util.LazyReference
import java.io.File

open class FileApplicationServices(override val rootDir: File) : IFileApplicationServices {


  override val dataStorageFactory: StorageInterface by lazy {
    DataStorage(
      dataDir = rootDir.resolve("data"),
      metadataStorage = metadataDB
    )
  }
  override val metadataDB: MetadataStorageInterface by lazy { MetadataStorageDB() }
  override val usageDB: UsageInterface by lazy {
    UsageDB().apply {
      ChatModel.ON_USAGE =
        { model, usage, user, session, data -> this.incrementUsage(session, user, model, usage, data) }
    }
  }

  override val authenticationManager: AuthenticationInterface by lazy { AuthenticationDB() }

  override val userSettingsManager: UserSettingsInterface by lazy { UserSettingsDB() }

  /**
   * Promotional credit gifts. Exposed here so callers do not have to construct
   * [com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB] (and therefore its [DatabaseFacet]) themselves.
   */
  override val giftedCreditsDB: GiftedCreditsInterface by lazy { GiftedCreditsDB(rootDir.resolve("giftsdb")) }
}