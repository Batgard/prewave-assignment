package fr.batgard.prewave_assignment.edge.repository

import fr.batgard.prewave_assignment.db.models.tables.Edge.Companion.EDGE
import fr.batgard.prewave_assignment.edge.repository.exception.EdgeAlreadyExistsException
import org.jooq.DSLContext
import org.jooq.Name
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType.INTEGER
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
internal class EdgeRepository(private val dslContext: DSLContext) {
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
