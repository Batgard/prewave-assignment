package fr.batgard.prewave_assignment.edge.repository.exception

class EdgeAlreadyExistsException(message: String) : RuntimeException(message)
class EdgeNotFoundException(message: String) : RuntimeException(message)
class PageIndexOutOfBoundsException(message: String) : RuntimeException(message)
class EmptyEdgeDatabaseException: RuntimeException("Database is empty")
