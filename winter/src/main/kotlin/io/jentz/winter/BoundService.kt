package io.jentz.winter

import java.lang.ref.SoftReference
import java.lang.ref.WeakReference

interface BoundService<A, R : Any> {
    val key: TypeKey
    val scope: Scope
    fun instance(argument: A): R
    fun postConstruct(arg: Any, instance: Any)
    fun dispose()
}

internal class BoundPrototypeService<T : Any>(
        private val graph: Graph,
        private val unboundService: UnboundPrototypeService<T>
) : BoundService<Unit, T> {

    override val scope: Scope get() = Scope.Prototype

    override val key: TypeKey get() = unboundService.key

    override fun instance(argument: Unit): T {
        val instance = graph.evaluate(this, argument) { unboundService.factory(graph) }
        graph.postConstruct()
        return instance
    }

    override fun postConstruct(arg: Any, instance: Any) {
        @Suppress("UNCHECKED_CAST")
        unboundService.postConstruct?.invoke(graph, instance as T)
        WinterPlugins.runPostConstructPlugins(graph, scope, Unit, instance)
    }

    override fun dispose() {
    }

}

internal abstract class AbstractBoundSingletonService<T : Any>(
        protected val graph: Graph
) : BoundService<Unit, T> {

    protected abstract val instance: Any

    protected abstract val unboundService: UnboundService<Unit, T>

    final override val key: TypeKey get() = unboundService.key

    final override fun instance(argument: Unit): T {
        val v1 = instance
        if (v1 !== UNINITIALIZED_VALUE) {
            @Suppress("UNCHECKED_CAST")
            return instance as T
        }

        synchronized(this) {
            val v2 = instance
            if (instance !== io.jentz.winter.UNINITIALIZED_VALUE) {
                @Suppress("UNCHECKED_CAST")
                return v2 as T
            }

            synchronized(graph) {
                val typedValue = initialize()
                graph.postConstruct()
                return typedValue
            }
        }
    }

    protected abstract fun initialize(): T

}

internal class BoundSingletonService<T : Any>(
        graph: Graph,
        override val unboundService: UnboundSingletonService<T>
) : AbstractBoundSingletonService<T>(graph) {

    override val scope: Scope get() = Scope.Singleton

    override var instance: Any = UNINITIALIZED_VALUE

    override fun initialize(): T {
        val instance = graph.evaluate(this, Unit) { unboundService.factory(graph) }
        this.instance = instance
        return instance
    }

    override fun postConstruct(arg: Any, instance: Any) {
        @Suppress("UNCHECKED_CAST")
        unboundService.postConstruct?.invoke(graph, instance as T)
        WinterPlugins.runPostConstructPlugins(graph, scope, Unit, instance)
    }

    override fun dispose() {
        val instance = instance
        if (instance !== UNINITIALIZED_VALUE) {
            @Suppress("UNCHECKED_CAST")
            unboundService.dispose?.invoke(graph, instance as T)
        }
    }
}

internal class BoundWeakSingletonService<T : Any>(
        graph: Graph,
        override val unboundService: UnboundWeakSingletonService<T>
) : AbstractBoundSingletonService<T>(graph) {

    override val scope: Scope get() = Scope.WeakSingleton

    override val instance: Any get() = reference?.get() ?: UNINITIALIZED_VALUE

    private var reference: WeakReference<T>? = null

    override fun initialize(): T {
        val instance = graph.evaluate(this, Unit) { unboundService.factory(graph) }
        reference = WeakReference(instance)
        return instance
    }

    override fun postConstruct(arg: Any, instance: Any) {
        @Suppress("UNCHECKED_CAST")
        unboundService.postConstruct?.invoke(graph, instance as T)
        WinterPlugins.runPostConstructPlugins(graph, scope, Unit, instance)
    }

    override fun dispose() {
    }

}

internal class BoundSoftSingletonService<T : Any>(
        graph: Graph,
        override val unboundService: UnboundSoftSingletonService<T>
) : AbstractBoundSingletonService<T>(graph) {

    override val instance: Any get() = reference?.get() ?: UNINITIALIZED_VALUE

    override val scope: Scope get() = Scope.SoftSingleton

    private var reference: SoftReference<T>? = null

    override fun initialize(): T {
        val instance = graph.evaluate(this, Unit) { unboundService.factory(graph) }
        reference = SoftReference(instance)
        return instance
    }

    override fun postConstruct(arg: Any, instance: Any) {
        @Suppress("UNCHECKED_CAST")
        unboundService.postConstruct?.invoke(graph, instance as T)
        WinterPlugins.runPostConstructPlugins(graph, scope, Unit, instance)
    }

    override fun dispose() {
    }

}

internal class BoundFactoryService<A, R : Any>(
        private val graph: Graph,
        private val unboundService: UnboundFactoryService<A, R>
) : BoundService<A, R> {

    override val key: TypeKey get() = unboundService.key

    override val scope: Scope get() = Scope.PrototypeFactory

    override fun instance(argument: A): R {
        val instance = graph.evaluate(this, argument) { unboundService.factory(graph, argument) }
        graph.postConstruct()
        return instance
    }

    override fun postConstruct(arg: Any, instance: Any) {
        @Suppress("UNCHECKED_CAST")
        unboundService.postConstruct?.invoke(graph, arg as A, instance as R)
        WinterPlugins.runPostConstructPlugins(graph, scope, arg, instance)
    }

    override fun dispose() {
    }
}

internal class BoundMultitonFactoryService<A, R : Any>(
        private val graph: Graph,
        private val unboundService: UnboundMultitonFactoryService<A, R>
) : BoundService<A, R> {

    override val key: TypeKey get() = unboundService.key

    override val scope: Scope get() = Scope.MultitonFactory

    private val map = mutableMapOf<A, R>()

    override fun instance(argument: A): R {
        synchronized(map) {
            map[argument]?.let { return it }

            val instance = graph.evaluate(this, argument) { unboundService.factory(graph, argument) }
            map[argument] = instance
            graph.postConstruct()
            return instance
        }
    }

    override fun postConstruct(arg: Any, instance: Any) {
        @Suppress("UNCHECKED_CAST")
        unboundService.postConstruct?.invoke(graph, arg as A, instance as R)
        WinterPlugins.runPostConstructPlugins(graph, scope, arg, instance)
    }

    override fun dispose() {
        unboundService.dispose?.let { fn ->
            map.forEach { argument, instance -> fn(graph, argument, instance) }
        }
    }
}