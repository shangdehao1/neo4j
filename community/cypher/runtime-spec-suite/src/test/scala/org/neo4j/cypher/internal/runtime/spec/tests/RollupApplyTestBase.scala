/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.spec.tests

import java.util.Collections

import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.logical.plans.Ascending
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.graphdb.Node

import scala.collection.JavaConverters.seqAsJavaListConverter

abstract class RollupApplyTestBase[CONTEXT <: RuntimeContext](edition: Edition[CONTEXT],
                                                            runtime: CypherRuntime[CONTEXT],
                                                            val sizeHint: Int
                                                           ) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("empty lhs should produce no rows") {
    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "list")
      .rollUpApply("list", "y")
      .|.unwind("[1,2,3] AS y")
      .|.argument("x")
      .input(variables = Seq("x"))
      .build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x", "list").withNoRows()
  }

  test("empty rhs should produce empty lists") {
    val inputRows = (0 until sizeHint).map { i =>
      Array[Any](i.toLong)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "list")
      .rollUpApply("list", "y")
      .|.allNodeScan("y", "x")
      .input(variables = Seq("x"))
      .build()

    // then
    val expectedRows: Iterable[Array[_]] = (0 until sizeHint).map { i =>
      Array[Any](i.toLong, Collections.emptyList())
    }
    val runtimeResult = execute(logicalQuery, runtime, inputValues(inputRows: _*))
    runtimeResult should beColumns("x", "list").withRows(expectedRows)
  }

  test("non-empty lhs, non-empty rhs should produce lists of rhs") {
    val inputRows = (0 until sizeHint).map { i =>
      Array[Any](i.toLong)
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "list")
      .rollUpApply("list", "y")
      .|.unwind("[x, x] AS y")
      .|.argument("x")
      .input(variables = Seq("x"))
      .build()

    // then
    val expectedRows: Iterable[Array[_]] = (0L until sizeHint).map { i =>
      Array[Any](i, java.util.List.of(i, i))
    }
    val runtimeResult = execute(logicalQuery, runtime, inputValues(inputRows: _*))
    runtimeResult should beColumns("x", "list").withRows(expectedRows)
  }

  test("non-empty lhs, non-empty rhs should produce null lists for null lhs variables") {
    val size = Math.sqrt(sizeHint).toInt
    val (aNodes, bNodes) =
      given {
        bipartiteGraph(size, "A", "B", "R")
      }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "list")
      .rollUpApply("list", "y", Set("y"))
      .|.argument("y")
      .optionalExpandAll("(x)-->(y)")
      .allNodeScan("x")
      .build()

    // then
    val expectedRows: Iterable[Array[_]] =
      aNodes.flatMap(a => bNodes.map(b => Array[Any](a, Collections.singletonList(b)))) ++
      bNodes.map(b => Array[Any](b, null))
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x", "list").withRows(expectedRows)
  }

  test("non-empty lhs, non-empty rhs should produce lists preserving nulls") {
    val size = Math.sqrt(sizeHint).toInt
    val (aNodes, bNodes) =
      given {
        bipartiteGraph(size, "A", "B", "R")
      }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "list")
      .rollUpApply("list", "y")
      .|.sort(Seq(Ascending("y"))) // to get consistent order in the produced lists
      .|.optionalExpandAll("(x)-->(y)")
      .|.argument("y")
      .allNodeScan("x")
      .build()

    // then
    val expectedRows: Iterable[Array[_]] =
      aNodes.map(a => Array[Any](a, bNodes.asJava)) ++
        bNodes.map(b => Array[Any](b, Collections.singletonList(null)))
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x", "list").withRows(expectedRows)
  }

  test("should RollUpApply under apply") {
    val size = Math.sqrt(sizeHint).toInt
    val (aNodes, bNodes) =
      given {
        bipartiteGraph(size, "A", "B", "R")
      }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("i", "x", "list")
      .apply()
      .|.rollUpApply("list", "y")
      .|.|.sort(Seq(Ascending("y"))) // to get consistent order in the produced lists
      .|.|.expandAll("(x)-->(y)")
      .|.|.argument("x")
      .|.filter("id(x) % 4 = i")
      .|.allNodeScan("x")
      .unwind("[0,1,2,3] AS i")
      .argument()
      .build()

    // then
    val expectedRows: Iterable[Array[_]] =
      aNodes.map(a => Array[Any](a.getId % 4, a, bNodes.asJava)) ++
      bNodes.map(b => Array[Any](b.getId % 4, b, Collections.emptyList))
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("i", "x", "list").withRows(expectedRows)
  }

  test("should RollUpApply under limit") {
    val size = Math.sqrt(sizeHint).toInt
    val limit = size / 2
    val (aNodes, bNodes) =
      given {
        bipartiteGraph(size, "A", "B", "R")
      }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "list")
      .top(Seq(Ascending("x")), limit)
      .rollUpApply("list", "y")
      .|.sort(Seq(Ascending("y"))) // to get consistent order in the produced lists
      .|.expandAll("(x)-->(y)")
      .|.argument()
      .allNodeScan("x")
      .build()

    // then
    val expectedRows: Seq[Array[_]] =
      (
        aNodes.map(a => Array[Any](a, bNodes.asJava)) ++
        bNodes.map(b => Array[Any](b, Collections.emptyList))
      ).sortBy(arr => arr(0).asInstanceOf[Node].getId).take(limit)

    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x", "list").withRows(expectedRows)
  }

  test("should RollUpApply under join") {
    val size = Math.sqrt(sizeHint).toInt
    val (aNodes, bNodes) =
      given {
        bipartiteGraph(size, "A", "B", "R")
      }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("x", "list")
      .nodeHashJoin("x")
      .|.rollUpApply("list", "y")
      .|.|.sort(Seq(Ascending("y"))) // to get consistent order in the produced lists
      .|.|.expandAll("(x)-->(y)")
      .|.|.argument()
      .|.allNodeScan("x")
      .nodeByLabelScan("x", "A")
      .build()

    // then
    val expectedRows: Iterable[Array[_]] = aNodes.map(a => Array[Any](a, bNodes.asJava))
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("x", "list").withRows(expectedRows)
  }
}
