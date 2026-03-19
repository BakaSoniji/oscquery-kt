package dev.slimevr.oscquery

import kotlinx.coroutines.channels.Channel
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.jmdns.ServiceInfo as JmDNSServiceInfo

actual class OSCQueryService actual constructor(private val name: String) : AutoCloseable {

    // Browse instances: one per interface for receiving multicast on all networks.
    // Initialized with a single wildcard instance, replaced when setBrowseAddresses is called.
    private var browseInstances: List<JmDNS> = listOf(JmDNS.create(null, name))

    // Publish instance: created lazily when setPublishAddress is called, bound to a specific interface
    private var publishJmDNS: JmDNS? = null

    // Pending services to register when publishJmDNS becomes available
    private val pendingServices = mutableListOf<JmDNSServiceInfo>()

    private val serviceCounter = AtomicLong(0)
    private val serviceHandles = mutableMapOf<Long, JmDNSServiceInfo>()

    actual fun createService(serviceName: String, name: String, port: UShort, text: String): ServiceHandle {
        val service = JmDNSServiceInfo.create(serviceName, name, port.toInt(), "help")
        val handle = serviceCounter.getAndIncrement()
        serviceHandles[handle] = service

        val publisher = publishJmDNS
        if (publisher != null) {
            publisher.registerService(service)
        } else {
            pendingServices.add(service)
        }

        return ServiceHandle(handle)
    }

    actual fun removeService(handle: ServiceHandle) {
        val service = serviceHandles.remove(handle.id) ?: return
        pendingServices.remove(service)
        publishJmDNS?.unregisterService(service)
    }

    actual fun setPublishAddress(address: InetAddress) {
        // Close existing publisher if it's on a different address
        publishJmDNS?.let { existing ->
            if (existing.inetAddress == address) return // Already bound to this address
            existing.close()
        }

        val publisher = JmDNS.create(address, name)
        publishJmDNS = publisher
        pendingServices.clear()

        // Register all known services on the new publisher
        for (service in serviceHandles.values) {
            publisher.registerService(service.clone())
        }
    }

    /**
     * Sets the addresses to browse for mDNS services on.
     * Creates one JmDNS instance per address. On Windows, each instance binds
     * multicast to its specific address, so multiple instances are needed
     * to discover services across different network interfaces.
     *
     * Must be called before addServiceListener for the listeners to be
     * registered on the new instances.
     */
    actual fun setBrowseAddresses(addresses: List<InetAddress>) {
        // Close existing browse instances
        for (instance in browseInstances) {
            instance.close()
        }

        browseInstances = addresses.map { addr ->
            JmDNS.create(addr, name)
        }
    }

    private val listenerCounter = AtomicLong(0)
    private val serviceListeners = mutableMapOf<Long, Pair<String, ServiceListener>>()

    actual fun addServiceListener(
        serviceName: String,
        onServiceResolved: (ServiceInfo) -> Unit,
        onServiceAdded: (ServiceInfo) -> Unit,
        onServiceRemoved: (type: String, name: String) -> Unit,
    ): ServiceListenerHandle {
        // Track seen services to deduplicate across browse instances
        val seenServices = mutableSetOf<String>()

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent?) {
                val evt = event ?: return
                val dns = evt.dns
                val info = dns.getServiceInfo(evt.type, evt.name) ?: return
                val key = "${info.name}:${info.port}"
                synchronized(seenServices) {
                    if (!seenServices.add(key)) return
                }
                onServiceAdded(ServiceInfo(info))
            }

            override fun serviceRemoved(event: ServiceEvent?) {
                val evt = event ?: return
                synchronized(seenServices) {
                    // Remove so re-discovery works
                    seenServices.removeIf { it.startsWith("${evt.name}:") }
                }
                onServiceRemoved(evt.type, evt.name)
            }

            override fun serviceResolved(event: ServiceEvent?) {
                onServiceResolved(ServiceInfo(event?.info ?: return))
            }
        }

        // Register listener on all browse instances
        for (instance in browseInstances) {
            instance.addServiceListener(serviceName, listener)
        }

        val handle = listenerCounter.getAndIncrement()
        serviceListeners[handle] = serviceName to listener
        return ServiceListenerHandle(handle)
    }

    actual fun removeServiceListener(handle: ServiceListenerHandle): Boolean {
        val (serviceName, listener) = serviceListeners.remove(handle.id) ?: return false
        for (instance in browseInstances) {
            instance.removeServiceListener(serviceName, listener)
        }
        return true
    }

    override fun close() {
        publishJmDNS?.close()
        for (instance in browseInstances) {
            instance.close()
        }
    }
}

actual typealias IpAddress = InetAddress

actual class ServiceInfo(private val serviceInfo: JmDNSServiceInfo) {
    actual val inetAddresses: Array<IpAddress>
        get() = serviceInfo.inetAddresses
    actual val port: Int
        get() = serviceInfo.port
    actual val name: String
        get() = serviceInfo.name
}
