package com.simiacryptus.cognotik.platform

/**
 * Facade retained for compatibility: it is now simply the composition of the three
 * responsibilities it used to conflate.
 *
 * New consumers should depend on the narrowest port they need:
 * - [EventBus] for publish/subscribe
 * - [PluginRegistry] for plugin lifecycle
 * - [PluginInstaller] for artifact installation/removal
 *
 * Not yet specified (tracked in REVIEW.md §3.8): API version compatibility,
 * load ordering, inter-plugin dependencies, and classloader isolation guarantees.
 */
interface PluginManagerInterface : EventBus, PluginRegistry, PluginInstaller