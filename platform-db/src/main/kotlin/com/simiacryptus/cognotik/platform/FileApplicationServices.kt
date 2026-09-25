package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.h2.AuthenticationDB
import com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.h2.SessionMetadataDB
import com.simiacryptus.cognotik.platform.h2.UsageDB
import com.simiacryptus.cognotik.platform.h2.UserSettingsDB
import java.io.File

open class FileApplicationServices(override val rootDir: File) : IFileApplicationServices {


  override val dataStorageFactory: StorageInterface by lazy {
     IFileApplicationServices.dataStorageFactoryFn?.invoke(rootDir)
       ?: DataStorage(
         dataDir = rootDir.resolve("data"),
         metadataStorage = metadataDB
       )
  }
   override val metadataDB: SessionMetadataInterface by lazy {
     IFileApplicationServices.metadataDBFn?.invoke(rootDir) ?: SessionMetadataDB()
   }
  override val usageDB: UsageInterface by lazy {
     (IFileApplicationServices.usageDBFn?.invoke(rootDir) ?: UsageDB()).apply {
      ChatModel.ON_USAGE =
        { model, usage, user, session, data -> this.incrementUsage(session, user, model, usage, data) }
    }
  }

   override val authenticationManager: AuthenticationInterface by lazy {
     IFileApplicationServices.authenticationManagerFn?.invoke(rootDir) ?: AuthenticationDB()
   }

   override val userSettingsManager: UserSettingsInterface by lazy {
     IFileApplicationServices.userSettingsManagerFn?.invoke(rootDir) ?: UserSettingsDB()
   }

  /**
   * Promotional credit gifts. Exposed here so callers do not have to construct
   * [com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB] (and therefore its [DatabaseFacet]) themselves.
   */
   override val giftedCreditsDB: GiftedCreditsInterface by lazy {
     IFileApplicationServices.giftedCreditsDBFn?.invoke(rootDir) ?: GiftedCreditsDB(rootDir.resolve("giftsdb"))
   }
}