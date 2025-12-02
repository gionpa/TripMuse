package com.tripmuse.exception

open class TripMuseException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : TripMuseException(message)

class ForbiddenException(message: String) : TripMuseException(message)

class BadRequestException(message: String) : TripMuseException(message)

class StorageException(message: String) : TripMuseException(message)
