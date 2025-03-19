package fr.batgard.prewave_assignment.edge

import fr.batgard.prewave_assignment.db.models.tables.Edge.Companion.EDGE
import fr.batgard.prewave_assignment.db.models.tables.records.EdgeRecord
import fr.batgard.prewave_assignment.edge.model.EdgeRequestBody
import fr.batgard.prewave_assignment.edge.repository.EdgeRepository
import fr.batgard.prewave_assignment.edge.repository.exception.EdgeAlreadyExistsException
import fr.batgard.prewave_assignment.edge.repository.exception.EdgeNotFoundException
import fr.batgard.prewave_assignment.edge.repository.exception.PageIndexOutOfBoundsException
import fr.batgard.prewave_assignment.edge.service.EdgeService
import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.http.HttpStatus
import kotlin.test.assertNotEquals

class EdgeControllerTest {
    private val testDataSource = PGSimpleDataSource().apply {
        user = "prewave"
        password = "<PASSWORD>"
        databaseName = "tree_edge_db"
        portNumbers = intArrayOf(5432)
    }

    private val dslContext = DSL.using(testDataSource, SQLDialect.POSTGRES)


    @BeforeEach
    fun cleanUpDatabase() {
        dslContext.truncate(EDGE).execute()
    }

    @Test
    fun `Given edge doesn't exists When creating it Then it gets inserted in database`() {
        val edgeController = createEdgeController()

        val edgeRequestContent = EdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        assertEquals(edgeRequestContent, dslContext.fetchOne(EDGE)?.into(EdgeRequestBody::class.java))
    }

    @Test
    fun `Given edge already exists When trying to insert it again Then there's only one entry in db for it`() {
        val edgeController = createEdgeController()

        val edgeRequestContent = EdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        try {
            edgeController.createEdge(edgeRequestContent)
        } catch (ignore: EdgeAlreadyExistsException) {
        }
        assertEquals(
            1,
            dslContext.fetchCount(EDGE, EDGE.FROM_ID.eq(1).and(EDGE.TO_ID.eq(2)))
        )
    }

    @Test
    fun `Given edge already exists When trying to insert it again Then a edge already exists exception is thrown`() {
        val edgeController = createEdgeController()

        val edgeRequestContent = EdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        assertThrows<EdgeAlreadyExistsException> {
            edgeController.createEdge(edgeRequestContent)
        }
    }

    @Test
    fun `Given edge 0 does not exist, When requesting it with its subtree Then an error not found is returned`() {
        val edgeController = createEdgeController()

        insertTreeWith6EdgesInDatabase(edgeController)

        assertThrows<EdgeNotFoundException> {
            edgeController.getTree(rootNodeId = 0)
        }
    }

    @Test
    fun `Given edge 1 exists and is the root node, When requesting it with its subtree Then ok response is returned with the complete list of edges of the entire tree`() {
        val edgeController = createEdgeController()

        insertTreeWith6EdgesInDatabase(edgeController)

        val treeFromRootNodeResponse = edgeController.getTree(rootNodeId = 1)

        assertEquals(HttpStatus.OK, treeFromRootNodeResponse.statusCode)

        assertThat(treeFromRootNodeResponse.body?.edges)
            .usingComparator(listComparator)
            .isEqualTo(
                listOf(arrayOf(1, 2), arrayOf(1, 3), arrayOf(2, 4), arrayOf(2, 5), arrayOf(3, 6), arrayOf(5, 7)),
            )
    }

    @Test
    fun `Given get tree endpoint is requested without an edge ID provided, When treating the request Then root node is used`() {
        val edgeController = createEdgeController()

        insertTreeWith6EdgesInDatabase(edgeController)

        val treeFromRootNodeResponse = edgeController.getTree()

        assertEquals(HttpStatus.OK, treeFromRootNodeResponse.statusCode)
        assertThat(treeFromRootNodeResponse.body?.edges)
            .usingComparator(listComparator)
            .isEqualTo(
                listOf(arrayOf(1, 2), arrayOf(1, 3), arrayOf(2, 4), arrayOf(2, 5), arrayOf(3, 6), arrayOf(5, 7)),
            )
    }

    /**
     * Note: not sure about this behavior. It might be that we want to distinguish a leaf node from a node which we requested a wrong page from
     */
    @Test
    fun `Given a leaf node exists When requesting its subtree Then a page out of bounds exception is thrown`() {
        val edgeController = createEdgeController()

        insertTreeWith6EdgesInDatabase(edgeController)

        assertThrows<PageIndexOutOfBoundsException> {
            edgeController.getTree(rootNodeId = 7)
        }
    }

    @Test
    fun `Given edge 2 exists and is not the root node, When requesting it with its subtree Then only its edges and those from it's subtree are returned`() {
        val edgeController = createEdgeController()

        insertTreeWith6EdgesInDatabase(edgeController)

        val subtree = edgeController.getTree(rootNodeId = 2)

        assertThat(subtree.body?.edges)
            .usingComparator(listComparator)
            .isEqualTo(listOf(arrayOf(2, 4), arrayOf(2, 5), arrayOf(5, 7)))
    }

    @Test
    fun `Given db has many edges When requesting with pagination Then correct subset of edges is returned`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val firstPageResponse = edgeController.getTree(page = 1, pageSize = 3)
        assertEquals(HttpStatus.OK, firstPageResponse.statusCode)

