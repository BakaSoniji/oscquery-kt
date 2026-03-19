package dev.slimevr.oscquery

import org.junit.Test

class OSCQueryServerTest {
    @Test
    fun testFetchHostInfo() {
        val server = OSCQueryServer(
            name = "SlimeVR-Test",
            transport = OscTransport.UDP,
            address = "192.168.1.100",
            oscPort = 9001u,
        )
        server.init()

        val hostInfo = fetchHostInfo("127.0.0.1", server.oscQueryPort.toInt())

        assert(hostInfo.name == "SlimeVR-Test")
        assert(hostInfo.oscPort?.toInt() == 9001)
        assert(hostInfo.oscIp == "192.168.1.100")
        assert(hostInfo.oscTransport == OscTransport.UDP)

        server.close()
    }
}
