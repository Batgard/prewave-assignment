package fr.batgard.prewave_assignment.edge.service

import fr.batgard.prewave_assignment.edge.model.EdgeRequestBody
import fr.batgard.prewave_assignment.edge.repository.EdgeRepository
import fr.batgard.prewave_assignment.edge.repository.exception.EdgeNotFoundException
import fr.batgard.prewave_assignment.edge.repository.exception.PageIndexOutOfBoundsException
import org.springframework.stereotype.Service

@Service
internal class EdgeService(
    private val edgeRepository: EdgeRepository,
    private val hostIP: String,
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

    /**
     * @return the number of deleted edges.
     * @throws EdgeNotFoundException if edge could not be found
     */
    fun deleteEdge(request: EdgeRequestBody): Int {
        val deleteEdgeCount = edgeRepository.deleteEdge(request.fromId, request.toId)
        if (deleteEdgeCount == 0) {
            throw EdgeNotFoundException(message = "No edge found with fromId = ${request.fromId} and toId = ${request.toId}.")
        } else {
            return deleteEdgeCount
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
        val edgeCount = countEdgesInSubtree(rootNodeId)
        val fullPageCount = edgeCount / pageSize
        val partiallyFullPageCount = if (edgeCount % pageSize == 0) 0 else 1
        val pageIndex = (fullPageCount + partiallyFullPageCount).coerceAtLeast(1)
        return generatePageLink(rootNodeId, pageIndex, pageSize)
    }

    private fun countEdgesInSubtree(rootNodeId: Int?) = if (rootNodeId == null) {
        edgeRepository.countAll()
    } else {
        edgeRepository.countEdgesForNode(rootNodeId)
    }

    private fun generatePageLink(rootNodeId: Int?, page: Int, pageSize: Int): String {
        val rootNodeIdQueryParam = if (rootNodeId != null) "&rootNodeId=$rootNodeId&" else ""
        return "http://$hostIP$port/edge?${rootNodeIdQueryParam}page=$page&pageSize=$pageSize"
    }

    private companion object {
        const val FIRST_PAGE_INDEX = 1
    }
}