        val secondPageResponse = edgeController.getTree(page = 2, pageSize = 3)
        assertEquals(HttpStatus.OK, secondPageResponse.statusCode)
        assertThat(secondPageResponse.body?.edges)
            .usingComparator(listComparator)
            .isEqualTo(listOf(arrayOf(2, 5), arrayOf(3, 6), arrayOf(5, 7)))
    }

    @Test
    fun `Given requested page exceeds total pages When requesting it Then edge not found exception is thrown`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        assertThrows<PageIndexOutOfBoundsException> {
            edgeController.getTree(page = 10, pageSize = 3)
        }
    }

    @Test
    fun `Given root node subtree contains 6 edges When requesting first page of size 3 Then next link is correcly set to second page with size 3`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 1, pageSize = 3) // Assuming total edges < 9
        assertEquals("http://$hostIP:$port/edge?page=2&pageSize=3", response.body?.links?.next)
    }

    @Test
    fun `Given root node subtree contains 6 edges When requesting first page of size 3 Then last link is correcly set to second page with size 3`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 1, pageSize = 3) // Assuming total edges < 9
        assertEquals("http://$hostIP:$port/edge?page=2&pageSize=3", response.body?.links?.last)
    }

    @Test
    fun `Given no more data in response When checking next link Then it is null`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 2, pageSize = 3) // Assuming total edges < 9
        assertNull(response.body?.links?.next)
    }

    @Test
    fun `Given a single page in response When checking first and last links Then they are equal`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 1, pageSize = 10) // Assuming all data fits in one page
        assertEquals(response.body?.links?.first, response.body?.links?.last)
    }

    @Test
    fun `Given first page of data is requested When checking previous link Then it is null`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 1, pageSize = 3)
        assertNull(response.body?.links?.previous)
    }

    @Test
    fun `Given second page of data is requested When checking previous link Then it is correctly set`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 2, pageSize = 3)
        assertEquals("http://$hostIP:$port/edge?page=1&pageSize=3", response.body?.links?.previous)
    }

    @Test
    fun `Given db contains 6 edges When requesting page size of 5 Then last page number is 2`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 1, pageSize = 5)
        assertEquals("http://$hostIP:$port/edge?page=2&pageSize=5", response.body?.links?.last)
    }

    @Test
    fun `Given db contains 6 edges When requesting page size of 3 Then last page number is 2`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)

        val response = edgeController.getTree(page = 1, pageSize = 3)
        assertEquals("http://$hostIP:$port/edge?page=2&pageSize=3", response.body?.links?.last)
    }

    //region edge deletion

    @Test
    fun `Given edge 1 to 5 doesn't exist, When requesting to delete it Then invalid page exception is thrown`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)
        assertThrows<EdgeNotFoundException> {
            edgeController.deleteEdge(EdgeRequestBody(1, 5))
        }
    }

    @Test
    fun `Given edge 1 to 3 exists, When requesting to delete it Then response ok is returned`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)
        assertEquals(HttpStatus.OK, edgeController.deleteEdge(EdgeRequestBody(1, 3)).statusCode)
    }

    @Test
    fun `Given edge 1 to 3 exists, When requesting to delete it Then it is removed from db`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)
        edgeController.deleteEdge(EdgeRequestBody(1, 3))
        val updatedDb = dslContext.fetch(EDGE)
        val deletedEdges = listOf(EdgeRecord(1, 3), EdgeRecord(3, 6))
        updatedDb.forEach {
            assertNotEquals(deletedEdges[0], it)
            assertNotEquals(deletedEdges[1], it)
        }
    }

    @Test
    fun `Given edge 1 to 3 existed but were then deleted, When requesting the updated tree Then it is not in the tree`() {
        val edgeController = createEdgeController()
        insertTreeWith6EdgesInDatabase(edgeController)
        edgeController.deleteEdge(EdgeRequestBody(1, 3))
        val updatedTree = edgeController.getTree()
        assertThat(updatedTree.body?.edges)
            .usingComparator(listComparator)
            .isEqualTo(
                listOf(
                    arrayOf(1, 2), arrayOf(2, 4), arrayOf(2, 5), arrayOf(5, 7)
                )
            )
    }

    private val listComparator = Comparator<List<Array<Int>>> { l1, l2 ->
        if (l1.size == l2.size && l1.zip(l2).all { pairOfArrays ->
                pairOfArrays.first.contentEquals(pairOfArrays.second)
            }) {
            0
        } else {
            -1
        }
    }

    private val hostIP = "192.168.0.42"
    private val port = "8080"
    private fun createEdgeController(): EdgeController {
        return EdgeController(
            EdgeService(
                edgeRepository = EdgeRepository(dslContext),
                hostIP = hostIP,
                port = port,
            )
        )
    }

    //endregion edge deletion

    private fun insertTreeWith6EdgesInDatabase(edgeController: EdgeController) {
        edgeController.createEdge(EdgeRequestBody(fromId = 1, toId = 2))
        edgeController.createEdge(EdgeRequestBody(fromId = 1, toId = 3))
        edgeController.createEdge(EdgeRequestBody(fromId = 2, toId = 4))
        edgeController.createEdge(EdgeRequestBody(fromId = 2, toId = 5))
        edgeController.createEdge(EdgeRequestBody(fromId = 3, toId = 6))
        edgeController.createEdge(EdgeRequestBody(fromId = 5, toId = 7))
    }


}