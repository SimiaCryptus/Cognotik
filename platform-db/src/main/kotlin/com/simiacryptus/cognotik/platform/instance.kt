package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.service.UserSettingsInterface

fun ChatModel.instance(user: User) = ApiChatModel(
  model = this,
  provider = ServiceRouter
    .getUserSettings(user).apis.find { it.provider == this.provider })