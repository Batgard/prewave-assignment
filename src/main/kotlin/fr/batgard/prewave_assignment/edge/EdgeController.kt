package fr.batgard.prewave_assignment.edge

import com.fasterxml.jackson.annotation.JsonProperty
import fr.batgard.prewave_assignment.db.models.tables.Edge.Companion.EDGE
import org.jooq.DSLContext
import org.jooq.Name
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType.INTEGER
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*

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
     */
    @GetMapping
    fun getTree(
        @RequestParam rootNodeId: Int? = null,
        @RequestParam page: Int = 1,
        @RequestParam pageSize: Int = 20,
    ): ResponseEntity<EdgeResponse> {

        //FIXME: Add parameter validation for page and pageSize (set)

        val tree: List<Array<Int>> = if (rootNodeId != null) {
            edgeService.fetchTreeWithRoot(rootNodeId, page, pageSize)
        } else {
            edgeService.fetchTreeFromRoot(page, pageSize)
        }
        return ResponseEntity.ok(
            EdgeResponse(
                tree, EdgePageLink(
                    first = edgeService.getFirstPageLink(rootNodeId, pageSize),
                    next = edgeService.getNextPageLink(rootNodeId, page, pageSize),
                    previous = edgeService.getPreviousPageLink(rootNodeId, page, pageSize),
                    last = edgeService.getLastPageLink(rootNodeId, page, pageSize),
                )
            )
        )
    }
}

data class EdgeRequestBody(
    val fromId: Int,
    val toId: Int
)

data class EdgeResponse(
    @JsonProperty("edges")
    val edges: List<Array<Int>>,
    @JsonProperty("links")
    val links: EdgePageLink,
)

data class EdgePageLink(
    @JsonProperty("first")
    val first: String,
    @JsonProperty("last")
    val last: String,
    @JsonProperty("next")
    val next: String?,
    @JsonProperty("previous")
    val previous: String?,
)

@ControllerAdvice
class GlobalExceptionHandler {
    class ApiError(
        val status: Int? = null,
        val message: String? = null,
    )

    @ExceptionHandler(Exception::class)
    fun defaultErrorHandler(e: Exception): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity(ApiError(httpStatus.value(), "Exception : ${e::class.simpleName} \n ${e.message}"), httpStatus)
    }

    @ExceptionHandler(EdgeAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleEdgeAlreadyExistsException(e: EdgeAlreadyExistsException): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.CONFLICT
        return ResponseEntity(ApiError(httpStatus.value(), e.message), httpStatus)
    }

    @ExceptionHandler(EdgeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleEdgeNotFoundException(e: EdgeNotFoundException): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.NOT_FOUND
        return ResponseEntity(ApiError(httpStatus.value(), e.message), httpStatus)
    }

    @ExceptionHandler(PageIndexOutOfBoundsException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleInvalidPageException(e: PageIndexOutOfBoundsException): ResponseEntity<ApiError> {
        val httpStatus = HttpStatus.NOT_FOUND
        return ResponseEntity(ApiError(httpStatus.value(), e.message), httpStatus)
    }
}

