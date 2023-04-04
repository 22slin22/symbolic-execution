// Here is the example program:

fun main () {
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

    val forwardExecutionTree = ast.symForwardTree()!!
    val counterExamples = forwardExecutionTree.findCounterExamples()

    println("Found counter example: ${counterExamples.first().asList()}")
    // -> Found counter example: [a: 2, b: 0]
}