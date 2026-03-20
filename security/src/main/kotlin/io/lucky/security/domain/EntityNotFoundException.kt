package io.lucky.security.domain

class EntityNotFoundException(
    entityName: String,
    id: Any,
) : RuntimeException("$entityName not found: $id")
