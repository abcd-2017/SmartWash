package com.smartwash.network.exception

import java.io.IOException

class TimeoutException(message: String) : IOException(message)
class NetworkException(message: String) : IOException(message)
class UnauthorizedException(message: String) : IOException(message)
class ServerException(message: String) : IOException(message)
class UnknownException(message: String) : IOException(message)