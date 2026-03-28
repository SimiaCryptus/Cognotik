package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.apps.ResourceApps

@Suppress("unused") object ExperimentalStuff : CognotikPlugin {
    override fun init() {
        ResourceApps("/apps/apps.json").init()
    }
}