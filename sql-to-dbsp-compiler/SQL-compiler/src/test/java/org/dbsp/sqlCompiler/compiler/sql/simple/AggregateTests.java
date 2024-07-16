package org.dbsp.sqlCompiler.compiler.sql.simple;

import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.sql.tools.SqlIoTest;
import org.junit.Test;

public class AggregateTests extends SqlIoTest {
    @Override
    public void prepareInputs(DBSPCompiler compiler) {
        compiler.compileStatements("""
                CREATE TABLE T(
                   B BIGINT,
                   I INTEGER,
                   S SMALLINT,
                   T TINYINT,
                   R REAL,
                   D DOUBLE,
                   E DECIMAL(3,2),
                   V VARCHAR
                );
                INSERT INTO T VALUES
                   (NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
                   (0, 0, 0, 0, 0, 0, 0, '0'),
                   (1, 1, 1, 1, 1, 1, 1, '1'),
                   (2, 2, 2, 2, 2, 2, 2, '2');
                CREATE TABLE warehouse (
                   id INT PRIMARY KEY,
                   parentId INT
                );
                INSERT INTO warehouse VALUES
                   (10, 20),
                   (20, 20),
                   (30, 20),
                   (5,  5),
                   (1,  5),
                   (3,  3);
                CREATE TABLE NN (
                   I INT NOT NULL,
                   J INT,
                   K INT
                );
                INSERT INTO NN VALUES
                   (0, 0, 0),
                   (1, 1, 1),
                   (2, NULL, 0),
                   (3, NULL, 1);
                """);
    }

    @Test
    public void issue2042() {
        this.qs("""
                SELECT ARG_MAX(I, I), ARG_MAX(I, J), ARG_MAX(J, I), ARG_MAX(J, J)
                FROM NN;
                 ii | ij | ji   | jj
                --------------------
                 3  | 1  | NULL | 1
                (1 row)
                
                SELECT K, ARG_MAX(I, I), ARG_MAX(I, J), ARG_MAX(J, I), ARG_MAX(J, J)
                FROM NN GROUP BY K;
                 k | ii | ij | ji   | jj
                -------------------------
                 0 | 2  | 0  | NULL | 0
                 1 | 3  | 1  | NULL | 1
                (1 row)""");
    }

    @Test
    public void testIssue1957() {
        // validated using Postgres
        this.qs("""
                SELECT
                  id,
                  (SELECT ARRAY_AGG(id) FROM (
                    SELECT id FROM warehouse WHERE parentId = warehouse.id
                    ORDER BY id LIMIT 2
                  )) AS first_children
                FROM warehouse;
                 id |  array
                ---------------
                 1  | { 3, 5 }
                 3  | { 3, 5 }
                 5  | { 3, 5 }
                 10 | { 3, 5 }
                 20 | { 3, 5 }
                 30 | { 3, 5 }
                (2 rows)""");
    }

    @Test
    public void testOneArgMin() {
        this.qs("""
                SELECT ARG_MIN(V, B)
                FROM T;
                 B
                ---
                 0
                (1 row)""");
    }

    @Test
    public void testArgMin() {
        this.qs("""
                SELECT ARG_MIN(V, B), ARG_MIN(V, I), ARG_MIN(V, S), ARG_MIN(V, T),
                ARG_MIN(V, R), ARG_MIN(V, D), ARG_MIN(V, E)
                FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 0| 0| 0| 0| 0| 0| 0
                (1 row)
                
                SELECT ARG_MAX(V, B), ARG_MAX(V, I), ARG_MAX(V, S), ARG_MAX(V, T),
                ARG_MAX(V, R), ARG_MAX(V, D), ARG_MAX(V, E)
                FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 2| 2| 2| 2| 2| 2| 2
                (1 row)""");
    }
    
    @Test
    public void testAggregates() {
        this.qs("""
                SELECT COUNT(*), COUNT(B), COUNT(I), COUNT(S), COUNT(T), COUNT(R), COUNT(D), COUNT(E) FROM T;
                 * | B | I | S | T | R | D | E
                -------------------------------
                 4 | 3 | 3 | 3 | 3 | 3 | 3 | 3
                (1 row)
                
                SELECT SUM(B), SUM(I), SUM(S), SUM(T), SUM(R), SUM(D), SUM(E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 3 | 3 | 3 | 3 | 3 | 3 | 3
                (1 row)
                
                SELECT AVG(B), AVG(I), AVG(S), AVG(T), AVG(R), AVG(D), AVG(E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 1 | 1 | 1 | 1 | 1 | 1 | 1
                (1 row)
                
                SELECT MIN(B), MIN(I), MIN(S), MIN(T), MIN(R), MIN(D), MIN(E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 0 | 0 | 0 | 0 | 0 | 0 | 0
                (1 row)
                
                SELECT MAX(B), MAX(I), MAX(S), MAX(T), MAX(R), MAX(D), MAX(E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 2 | 2 | 2 | 2 | 2 | 2 | 2
                (1 row)
                
                SELECT STDDEV(B), STDDEV(I), STDDEV(S), STDDEV(T), STDDEV(R), STDDEV(D), STDDEV(E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 1 | 1 | 1 | 1 | 1 | 1 | 1
                (1 row)
                
                SELECT STDDEV_POP(B), STDDEV_POP(I), STDDEV_POP(S), STDDEV_POP(T), STDDEV_POP(R), STDDEV_POP(D), STDDEV_POP(E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 0 | 0 | 0 | 0 | 0.8164966 | 0.816496580927726 | 0.82
                (1 row)
                
                SELECT STDDEV_SAMP(B), STDDEV_SAMP(I), STDDEV_SAMP(S), STDDEV_SAMP(T), STDDEV_SAMP(R), STDDEV_SAMP(D), STDDEV_SAMP(E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 1 | 1 | 1 | 1 | 1 | 1 | 1
                (1 row)
                
                SELECT BIT_AND(B), BIT_AND(I), BIT_AND(S), BIT_AND(T) FROM T;
                 B | I | S | T
                ---------------
                 0 | 0 | 0 | 0
                (1 row)
                
                SELECT BIT_OR(B), BIT_OR(I), BIT_OR(S), BIT_OR(T) FROM T;
                 B | I | S | T
                ---------------
                 3 | 3 | 3 | 3
                (1 row)
                
                SELECT BIT_XOR(B), BIT_XOR(I), BIT_XOR(S), BIT_XOR(T) FROM T;
                 B | I | S | T
                ---------------
                 3 | 3 | 3 | 3
                (1 row)""");
    }

