package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.User

fun ChatModel.instance(user: User) = ApiChatModel(
  model = this,
  provider = ApplicationServicesImpl.fileApplicationServices().userSettingsManager
    .getUserSettings(user).apis.find { it.provider == this.provider })