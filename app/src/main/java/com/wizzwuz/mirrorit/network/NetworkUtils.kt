package com.wizzwuz.mirrorit.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    data class NetworkInterfaceInfo(
        val name: String,
        val displayName: String,
        val ipAddress: String,
        val isHotspot: Boolean
    )

    fun getLocalIpAddresses(context: Context): List<NetworkInterfaceInfo> {
        val list = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        val isAp = intf.name.startsWith("ap") ||
                                   intf.name.startsWith("softap") ||
                                   intf.name.startsWith("rndis") ||
                                   hostAddr == "192.168.43.1"
                        val displayName = when {
                            isAp -> "Mobile Hotspot (${intf.name})"
                            intf.name.startsWith("wlan") -> "Wi-Fi (${intf.name})"
                            intf.name.startsWith("eth") -> "Ethernet (${intf.name})"
                            else -> intf.displayName ?: intf.name
                        }
                        list.add(
                            NetworkInterfaceInfo(
                                name = intf.name,
                                displayName = displayName,
                                ipAddress = hostAddr,
                                isHotspot = isAp
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getPrimaryIpAddress(context: Context): String {
        val list = getLocalIpAddresses(context)
        // Prefer Wi-Fi or Hotspot
        return list.firstOrNull { it.isHotspot }?.ipAddress
            ?: list.firstOrNull { it.name.startsWith("wlan") }?.ipAddress
            ?: list.firstOrNull()?.ipAddress
            ?: "127.0.0.1"
    }

    fun getBroadcastAddress(): InetAddress {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (interfaceAddress in intf.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && broadcast is Inet4Address) {
                        return broadcast
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return InetAddress.getByName("255.255.255.255")
    }

    fun isConnectedToWifiOrHotspot(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
