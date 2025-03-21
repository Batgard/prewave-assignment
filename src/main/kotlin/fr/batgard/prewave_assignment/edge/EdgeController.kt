package fr.batgard.prewave_assignment.edge

import fr.batgard.prewave_assignment.edge.model.EdgePageLink
import fr.batgard.prewave_assignment.edge.model.EdgeRequestBody
import fr.batgard.prewave_assignment.edge.model.EdgeResponse
import fr.batgard.prewave_assignment.edge.service.EdgeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/edge")
internal class EdgeController(private val edgeService: EdgeService) {


    @PostMapping
    fun createEdge(@RequestBody requestBody: EdgeRequestBody): ResponseEntity<Any> {
        edgeService.createEdge(requestBody)
        return ResponseEntity.status(HttpStatus.CREATED).body("Edge created successfully.")
    }

    @DeleteMapping
    fun deleteEdge(@RequestBody requestBody: EdgeRequestBody): ResponseEntity<String> {
        val deletedEdgeCount = edgeService.deleteEdge(requestBody)
        return ResponseEntity.ok("$deletedEdgeCount edges deleted successfully.")
    }

    /**
     * @param rootNodeId the root node ID
     * @param page the page number
     * @param pageSize the page size. Must be between 1 and 100
     */
    @GetMapping
    fun getTree(
        @RequestParam rootNodeId: Int? = null,
        @RequestParam page: Int = 1,
        @RequestParam pageSize: Int = 20,
    ): ResponseEntity<EdgeResponse> {

        require(page > 0) { "Page number must be greater than 0" }
        require(pageSize > 0 && pageSize <= 100) { "Page size must be greater than 0" }

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
