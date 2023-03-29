sealed class Expr {
    open fun toShortString(): String = toString()
}

class Block(vararg val exprs: Expr) : Expr() {
    override fun toString(): String = "{" + exprs.joinToString("\n") + "}"
}

data class Const(val value: Int) : Expr() {
    override fun toString(): String = value.toString()
}
data class Var(val name: String) : Expr() {
    override fun toString(): String = name
}
data class Let(val variable: Var, val value: Expr) : Expr() {
    override fun toString(): String = "let $variable = $value"
}
data class Eq(val left: Expr, val right: Expr) : Expr() {
    override fun toString(): String = "$left == $right"
}
data class NEq(val left: Expr, val right: Expr) : Expr() {
    override fun toString(): String = "$left != $right"
}
data class If(val cond: Expr, val thenExpr: Expr, val elseExpr: Expr? = null) : Expr() {
    override fun toString(): String = "if ($cond) $thenExpr" + (elseExpr?.let { " else $it" } ?: "")
    override fun toShortString(): String = "if ($cond)"
}
data class Plus(val left: Expr, val right: Expr) : Expr() {
    override fun toString(): String = "($left + $right)"
}
data class Minus(val left: Expr, val right: Expr) : Expr() {
    override fun toString(): String = "($left - $right)"
}
data class Mul(val left: Expr, val right: Expr) : Expr() {
    override fun toString(): String = "($left * $right)"
}

data class SymVal(val name: String) : Expr() {
    override fun toString(): String = "Sym($name)"
}

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
}

fun freeVariables(expr: Expr) : Set<Var> =
    when (expr) {
        is Var -> setOf(expr)
        is Const -> emptySet()
        is Block -> {
            val boundVars = emptySet<Var>().toMutableSet()
            val freeVars = emptySet<Var>().toMutableSet()
            expr.exprs.forEach {
                if (it is Let)
                    boundVars += it.variable
                // We delete the bound variables in each statement and not only at the end because a variable can occur
                // free before it is bound
                freeVars += freeVariables(it) - boundVars
            }
            freeVars
        }
        is Let -> freeVariables(expr.value) - expr.variable
        is Eq -> freeVariables(expr.left) + freeVariables(expr.right)
        is NEq -> freeVariables(expr.left) + freeVariables(expr.right)
        is If -> freeVariables(expr.cond) + freeVariables(expr.thenExpr) + (expr.elseExpr?.let { freeVariables(it) } ?: emptySet())
        is Plus -> freeVariables(expr.left) + freeVariables(expr.right)
        is Minus -> freeVariables(expr.left) + freeVariables(expr.right)
        is Mul -> freeVariables(expr.left) + freeVariables(expr.right)
        is SymVal -> emptySet()
    }

fun negate(expr: Expr): Expr =
    when (expr) {
        is Eq -> NEq(expr.left, expr.right)
        is NEq -> Eq(expr.left, expr.right)
        else -> throw IllegalArgumentException("Cannot negate $expr")
    }

/**
 * This function will evaluate the given expression to an expression over constants and symbolic symbols.
 */
