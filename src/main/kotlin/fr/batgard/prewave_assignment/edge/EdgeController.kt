package fr.batgard.prewave_assignment.edge

import fr.batgard.prewave_assignment.db.models.tables.Edge.Companion.EDGE
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/edge")
class EdgeController(private val edgeService: EdgeService) {

    @PostMapping
    fun createEdge(@RequestBody requestBody: CreateEdgeRequestBody): ResponseEntity<Any> {
        return try {
            edgeService.createEdge(requestBody)
            ResponseEntity.status(HttpStatus.CREATED).body("Edge created successfully.")
        } catch (e: EdgeAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(e.message)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.")
        }
    }
}

data class CreateEdgeRequestBody(
    val fromId: Int,
    val toId: Int
)

@Service
class EdgeService(private val edgeRepository: EdgeRepository) {

    fun createEdge(request: CreateEdgeRequestBody) {
        edgeRepository.insertEdge(request.fromId, request.toId)
    }
}

@Repository
class EdgeRepository(private val dslContext: DSLContext) {
    fun insertEdge(fromId: Int, toId: Int) {
        try {
            dslContext.insertInto(EDGE)
                .set(EDGE.FROM_ID, fromId)
                .set(EDGE.TO_ID, toId)
                .execute()
        } catch (e: IntegrityConstraintViolationException) {
            throw EdgeAlreadyExistsException(message = "Edge fromId = $fromId, toId = $toId already exists.")
        }
    }
}

class EdgeAlreadyExistsException(message: String) : RuntimeException(message)