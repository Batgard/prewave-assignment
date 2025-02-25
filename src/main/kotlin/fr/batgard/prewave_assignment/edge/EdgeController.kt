package fr.batgard.prewave_assignment.edge

import fr.batgard.prewave_assignment.db.models.tables.Edge.Companion.EDGE
import jakarta.servlet.http.HttpServletRequest
import org.jooq.DSLContext
import org.jooq.Name
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType.INTEGER
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView

@RestController
@RequestMapping("/edge")
class EdgeController(private val edgeService: EdgeService) {

    @PostMapping
    fun createEdge(@RequestBody requestBody: EdgeRequestBody): ResponseEntity<Any> {
        edgeService.createEdge(requestBody)
        return ResponseEntity.status(HttpStatus.CREATED).body("Edge created successfully.")
    }

    @DeleteMapping
    fun deleteEdge(@RequestBody requestBody: EdgeRequestBody): ResponseEntity<String> {
        edgeService.deleteEdge(requestBody)
        return ResponseEntity.ok("Edge deleted successfully.")
    }

    /**
     * @param rootNodeId the root node ID
     * FIXME: this needs to be paginated
     */
    @GetMapping
    fun getTree(rootNodeId: Int): ResponseEntity<List<Pair<Int, Int>>> {
        return ResponseEntity.ok(edgeService.fetchTreeWithRoot(rootNodeId))
    }
}

@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(Exception::class)
    fun defaultErrorHandler(req: HttpServletRequest, e: Exception): ModelAndView {
        if (AnnotationUtils.findAnnotation(e.javaClass, ResponseStatus::class.java) != null) {
            throw e
        }

        return ModelAndView("error").apply {
            addObject("exception", e)
            addObject("url", req.requestURL)
        }
    }

    @ExceptionHandler(EdgeAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleEdgeAlreadyExistsException(e: EdgeAlreadyExistsException) {
    }

    @ExceptionHandler(EdgeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleEdgeNotFoundException(e: EdgeAlreadyExistsException) {
    }
}

data class EdgeRequestBody(
    val fromId: Int,
    val toId: Int
)

@Service
class EdgeService(private val edgeRepository: EdgeRepository) {
    fun createEdge(request: EdgeRequestBody) {
        edgeRepository.insertEdge(request.fromId, request.toId)
    }

    fun deleteEdge(request: EdgeRequestBody) {
        if(!edgeRepository.deleteEdge(request.fromId, request.toId)) {
            throw EdgeNotFoundException(message = "No edge found with fromId = ${request.fromId} and toId = ${request.toId}.")
        }
    }

    fun fetchTreeWithRoot(nodeId: Int): List<Pair<Int, Int>> = edgeRepository.getTreeWithRoot(nodeId)
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

    /**
     * Given the fact that for this exercise, the connection between companies is represented by a tree, it is assumed that
     * a company ID can't appear twice in the tree. That beeing said, if a node is removed following the deletion of an edge,
     * the subtree of the node will be deleted as well unless specified. Reasons for not deleting the entire subtree are
     * that in real-life cases, the conections between all the downstream companies might still exist even after the deletion of
     * the upstream company.
     * @return whether or not a row was deleted
     */
    fun deleteEdge(fromId: Int, toId: Int): Boolean {
        val cteName = "edges_to_delete"
        val edgesToDeleteCteName: Name = name(cteName)

        val edgesToDeleteCommonTableExpression = edgesToDeleteCteName
            .fields(EDGE.FROM_ID.name, EDGE.TO_ID.name).`as`(
                // Base case: the edge to delete
                select(EDGE.FROM_ID, EDGE.TO_ID)
                    .from(EDGE)
                    .where(EDGE.FROM_ID.eq(fromId).and(EDGE.TO_ID.eq(toId)))
                    .unionAll(
                        // Recursive case: find child edges
                        select(EDGE.FROM_ID, EDGE.TO_ID)
                            .from(EDGE)
                            .join(edgesToDeleteCteName)
                            .on(
                                EDGE.FROM_ID.eq(
                                    field(name(cteName, EDGE.TO_ID.name), INTEGER)
                                )
                            )
                    )
            )
        // Execute the DELETE query
        val result = dslContext.withRecursive(edgesToDeleteCommonTableExpression)
            .delete(EDGE)
            .where(
                row(EDGE.FROM_ID, EDGE.TO_ID).`in`(
                    select(EDGE.FROM_ID, EDGE.TO_ID).from(edgesToDeleteCteName)
                )
            ).execute()
        return result > 0
    }

    fun getTreeWithRoot(nodeId: Int): List<Pair<Int, Int>> {
        val cteName = "tree"
        val cte = name(cteName).fields(EDGE.FROM_ID.name, EDGE.TO_ID.name).`as`(
            select(EDGE.FROM_ID, EDGE.TO_ID).from(EDGE).where(EDGE.FROM_ID.eq(nodeId))
                .unionAll(
                    select(EDGE.FROM_ID, EDGE.TO_ID)
                        .from(table(name(cteName)))
                        .join(EDGE)
                        .on(EDGE.FROM_ID.eq(field(name(cteName, EDGE.TO_ID.name), INTEGER)))
                )
        )
        val fetchResult = dslContext.withRecursive(cte).selectFrom(cte).fetch()
        if (fetchResult.isEmpty()) {
            throw EdgeNotFoundException(message = "No edge found with fromId = $nodeId.")
        }
        return fetchResult.map { it.value1()!! to it.value2()!! }
    }

}

class EdgeAlreadyExistsException(message: String) : RuntimeException(message)
class EdgeNotFoundException(message: String) : RuntimeException(message)