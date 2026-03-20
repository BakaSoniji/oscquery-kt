package dev.slimevr.oscquery

import kotlinx.serialization.encodeToString

expect fun randomFreePort(): UShort

abstract class IOSCQueryServer(
    val name: String,
    val transport: OscTransport,
    val address: String,
    var oscPort: UShort,
    val oscQueryPort: UShort = randomFreePort(),
) : AutoCloseable {
    val rootNode = OSCQueryRootNode()

    val service = OSCQueryService(name)

    var hostInfo = buildHostInfo()

    fun buildHostInfo(): HostInfo {
        return HostInfo(
            name = name,
            oscIp = address,
            oscPort = oscPort,
            oscTransport = transport,
            websocketIp = null,
            websocketPort = null,
            extensions = mapOf(Extension.VALUE.name to true)
        )
    }

    protected var oscServiceHandle: ServiceHandle? = null

    fun processPath(path: String): String {
        return format.encodeToString(rootNode.getNodeWithPath(path))
    }

    protected abstract fun initHttp(port: UShort, address: String)

    fun init() {
        initHttp(oscQueryPort, "0.0.0.0")

        // Register services — these will be queued until setPublishAddress is called
        service.createService("_oscjson._tcp.local.", name, oscQueryPort, "")
        oscServiceHandle = createOscService()
    }

    fun createOscService() : ServiceHandle {
        return service.createService("_osc._${transport.name.lowercase()}.local.", name, oscPort, "")
    }

    /**
     * Sets the address to publish mDNS services on.
     * Creates (or recreates) the publish JmDNS instance bound to the given address.
     * Any previously queued or registered services will be (re)published on this address.
     */
    fun setPublishAddress(address: IpAddress) {
        service.setPublishAddress(address)
    }

    /**
     * Sets the addresses to browse for mDNS services on.
     * Must be called before adding service listeners.
     */
    fun setBrowseAddresses(addresses: List<IpAddress>) {
        service.setBrowseAddresses(addresses)
    }

    abstract fun updateOscService(port: UShort)
}

expect class OSCQueryServer(
    name: String,
    transport: OscTransport,
    address: String,
    oscPort: UShort,
    oscQueryPort: UShort = randomFreePort(),
) : IOSCQueryServer
