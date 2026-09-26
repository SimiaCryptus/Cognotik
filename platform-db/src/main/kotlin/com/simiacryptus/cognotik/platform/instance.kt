package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.User

fun ChatModel.instance(user: User) = ApiChatModel(
  model = this,
  provider = (ServiceMap
    ?: throw IllegalStateException("ApplicationServices not initialized"))[ServiceKey.USER_SETTINGS]
    .getUserSettings(user).apis.find { it.provider == this.provider })