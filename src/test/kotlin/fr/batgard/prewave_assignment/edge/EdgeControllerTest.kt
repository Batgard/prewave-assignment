package fr.batgard.prewave_assignment.edge

import fr.batgard.prewave_assignment.db.models.tables.Edge.Companion.EDGE
import fr.batgard.prewave_assignment.db.models.tables.records.EdgeRecord
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
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
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        val edgeRequestContent = EdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        assertEquals(edgeRequestContent, dslContext.fetchOne(EDGE)?.into(EdgeRequestBody::class.java))
    }

    @Test
    fun `Given edge already exists When trying to insert it again Then there's only one entry in db for it`() {
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

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
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        val edgeRequestContent = EdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        assertThrows<EdgeAlreadyExistsException> {
            edgeController.createEdge(edgeRequestContent)
        }
    }


    @Test
    fun `Given edge 0 does not exist, When requesting it with its subtree Then an error not found is returned`() {
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        insertTreeInDatabase(edgeController)

        assertThrows<EdgeNotFoundException> {
            edgeController.getTree(rootNodeId = 0)
        }
    }

    @Test
    fun `Given edge 1 exists and is the root node, When requesting it with its subtree Then ok response is returned with the complete list of edges of the entire tree`() {
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        insertTreeInDatabase(edgeController)

        val entireTree = edgeController.getTree(rootNodeId = 1)

        assertEquals(HttpStatus.OK, entireTree.statusCode)
        assertEquals(listOf(Pair(1, 2), Pair(1, 3), Pair(2, 4), Pair(2, 5), Pair(3, 6), Pair(5, 7)), entireTree.body)
    }

    @Test
    fun `Given edge 2 exists and is not the root node, When requesting it with its subtree Then only its edges and those from it's subtree are returned`() {
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        insertTreeInDatabase(edgeController)

        val entireTree = edgeController.getTree(rootNodeId = 2)

        assertEquals(listOf(Pair(2, 4), Pair(2, 5), Pair(5, 7)), entireTree.body)
    }

    @Test
    fun `Given edge 1 to 5 doesn't exist, When requesting to delete it Then edge not found exception is thrown`() {
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))
        insertTreeInDatabase(edgeController)
        assertThrows<EdgeNotFoundException> {
            edgeController.deleteEdge(EdgeRequestBody(1, 5))
        }
    }

    @Test
    fun `Given edge 1 to 3 exists, When requesting to delete it Then response ok is returned`() {
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))
        insertTreeInDatabase(edgeController)
        assertEquals(HttpStatus.OK, edgeController.deleteEdge(EdgeRequestBody(1, 3)).statusCode)
    }

    @Test
    fun `Given edge 1 to 3 exists, When requesting to delete it Then it is removed from db as well as all its subtree`() {
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))
        insertTreeInDatabase(edgeController)
        edgeController.deleteEdge(EdgeRequestBody(1, 3))
        val updatedDb = dslContext.fetch(EDGE)
        val deletedEdges = listOf(EdgeRecord(1, 3), EdgeRecord(3, 6))
        updatedDb.forEach {
            assertNotEquals(deletedEdges[0], it)
            assertNotEquals(deletedEdges[1], it)
        }
    }

    private fun insertTreeInDatabase(edgeController: EdgeController) {
        edgeController.createEdge(EdgeRequestBody(fromId = 1, toId = 2))
        edgeController.createEdge(EdgeRequestBody(fromId = 1, toId = 3))
        edgeController.createEdge(EdgeRequestBody(fromId = 2, toId = 4))
        edgeController.createEdge(EdgeRequestBody(fromId = 2, toId = 5))
        edgeController.createEdge(EdgeRequestBody(fromId = 3, toId = 6))
        edgeController.createEdge(EdgeRequestBody(fromId = 5, toId = 7))
    }


}