    @Test
    public void testDistinctAggregates() {
        this.qs("""
                SELECT COUNT(*), COUNT(DISTINCT B), COUNT(DISTINCT I), COUNT(DISTINCT S), COUNT(DISTINCT T), COUNT(DISTINCT R), COUNT(DISTINCT D), COUNT(DISTINCT E) FROM T;
                 * | B | I | S | T | R | D | E
                -------------------------------
                 4 | 3 | 3 | 3 | 3 | 3 | 3 | 3
                (1 row)
                
                SELECT SUM(DISTINCT B), SUM(DISTINCT I), SUM(DISTINCT S), SUM(DISTINCT T), SUM(DISTINCT R), SUM(DISTINCT D), SUM(DISTINCT E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 3 | 3 | 3 | 3 | 3 | 3 | 3
                (1 row)
                
                SELECT AVG(DISTINCT B), AVG(DISTINCT I), AVG(DISTINCT S), AVG(DISTINCT T), AVG(DISTINCT R), AVG(DISTINCT D), AVG(DISTINCT E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 1 | 1 | 1 | 1 | 1 | 1 | 1
                (1 row)
                
                SELECT MIN(DISTINCT B), MIN(DISTINCT I), MIN(DISTINCT S), MIN(DISTINCT T), MIN(DISTINCT R), MIN(DISTINCT D), MIN(DISTINCT E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 0 | 0 | 0 | 0 | 0 | 0 | 0
                (1 row)
                
                SELECT MAX(DISTINCT B), MAX(DISTINCT I), MAX(DISTINCT S), MAX(DISTINCT T), MAX(DISTINCT R), MAX(DISTINCT D), MAX(DISTINCT E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 2 | 2 | 2 | 2 | 2 | 2 | 2
                (1 row)
                
                SELECT STDDEV(DISTINCT B), STDDEV(DISTINCT I), STDDEV(DISTINCT S), STDDEV(DISTINCT T), STDDEV(DISTINCT R), STDDEV(DISTINCT D), STDDEV(DISTINCT E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 1 | 1 | 1 | 1 | 1 | 1 | 1
                (1 row)
                
                SELECT STDDEV_POP(DISTINCT B), STDDEV_POP(DISTINCT I), STDDEV_POP(DISTINCT S), STDDEV_POP(DISTINCT T), STDDEV_POP(DISTINCT R), STDDEV_POP(DISTINCT D), STDDEV_POP(DISTINCT E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 0 | 0 | 0 | 0 | 0.8164966 | 0.816496580927726 | 0.82
                (1 row)
                
                SELECT STDDEV_SAMP(DISTINCT B), STDDEV_SAMP(DISTINCT I), STDDEV_SAMP(DISTINCT S), STDDEV_SAMP(DISTINCT T), STDDEV_SAMP(DISTINCT R), STDDEV_SAMP(DISTINCT D), STDDEV_SAMP(DISTINCT E) FROM T;
                 B | I | S | T | R | D | E
                ---------------------------
                 1 | 1 | 1 | 1 | 1 | 1 | 1
                (1 row)
                
                SELECT BIT_AND(DISTINCT B), BIT_AND(DISTINCT I), BIT_AND(DISTINCT S), BIT_AND(DISTINCT T) FROM T;
                 B | I | S | T
                ---------------
                 0 | 0 | 0 | 0
                (1 row)
                
                SELECT BIT_OR(DISTINCT B), BIT_OR(DISTINCT I), BIT_OR(DISTINCT S), BIT_OR(DISTINCT T) FROM T;
                 B | I | S | T
                ---------------
                 3 | 3 | 3 | 3
                (1 row)
                
                SELECT BIT_XOR(DISTINCT B), BIT_XOR(DISTINCT I), BIT_XOR(DISTINCT S), BIT_XOR(DISTINCT T) FROM T;
                 B | I | S | T
                ---------------
                 3 | 3 | 3 | 3
                (1 row)""");
    }
}
