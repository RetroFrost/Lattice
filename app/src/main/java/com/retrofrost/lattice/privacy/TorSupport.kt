package com.retrofrost.lattice.privacy

import android.content.Context

object TorSupport {
    const val ORBOT_PACKAGE = "org.torproject.android"
    const val DEFAULT_SOCKS_HOST = "127.0.0.1"
    const val DEFAULT_SOCKS_PORT = 9050

    fun isOrbotInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
    }.isSuccess
}
