package io.lucky.orchestrator.domain

class EntityNotFoundException(
    entityName: String,
    id: Any,
) : RuntimeException("$entityName not found: $id")
