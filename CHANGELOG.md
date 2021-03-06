Changelog
=========

Version 0.2.0
-------------

* New WinterApplication base class for registering application component and plugins
* New Winter object as default WinterApplication
* Plugins are now registered on WinterApplication objects and not global anymore
* New Plugin interface
* New WinterInjection base class for use in libraries 
* New Injection class in core (extracted from AndroidInjection)
* New Injection#createGraph now takes an optional component builder block
* Deprecates AndroidInjection in favour of core Injection
* New ComponentCallbacks2 extension methods
* New View extension methods
* New RxJava2 module with WinterDisposablePlugin
* Fix: Make singleton, factory & multiton scopes thread safe
* Breaking: View.graph extension renamed to dependencyGraph

Version 0.1.0
-------------

* Init complete and dispose callbacks for factories
* Type aliases
* API to allow custom scopes
* Post-Construct-Plugins run also for factories with argument
* Resolver methods that allow to supply an argument for a factory
* Rewrite of the internal structure of Component and Graph entries
* Use JUnit 5 and KotlinTest matcher


Version 0.0.4 *(2018-07-13)*
----------------------------

 * Add component and parentGraph property to Graph
 * Add optional qualifier on Component (useful in Winter plugins)