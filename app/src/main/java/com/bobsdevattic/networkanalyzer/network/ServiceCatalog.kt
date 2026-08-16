package com.bobsdevattic.networkanalyzer.network

/**
 * Well-known TCP port → service name. Used to label open ports in the scan
 * results. Not exhaustive; unknown ports are left unlabeled.
 */
object ServiceCatalog {

    fun serviceFor(port: Int): String? = PORTS[port]

    /** A compact set of commonly interesting ports for the "Common" scan profile. */
    val COMMON_PORTS: List<Int> = listOf(
        20, 21, 22, 23, 25, 53, 67, 80, 110, 111, 123, 135, 139, 143, 161,
        389, 443, 445, 465, 500, 514, 515, 548, 587, 631, 993, 995,
        1080, 1433, 1521, 1723, 1883, 1900, 2049, 3306, 3389, 5000, 5060,
        5353, 5432, 5555, 5900, 6379, 7000, 8000, 8080, 8443, 8883, 9000,
        9100, 27017, 32400, 62078,
    )

    private val PORTS: Map<Int, String> = mapOf(
        20 to "ftp-data",
        21 to "ftp",
        22 to "ssh",
        23 to "telnet",
        25 to "smtp",
        53 to "dns",
        67 to "dhcp",
        68 to "dhcp",
        80 to "http",
        110 to "pop3",
        111 to "rpcbind",
        123 to "ntp",
        135 to "msrpc",
        137 to "netbios-ns",
        139 to "netbios-ssn",
        143 to "imap",
        161 to "snmp",
        389 to "ldap",
        443 to "https",
        445 to "smb",
        465 to "smtps",
        500 to "isakmp",
        514 to "syslog",
        515 to "printer",
        548 to "afp",
        587 to "submission",
        631 to "ipp",
        993 to "imaps",
        995 to "pop3s",
        1080 to "socks",
        1433 to "ms-sql",
        1521 to "oracle",
        1723 to "pptp",
        1883 to "mqtt",
        1900 to "ssdp/upnp",
        2049 to "nfs",
        3306 to "mysql",
        3389 to "rdp",
        5000 to "upnp/http",
        5060 to "sip",
        5353 to "mdns",
        5432 to "postgresql",
        5555 to "adb",
        5900 to "vnc",
        6379 to "redis",
        7000 to "afs/http",
        8000 to "http-alt",
        8080 to "http-proxy",
        8443 to "https-alt",
        8883 to "mqtts",
        9000 to "http-alt",
        9100 to "jetdirect",
        27017 to "mongodb",
        32400 to "plex",
        62078 to "iphone-sync",
    )
}
