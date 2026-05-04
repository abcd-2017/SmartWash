package com.smartwash.network.exception

import androidx.annotation.StringRes
import java.io.IOException

class NetworkException(
    message: String,
    @StringRes val resId: Int = 0
) : IOException(message)
