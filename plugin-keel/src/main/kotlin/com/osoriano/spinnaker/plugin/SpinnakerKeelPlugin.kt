package com.osoriano.spinnaker.plugin

import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import com.netflix.spinnaker.kork.plugins.api.spring.SpringLoader
import com.netflix.spinnaker.kork.plugins.proxy.LazyExtensionInvocationProxy
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.annotation.AnnotationBeanNameGenerator
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.type.filter.AssignableTypeFilter
import java.util.function.Supplier

/** Packages that will be scanned for Keel plugins and Spring beans */
private const val SPRING_SCAN_PATH = "com.osoriano.spinnaker.plugin"

/**
 * Custom PrivilegedSpringPlugin to support Keel.
 * Here are alternatives that were explored.
 *
 * com.netflix.spinnaker.kork.plugins.api.spring.SpringLoaderPlugin:
 *   This upstream plugin provided by Kork does not work with Keel since Keel
 *   expects extension beans to be defined early, but SpringLoaderPlugin does
 *   not make extensions available in the application context until
 *   SpringLoaderBeanPostProcessor.postProcessAfterInitialization
 *
 *   If one day Keel switches to use ObjectProvider (this is what Orca uses),
 *   the SpringLoaderPlugin can be used as expected since ObjectProvider can
 *   load beans dynamically at runtime.
 *
 * org.pf4j.Plugin:
 *   This upstream plugin can potentially be used, but since it is not a
 *   PrivilegedSpringPlugin, the Spring beans that can be injected are
 *   limited. See:
 *   https://github.com/spinnaker/kork/blob/master/kork-plugins/V2-COMPATIBILITY.md
 */
class SpinnakerKeelPlugin(wrapper: PluginWrapper) : PrivilegedSpringPlugin(wrapper) {

  private val logger by lazy { LoggerFactory.getLogger(javaClass) }

  private val pluginContext = AnnotationConfigApplicationContext()

  override fun registerBeanDefinitions(registry: BeanDefinitionRegistry) {
    val springLoaderBeanName = wrapper.getPluginId() + "." + SpringLoader::class.java.getName()

    val pluginClassLoader = javaClass.getClassLoader()
    val springLoaderBeanDefinition =
      BeanDefinitionBuilder.genericBeanDefinition(SpringLoader::class.java)
        .setScope(BeanDefinition.SCOPE_SINGLETON)
        .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_NO)
        .addConstructorArgValue(pluginContext)
        .addConstructorArgValue(pluginClassLoader)
        .addConstructorArgValue(getPackagesToScan())
        .addConstructorArgValue(getClassesToRegister())
        .getBeanDefinition()
    registry.registerBeanDefinition(springLoaderBeanName, springLoaderBeanDefinition)

    registerProxies(registry, springLoaderBeanName)

    // Delay keelConfigurationFinalizer until after the plugin has a chance to load its own classes
    val keelConfigFinalizer = registry.getBeanDefinition("keelConfigurationFinalizer")
    val keelConfigFinalizerDependsOnArray = keelConfigFinalizer.getDependsOn()
    var keelConfigFinalizerDependsOn: MutableList<String>
    if (keelConfigFinalizerDependsOnArray == null) {
      keelConfigFinalizerDependsOn = mutableListOf()
    } else {
      keelConfigFinalizerDependsOn = keelConfigFinalizerDependsOnArray.toMutableList()
    }
    keelConfigFinalizerDependsOn.add(springLoaderBeanName)
    keelConfigFinalizer.setDependsOn(*keelConfigFinalizerDependsOn.toTypedArray())
  }

  /**
   * Registers lazy, proxied bean definitions for a plugin's extensions.
   * This allows service-level beans to inject and use extensions like any other beans.
   * The proxied extensions are initialized via [SpringLoader] when called for the first time.
   */
  private fun registerProxies(registry: BeanDefinitionRegistry, springLoaderBeanName: String) {
    val pluginId = wrapper.descriptor.pluginId

    // Configure scanner for SpinnakerExtensionPoints
    val extensionProvider = ClassPathScanningCandidateComponentProvider(false).apply {
      addIncludeFilter(AssignableTypeFilter(SpinnakerExtensionPoint::class.java))
      resourceLoader = DefaultResourceLoader(wrapper.pluginClassLoader)
    }

    getPackagesToScan().forEach { packageToScan ->
      extensionProvider.findCandidateComponents(packageToScan).forEach { extensionBeanDefinition ->
        @Suppress("UNCHECKED_CAST")
        val extensionBeanClass = wrapper.pluginClassLoader.loadClass(extensionBeanDefinition.beanClassName) as Class<out SpinnakerExtensionPoint>

        // Find the name that the extension bean will (but hasn't yet) be given inside the plugin application context.
        // We'll use this to look up the extension inside the lazy loader.
        val pluginContextBeanName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(
          extensionBeanDefinition,
          pluginContext,
        )

        // Provide an implementation of the extension that can be injected immediately by service-level classes.
        val proxy = LazyExtensionInvocationProxy.proxy(
          lazy {
            // Force the plugin's initializer to run if it hasn't already.
            pluginContext.parent?.also { it.getBean(springLoaderBeanName) }
              ?: throw IllegalStateException("Plugin context for \"${pluginId}\" was not configured with a parent context")

            // Fetch the extension from the plugin context.
            return@lazy pluginContext.getBean(pluginContextBeanName) as SpinnakerExtensionPoint
          },
          extensionBeanClass,
          emptyList(), // InvocationAspect not supported by PrivilegedSpringPlugin
          wrapper.descriptor as SpinnakerPluginDescriptor,
        )

        val proxyBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition().beanDefinition.apply {
          instanceSupplier = Supplier { proxy }
          beanClass = extensionBeanClass
        }
        registry.registerBeanDefinition(
          "${pluginId}_$pluginContextBeanName",
          proxyBeanDefinition,
        )
      }
    }
  }

  private fun getPackagesToScan(): List<String> {
    return listOf(SPRING_SCAN_PATH)
  }

  private fun getClassesToRegister(): List<String> {
    return emptyList()
  }

  override fun start() {
    logger.info("SpinnakerKeelPlugin.start()")
  }

  override fun stop() {
    logger.info("SpinnakerKeelPlugin.start()")
  }
}