@Service
class EdgeService constructor(
    private val edgeRepository: EdgeRepository,
    private val hostName: String,
    port: String?,
) {
    private val port: String = if (port != null) {
        ":$port"
    } else {
        ""
    }

    fun createEdge(request: EdgeRequestBody) {
        edgeRepository.insertEdge(request.fromId, request.toId)
    }

    fun deleteEdge(request: EdgeRequestBody) {
        if (!edgeRepository.deleteEdge(request.fromId, request.toId)) {
            throw EdgeNotFoundException(message = "No edge found with fromId = ${request.fromId} and toId = ${request.toId}.")
        }
    }

    fun fetchTreeFromRoot(
        page: Int,
        pageSize: Int,
    ): List<Array<Int>> {
        return fetchTreeWithRoot(edgeRepository.getRootNodeId(), page, pageSize)
    }

    fun fetchTreeWithRoot(
        nodeId: Int,
        page: Int,
        pageSize: Int,
    ): List<Array<Int>> {
        if (edgeRepository.contains(nodeId).not()) {
            throw EdgeNotFoundException(message = "No edge found with fromId = $nodeId.")
        }
        if (countEdgesInSubtree(nodeId) <= (page - 1) * pageSize) {
            throw PageIndexOutOfBoundsException("No data for requested page")
        }
        return edgeRepository.getTreeWithRoot(
            nodeId,
            page,
            pageSize,
        )
    }

    fun getFirstPageLink(rootNodeId: Int?, pageSize: Int) = generatePageLink(rootNodeId, FIRST_PAGE_INDEX, pageSize)

    fun getNextPageLink(rootNodeId: Int?, page: Int, pageSize: Int): String? {
        val totalEdges = countEdgesInSubtree(rootNodeId)
        return if (totalEdges <= pageSize * page) {
            null
        } else {
            generatePageLink(rootNodeId, page + 1, pageSize)
        }
    }

    fun getPreviousPageLink(rootNodeId: Int?, page: Int, pageSize: Int): String? {
        return if (page == FIRST_PAGE_INDEX) {
            null
        } else {
            generatePageLink(rootNodeId, page - 1, pageSize)
        }
    }

    fun getLastPageLink(rootNodeId: Int?, page: Int, pageSize: Int): String {
        val pageIndex = (countEdgesInSubtree(rootNodeId) / pageSize).coerceAtLeast(1)
        return generatePageLink(rootNodeId, pageIndex, pageSize)
    }

    private fun countEdgesInSubtree(rootNodeId: Int?) = if (rootNodeId == null) {
        edgeRepository.countAll()
    } else {
        edgeRepository.countEdgesForNode(rootNodeId)
    }

    private fun generatePageLink(rootNodeId: Int?, page: Int, pageSize: Int): String {
        val rootNodeIdQueryParam = if (rootNodeId != null) "&rootNodeId=$rootNodeId&" else ""
        return "http://$hostName$port/edge?${rootNodeIdQueryParam}page=$page&pageSize=$pageSize"
    }

    private companion object {
        const val FIRST_PAGE_INDEX = 1
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
        } catch (e: DataIntegrityViolationException) {
            throw EdgeAlreadyExistsException(message = "Edge fromId = $fromId, toId = $toId already exists.")
        } catch (e: IntegrityConstraintViolationException) {
            throw EdgeAlreadyExistsException(message = "Edge fromId = $fromId, toId = $toId already exists.")
            // for an unknow reason, the exception thrown by the testdatasource isn't the same as the one in production
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

    /**
     * Identifies the root node in a directed acyclic graph (DAG) represented by edges.
     *
     * The root node is determined as the node that only appears as a `FROM_ID` in the edge table
     * and does not appear as a `TO_ID`. If multiple nodes satisfy this condition, the method will
     * return the first result fetched. If no root node is found, an exception will be thrown.
     *
     * @return The ID of the root node of the graph.
     * @throws IllegalStateException If no root node is found in the graph.
     */
    fun getRootNodeId(): Int {
        return dslContext.selectDistinct(EDGE.FROM_ID)
            .from(EDGE)
            .where(
                EDGE.FROM_ID.notIn(
                    dslContext.select(EDGE.TO_ID).from(EDGE)
                )
            )
            .fetch(EDGE.FROM_ID)
            .first() ?: throw IllegalStateException("Couldn't find the root node")
    }

    fun getTreeWithRoot(nodeId: Int, page: Int, pageSize: Int): List<Array<Int>> {
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
        val offset = (page - 1) * pageSize
        /* Probably not ideal but better than filtering at a later point.
         * Some thoughts on how to solve this: For each client, save requested content and some kind of index where we left off
         */
        val fetchResult = dslContext.withRecursive(cte)
            .selectFrom(cte)
            .limit(pageSize)
            .offset(offset)
            .fetch()
            .sortedBy { it.value1() }

        return fetchResult.map { arrayOf(it.value1()!!, it.value2()!!) }
    }

    fun countAll(): Int {
        return dslContext.fetchCount(EDGE)
    }

    fun countEdgesForNode(nodeId: Int): Int {
        val cteName = "edge_subtree_count"
        val edgeSubtreeCountCte = name(cteName).fields(EDGE.FROM_ID.name, EDGE.TO_ID.name).`as`(
            select(EDGE.FROM_ID, EDGE.TO_ID).from(EDGE).where(EDGE.FROM_ID.eq(nodeId))
                .unionAll(
                    select(EDGE.FROM_ID, EDGE.TO_ID)
                        .from(table(name(cteName)))
                        .join(EDGE)
                        .on(EDGE.FROM_ID.eq(field(name(cteName, EDGE.TO_ID.name), INTEGER)))
                )
        )
        return dslContext.withRecursive(edgeSubtreeCountCte)
            .selectCount()
            .from(table(name(cteName)))
            .fetchOne(0, Int::class.java) ?: 0
    }

    fun contains(nodeId: Int): Boolean {
        return dslContext.fetchExists(
            selectOne()
                .from(EDGE)
                .where(EDGE.FROM_ID.eq(nodeId).or(EDGE.TO_ID.eq(nodeId)))
        )
    }

}

class EdgeAlreadyExistsException(message: String) : RuntimeException(message)
class EdgeNotFoundException(message: String) : RuntimeException(message)
class PageIndexOutOfBoundsException(message: String) : RuntimeException(message)