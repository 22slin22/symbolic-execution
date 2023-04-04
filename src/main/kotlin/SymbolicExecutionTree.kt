import org.sosy_lab.java_smt.api.Model

class ExecTreeNode(
    val children: List<ExecTreeNode>,
    val nextExpr: Expr,
    val S: Map<Var, Expr>,
    val Pi: Set<Expr>
) {
    override fun toString(): String = toString(0)

    private fun toString(indent: Int): String {
        var string = " ".repeat(indent) + "σ: " + S + "\n"
        string += " ".repeat(indent) + "π: " + Pi + "\n"
        string += " ".repeat(indent) + "Next Expression: " + nextExpr.toShortString() + "\n"
        children.forEach { string += it.toString(indent + 2) }
        return string
    }

    fun constraints(): List<Constraints> {
        val constraints = mutableListOf<Constraints>()
        if (nextExpr is Assert)
            constraints.add(Constraints(nextExpr.eval(S.toMutableMap()) as Assert, Pi))

        children.forEach { constraints.addAll(it.constraints()) }
        return constraints
    }

    fun findCounterExamples(): List<Model> = constraints().mapNotNull { ConstraintSolver.solveConstraint(it) }
}

/**
 * This function will convert a list of expressions to a symbolic execution tree.
 * @param exprs A list of expression to be executed in order.
 * @param symStore A map of variables to symbolic expressions.
 * @param pathConstraints A set of path constraints.
 */
fun astToSymForwardTree(exprs: ArrayDeque<Expr>, symStore: Map<Var, Expr>, pathConstraints: Set<Expr>): ExecTreeNode? {
    if (exprs.isEmpty()) return null

    when (val stmt = exprs.removeFirst()) {
        is Block -> {
            // Unfold the block statement and add all expressions to the front of the queue (to be executed first).
            exprs.addAll(0, stmt.exprs.asList())
            return astToSymForwardTree(exprs, symStore, pathConstraints)
        }

        is Let -> {
            // Evaluate the value of the let statement and add it to the symbol store, then continue execution with the
            // remaining expressions.
            val child = astToSymForwardTree(
                exprs,
                symStore + (stmt.variable to (stmt.value.eval(symStore.toMutableMap()) ?: throw IllegalArgumentException("Invalid value to ${stmt.value}"))),
                pathConstraints
            )
            return ExecTreeNode(listOfNotNull(child), stmt, symStore, pathConstraints)
        }

        is If -> {
            // Evaluate the condition to simplify it and substitute values for variables.
            val condEval = stmt.cond.eval(symStore.toMutableMap())
                ?: throw IllegalArgumentException("Invalid condition $stmt.cond")
            // Copy the expressions to be executed and add the then branch to the front of the queue.
            val thenExprs = ArrayDeque(exprs)
            thenExprs.add(0, stmt.thenExpr)
            val childTrue = astToSymForwardTree(thenExprs, symStore, pathConstraints + condEval)

            // If the If-statement has an else branch, execute it first before executing the remaining expressions.
            if (stmt.elseExpr != null) exprs.add(0, stmt.elseExpr)
            val childFalse = astToSymForwardTree(exprs, symStore, pathConstraints + condEval.negate())
            return ExecTreeNode(listOfNotNull(childTrue, childFalse), stmt, symStore, pathConstraints)
        }

        else -> {
            val child = astToSymForwardTree(exprs, symStore, pathConstraints)
            return ExecTreeNode(listOfNotNull(child), stmt, symStore, pathConstraints)
        }
    }
}

fun Expr.symForwardTree(): ExecTreeNode? = astToSymForwardTree(ArrayDeque(listOf(this)),
    freeVariables().associateWith { SymVal(it.name) }, emptySet()
)


