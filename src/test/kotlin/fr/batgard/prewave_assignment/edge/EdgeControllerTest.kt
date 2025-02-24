package fr.batgard.prewave_assignment.edge

import fr.batgard.prewave_assignment.db.models.tables.Edge.Companion.EDGE
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.http.HttpStatus

class EdgeControllerTest {
    private val testDataSource = PGSimpleDataSource().apply {
        user = "prewave"
        password = "<PASSWORD>"
        databaseName = "tree_edge_db"
        portNumbers = intArrayOf(5432)
    }

    @Test
    fun `Given edge doesn't exists When creating it Then it gets inserted in database`() {
        val dslContext = DSL.using(testDataSource, SQLDialect.POSTGRES)
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        val edgeRequestContent = CreateEdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        assertEquals(edgeRequestContent, dslContext.fetchOne(EDGE)?.into(CreateEdgeRequestBody::class.java))
    }

    @Test
    fun `Given edge already exists When trying to insert it again Then there's only one entry in db for it`() {
        val dslContext = DSL.using(testDataSource, SQLDialect.POSTGRES)
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        val edgeRequestContent = CreateEdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        edgeController.createEdge(edgeRequestContent)
        assertEquals(
            1,
            dslContext.fetchCount(EDGE, EDGE.FROM_ID.eq(1).and(EDGE.TO_ID.eq(2)))
        )
    }

    @Test
    fun `Given edge already exists When trying to insert it again Then conflict is returned`() {
        val dslContext = DSL.using(testDataSource, SQLDialect.POSTGRES)
        val edgeController = EdgeController(EdgeService(EdgeRepository(dslContext)))

        val edgeRequestContent = CreateEdgeRequestBody(fromId = 1, toId = 2)
        edgeController.createEdge(edgeRequestContent)
        val responseEntity = edgeController.createEdge(edgeRequestContent)
        assertEquals(HttpStatus.CONFLICT, responseEntity.statusCode)
//        println(dslContext.selectFrom(EDGE).fetch())
    }
}