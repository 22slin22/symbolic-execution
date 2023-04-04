import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class SymbolicExecutionKtTest {

    @Test
    fun freeVariablesIncludesFreeVariables() {
        val freeVars = Block(
            Var("x1"),
            Eq(Var("x2"), Var("x3")),
            If(NEq(Var("x4"), Var("x5")),
                Plus(Minus(Var("x6"), Var("x7")), Var("x8")),
                Let(Var("y"), Var("x9"))
        )).freeVariables()
        assertEquals(
            setOf(Var("x1"), Var("x2"), Var("x3"), Var("x4"), Var("x5"), Var("x6"), Var("x7"), Var("x8"), Var("x9")),
            freeVars)
    }

    @Test
    fun boundVariableNotFree() {
        val freeVars = Block(
            Let(Var("x"), Var("y")),
            Var("x")).freeVariables()
        assertFalse(freeVars.contains(Var("x")))
    }

    @Test
    fun variableFreeBeforeBound() {
        val freeVars = Block(
            Var("x"),
            Let(Var("x"), Var("y"))).freeVariables()
        assertTrue(freeVars.contains(Var("x")))
    }

    @Test
    fun symbolicVarNotFree() {
        val freeVars = SymVal("x").freeVariables()
        assertEquals(emptySet<Var>(), freeVars)
    }

    @Test
    fun evaluateSimplifiesConstantOperation() {
        val result = Plus(Const(1), Minus(Mul(Const(2), Const(3)), Const(2)))
            .eval(emptyMap<Var,Expr>().toMutableMap())
        assertEquals(Const(5), result)
    }

    @Test
    fun evaluateVariable() {
        val result = Var("x").eval(mutableMapOf(Var("x") to Const(3)))
        assertEquals(Const(3), result)
    }

    @Test
    fun evaluateVariableNotInState() {
        assertThrows(IllegalArgumentException::class.java) {
            Var("x").eval(mutableMapOf(Var("y") to Const(3)))
        }
    }

    @Test
    fun evaluateLetUpdatesState() {
        val result = Block(
            Let(Var("x"), Const(10)),
            Var("x"))
            .eval(mutableMapOf(Var("x") to Const(3)))
        assertEquals(Const(10), result)
    }

    @Test
    fun evaluateIf() {
        val result = If(Eq(Const(1), Const(2)), Const(3), Const(4))
            .eval(mutableMapOf())
        assertEquals(Const(4), result)
    }

    @Test
    fun testAstToSymForwardTree() {
        val ast = Block(
            Let(Var("x"), Const(1)),
            Let(Var("y"), Const(0)),
            If( NEq(Var("a"), Const(0)),
                Block(
                    Let(Var("y"), Plus(Const(3), Var("x"))),
                    If( Eq(Var("b"), Const(0)),
                        Let(Var("x"), Mul(Const(2), Plus(Var("a"), Var("b")))),
                    )
                )
            ),
            Minus(Var("x"), Var("y"))
        )
        assertEquals("""σ: {a=Sym(a), b=Sym(b)}
π: []
Next Expression: let x = 1
  σ: {a=Sym(a), b=Sym(b), x=1}
  π: []
  Next Expression: let y = 0
    σ: {a=Sym(a), b=Sym(b), x=1, y=0}
    π: []
    Next Expression: if (a != 0)
      σ: {a=Sym(a), b=Sym(b), x=1, y=0}
      π: [Sym(a) != 0]
      Next Expression: let y = (3 + x)
        σ: {a=Sym(a), b=Sym(b), x=1, y=4}
        π: [Sym(a) != 0]
        Next Expression: if (b == 0)
          σ: {a=Sym(a), b=Sym(b), x=1, y=4}
          π: [Sym(a) != 0, Sym(b) == 0]
          Next Expression: let x = (2 * (a + b))
            σ: {a=Sym(a), b=Sym(b), x=(2 * (Sym(a) + Sym(b))), y=4}
            π: [Sym(a) != 0, Sym(b) == 0]
            Next Expression: (x - y)
          σ: {a=Sym(a), b=Sym(b), x=1, y=4}
          π: [Sym(a) != 0, Sym(b) != 0]
          Next Expression: (x - y)
      σ: {a=Sym(a), b=Sym(b), x=1, y=0}
      π: [Sym(a) == 0]
      Next Expression: (x - y)
""",
            ast.symForwardTree().toString())
    }

    @Test
    fun findCounterExampleTest() {
        val ast = Block(
            Let(Var("x"), Const(1)),
            Let(Var("y"), Const(0)),
            If(
                NEq(Var("a"), Const(0)),
                Block(
                    Let(Var("y"), Plus(Const(3), Var("x"))),
                    If(
                        Eq(Var("b"), Const(0)),
                        Let(Var("x"), Mul(Const(2), Plus(Var("a"), Var("b")))),
                    )
                )
            ),
            Assert(NEq(Minus(Var("x"), Var("y")), Const(0)))
        )

        val counterExamples = ast.symForwardTree()!!.findCounterExamples()
        assertEquals("[a: 2, b: 0]", counterExamples.first().asList().toString())
    }
}