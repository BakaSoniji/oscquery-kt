package dev.slimevr.oscquery

import java.net.HttpURLConnection
import java.net.URI

/**
 * Fetches HOST_INFO from a remote OSCQuery server.
 *
 * @param ip The IP address of the remote server
 * @param port The HTTP port of the remote OSCQuery server (from _oscjson._tcp mDNS)
 * @return The parsed HostInfo response
 * @throws Exception on network or parsing errors
 */
fun fetchHostInfo(ip: String, port: Int): HostInfo {
    val url = URI("http://$ip:$port/?HOST_INFO").toURL()
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val body = connection.inputStream.bufferedReader().readText()
        return format.decodeFromString<HostInfo>(body)
    } finally {
        connection.disconnect()
    }
}
