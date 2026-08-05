package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.PluginEvents
import com.simiacryptus.cognotik.platform.model.Topic

/**
 * Publish/subscribe event router.
 *
 * Split out of `PluginManagerInterface` so that consumers which only need events
 * are not exposed to plugin lifecycle operations such as `deletePlugin`
 * (REVIEW.md §3.8).
 */
interface EventBus {

  /**
   * Publish an event to all subscribers of the given topic.
   *
   * @param topic the event topic/channel name
   * @param data the event payload
   */
  fun publish(topic: String, data: Any?)

  /** Typed variant of [publish]. */
  fun <T : Any> publish(topic: Topic<T>, data: T?) = publish(topic.name, data)

  /**
   * Subscribe to events on a given topic.
   *
   * @param topic the event topic/channel name
   * @param handler callback invoked with the event payload when an event is published
   * @return a subscription ID that can be used to unsubscribe
   */
  fun subscribe(topic: String, handler: (Any?) -> Unit): String

  /**
   * Typed variant of [subscribe]: payloads that do not match [Topic.payloadType]
   * are delivered as null rather than causing a ClassCastException inside a plugin.
   */
  fun <T : Any> subscribe(topic: Topic<T>, handler: (T?) -> Unit): String =
    subscribe(topic.name) { raw -> handler(topic.cast(raw)) }

  /**
   * Unsubscribe a previously registered event handler.
   *
   * @param subscriptionId the subscription ID returned by [subscribe] or [onChange]
   */
  fun unsubscribe(subscriptionId: String)

  /**
   * Register a change listener, returning a handle for [unsubscribe].
   *
   * @return a subscription ID
   */
  fun onChange(subscriber: () -> Unit): String =
    subscribe(PluginEvents.CHANGE_NOTIFICATION) { subscriber() }

  /** Notify all change listeners. */
  fun triggerChangeNotification()
}