fun evaluate(expr: Expr, state: MutableMap<Var, Expr>) : Expr? =
    when (expr) {
        is Var -> state[expr] ?: throw IllegalArgumentException("Variable $expr not found in state $state")
        is Const -> Const(expr.value)
        is Block -> {
            var result: Expr? = null
            expr.exprs.forEach { result = evaluate(it, state) }
            result ?: throw IllegalArgumentException("Cannot evaluate empty block")
        }
        is Let -> {
            val value = evaluate(expr.value, state)
            state[expr.variable] = value ?: throw IllegalArgumentException()
            value
        }
        is Eq -> Eq(evaluate(expr.left, state) ?: throw IllegalArgumentException("Invalid expression ${expr.left}"), evaluate(expr.right, state) ?: throw IllegalArgumentException("Invalid expression ${expr.right}"))
        is NEq -> NEq(evaluate(expr.left, state) ?: throw IllegalArgumentException("Invalid expression ${expr.left}"), evaluate(expr.right, state) ?: throw IllegalArgumentException("Invalid expression ${expr.right}"))
        is If -> {
            // Simplify to the then or else branch if possible
            when (val cond = evaluate(expr.cond, state)) {
                is Eq -> {
                    if (cond.left is Const && cond.right is Const) {
                        if (cond.left.value == cond.right.value) {
                            evaluate(expr.thenExpr, state)
                        } else {
                            expr.elseExpr?.let { evaluate(it, state) }
                        }
                    } else {
                        If(cond, evaluate(expr.thenExpr, state) ?: throw IllegalArgumentException("Invalid expression ${expr.thenExpr}"), expr.elseExpr?.let { evaluate(it, state) })
                    }
                }
                is NEq -> {
                    if (cond.left is Const && cond.right is Const) {
                        if (cond.left.value != cond.right.value) {
                            evaluate(expr.thenExpr, state)
                        } else {
                            expr.elseExpr?.let { evaluate(it, state) }
                        }
                    } else {
                        If(cond, evaluate(expr.thenExpr, state) ?: throw IllegalArgumentException("Invalid expression ${expr.thenExpr}"), expr.elseExpr?.let { evaluate(it, state) })
                    }
                }
                else -> throw IllegalArgumentException("Invalid condition $cond, must be Eq or NEq")
            }
        }
        is Plus -> {
            val left = evaluate(expr.left, state)
            val right = evaluate(expr.right, state)
            if (left is Const && right is Const) {
                Const(left.value + right.value)
            } else {
                Plus(left ?: throw IllegalArgumentException("Invalid expression ${expr.left}"),
                    right ?: throw IllegalArgumentException("Invalid expression ${expr.right}"))
            }
        }
        is Minus -> {
            val left = evaluate(expr.left, state)
            val right = evaluate(expr.right, state)
            if (left is Const && right is Const) {
                Const(left.value - right.value)
            } else {
                Minus(left ?: throw IllegalArgumentException("Invalid expression ${expr.left}"),
                    right ?: throw IllegalArgumentException("Invalid expression ${expr.right}"))
            }
        }
        is Mul -> {
            val left = evaluate(expr.left, state)
            val right = evaluate(expr.right, state)
            if (left is Const && right is Const) {
                Const(left.value * right.value)
            } else {
                Mul(left ?: throw IllegalArgumentException("Invalid expression ${expr.left}"),
                    right ?: throw IllegalArgumentException("Invalid expression ${expr.right}"))
            }
        }
        is SymVal -> SymVal(expr.name)
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
            val child = astToSymForwardTree(exprs, symStore + (stmt.variable to evaluate(stmt.value, symStore.toMutableMap())!!), pathConstraints)
            return ExecTreeNode(listOfNotNull(child), stmt, symStore, pathConstraints)
        }
        is If -> {
            // Evaluate the condition to simplify it and substitute values for variables.
            val condEval = evaluate(stmt.cond, symStore.toMutableMap()) ?: throw IllegalArgumentException("Invalid condition $stmt.cond")
            // Copy the expressions to be executed and add the then branch to the front of the queue.
            val thenExprs = ArrayDeque(exprs)
            thenExprs.add(0, stmt.thenExpr)
            val childTrue = astToSymForwardTree(thenExprs, symStore, pathConstraints + condEval)

            // If the If-statement has an else branch, execute it first before executing the remaining expressions.
            if (stmt.elseExpr != null) exprs.add(0, stmt.elseExpr)
            val childFalse = astToSymForwardTree(exprs, symStore, pathConstraints + negate(condEval))
            return ExecTreeNode(listOfNotNull(childTrue, childFalse), stmt, symStore, pathConstraints)
        }
        else -> {
            val child = astToSymForwardTree(exprs, symStore, pathConstraints)
            return ExecTreeNode(listOfNotNull(child), stmt, symStore, pathConstraints)
        }
    }
}

fun astToSymForwardTree(expr: Expr): ExecTreeNode? = astToSymForwardTree(ArrayDeque(listOf(expr)), freeVariables(expr).associateWith { SymVal(it.name) }, emptySet())
