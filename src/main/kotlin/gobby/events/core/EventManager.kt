package gobby.events.core

import gobby.Gobbyclient
import gobby.events.Events
import gobby.events.render.Render3DEvent
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents

import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Contents of this file are based on ViceMod and the work of Oxyopiia under MIT License.
 * All the credits go to him.
 * @author Oxyopiia (https://github.com/Oxyopiia)
 * License: https://github.com/Oxyopiia/ViceMod/blob/master/LICENSE
 * Original source: https://github.com/Oxyopiia/ViceMod/blob/fd8b0db781c0dfd45dc958665ec8ea5d4deb88f1/src/main/java/net/oxyopia/vice/events/core/EventManager.kt
 */
class EventManager {
    data class EventListener(var event: Class<out Events?>, var target: Method, var source: Any)

    val subscribers: ConcurrentHashMap<Class<out Events>, ArrayList<EventListener>> = ConcurrentHashMap()

    /**
     * Subscribes any methods marked with @SubscribeEvent to the Event System.
     *
     * @param obj the object to be subscribed to events.
     */
    fun subscribe(obj: Any) {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            for (method in clazz.declaredMethods) {
                if (method.isAnnotationPresent(SubscribeEvent::class.java)) {
                    val parameterTypes = method.parameterTypes

                    if (parameterTypes.isNotEmpty() && Events::class.java.isAssignableFrom(parameterTypes[0])) {
                        val eventClazz = parameterTypes[0]

                        if (Events::class.java.isAssignableFrom(eventClazz)) {
                            val safeEventClazz = eventClazz.asSubclass(Events::class.java)

                            val listener = EventListener(safeEventClazz, method, obj)
                            subscribers.computeIfAbsent(safeEventClazz) { ArrayList() }.add(listener)

                            for (subClazz in eventClazz.declaredClasses) {
                                if (eventClazz.isAssignableFrom(subClazz) && Events::class.java.isAssignableFrom(subClazz)) {
                                    val safeSubClazz = subClazz.asSubclass(Events::class.java)
                                    val subListener = EventListener(safeSubClazz, method, obj)
                                    subscribers.computeIfAbsent(safeSubClazz) { ArrayList() }.add(subListener)
                                }
                            }
                        }
                    }
                }
            }
            clazz = clazz.superclass
        }
    }

    /**
     * Hooks an event to all its subscribed listeners.
     * If an exception is thrown during invocation, it is caught and printed to the Minecraft Chat using a Vice Error.
     */
    fun <T: Events> publish(event: T): T {

        var clazz: Class<*>? = event.javaClass

        while (clazz != null) {
            if (subscribers.containsKey(clazz)) {
                val listenersCopy = subscribers[clazz]?.let { ArrayList(it) } ?: return event
                for (listener in listenersCopy) {
                    try {
                        val lookup = MethodHandles.lookup()
                        val handle = lookup.unreflect(listener.target)
                        handle.invoke(listener.source, event)

                        if (event is Events.Cancelable<*> && event.isCanceled) {
                            break
                        }

                    } catch (e: Throwable) {
                        println("[GobbyClient] $e An error occurred invoking ${clazz.simpleName}")
                        e.printStackTrace()
                    }
                }
            }

            clazz = clazz.superclass
        }

        event.onceSent()
        return event
    }

    fun initEvents() {
        WorldRenderEvents.BEFORE_ENTITIES.register { context ->
            val event = Render3DEvent(context, Render3DEvent.Type.BeforeEntity)
            Gobbyclient.EVENT_MANAGER.publish(event)
        }

        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            val event = Render3DEvent(context, Render3DEvent.Type.AfterEntity)
            Gobbyclient.EVENT_MANAGER.publish(event)
        }

        // Add other hooks here (AFTER_ENTITIES, HUD, Tick, etc.)
    }

}