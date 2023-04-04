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

data class Assert(val constraint: Expr) : Expr() {
    override fun toString(): String = "assert($constraint)"
}

data class SymVal(val name: String) : Expr() {
    override fun toString(): String = "Sym($name)"
}

fun Expr.freeVariables(): Set<Var> =
    when (this) {
        is Var -> setOf(this)
        is Const -> emptySet()
        is Block -> {
            val boundVars = emptySet<Var>().toMutableSet()
            val freeVars = emptySet<Var>().toMutableSet()
            exprs.forEach {
                if (it is Let)
                    boundVars += it.variable
                // We delete the bound variables in each statement and not only at the end because a variable can occur
                // free before it is bound
                freeVars += it.freeVariables() - boundVars
            }
            freeVars
        }

        is Let -> value.freeVariables() - variable
        is Eq -> left.freeVariables() + right.freeVariables()
        is NEq -> left.freeVariables() + right.freeVariables()
        is If -> cond.freeVariables() + thenExpr.freeVariables() + (elseExpr?.freeVariables() ?: emptySet())
        is Plus -> left.freeVariables() + right.freeVariables()
        is Minus -> left.freeVariables() + right.freeVariables()
        is Mul -> left.freeVariables() + right.freeVariables()
        is SymVal -> emptySet()
        is Assert -> emptySet()
    }

fun Expr.symbolicVariables(): Set<SymVal> =
    when (this) {
        is Var -> emptySet()
        is Const -> emptySet()
        is Block -> exprs.flatMap { it.symbolicVariables() }.toSet()
        is Let -> value.symbolicVariables()
        is Eq -> left.symbolicVariables() + right.symbolicVariables()
        is NEq -> left.symbolicVariables() + right.symbolicVariables()
        is If -> cond.symbolicVariables() + thenExpr.symbolicVariables() + (elseExpr?.symbolicVariables() ?: emptySet())
        is Plus -> left.symbolicVariables() + right.symbolicVariables()
        is Minus -> left.symbolicVariables() + right.symbolicVariables()
        is Mul -> left.symbolicVariables() + right.symbolicVariables()
        is SymVal -> setOf(this)
        is Assert -> constraint.symbolicVariables()
    }

fun Expr.negate(): Expr =
    when (this) {
        is Eq -> NEq(left, right)
        is NEq -> Eq(left, right)
        else -> throw IllegalArgumentException("Cannot negate $this")
    }

/**
 * This function will evaluate the given expression to an expression over constants and symbolic symbols.
 */
fun Expr.eval(state: MutableMap<Var, Expr>): Expr? =
    when (this) {
        is Var -> state[this] ?: throw IllegalArgumentException("Variable $name not found in state $state")
        is Const -> Const(value)
        is Block -> {
            var result: Expr? = null
            exprs.forEach { result = it.eval(state) }
            result ?: throw IllegalArgumentException("Cannot evaluate empty block")
        }

        is Let -> {
            val value = value.eval(state)
            state[variable] = value ?: throw IllegalArgumentException()
            value
        }

        is Eq -> Eq(
            left.eval(state) ?: throw IllegalArgumentException("Invalid expression $left"),
            right.eval(state) ?: throw IllegalArgumentException("Invalid expression $right")
        )

        is NEq -> NEq(
            left.eval(state) ?: throw IllegalArgumentException("Invalid expression $left"),
            right.eval(state) ?: throw IllegalArgumentException("Invalid expression $right")
        )

        is If -> {
            // Simplify to the then or else branch if possible
            when (val cond = cond.eval(state)) {
                is Eq -> {
                    if (cond.left is Const && cond.right is Const) {
                        if (cond.left.value == cond.right.value) {
                            thenExpr.eval(state)
                        } else {
                            elseExpr?.eval(state)
                        }
                    } else {
                        If(
                            cond,
                            thenExpr.eval(state)
                                ?: throw IllegalArgumentException("Invalid expression $thenExpr"),
                            elseExpr?.eval(state)
                        )
                    }
                }

                is NEq -> {
                    if (cond.left is Const && cond.right is Const) {
                        if (cond.left.value != cond.right.value) {
                            thenExpr.eval(state)
                        } else {
                            elseExpr?.eval(state)
                        }
                    } else {
                        If(
                            cond,
                            thenExpr.eval(state)
                                ?: throw IllegalArgumentException("Invalid expression $thenExpr"),
                            elseExpr?.eval(state)
                        )
                    }
                }

                else -> throw IllegalArgumentException("Invalid condition $cond, must be Eq or NEq")
            }
        }

        is Plus -> {
            val left = left.eval(state)
            val right = right.eval(state)
            if (left is Const && right is Const) {
                Const(left.value + right.value)
            } else {
                Plus(
                    left ?: throw IllegalArgumentException("Invalid expression $left"),
                    right ?: throw IllegalArgumentException("Invalid expression $right")
                )
            }
        }

        is Minus -> {
            val leftVal = left.eval(state)
            val rightVal = right.eval(state)
            if (leftVal is Const && rightVal is Const) {
                Const(leftVal.value - rightVal.value)
            } else {
                Minus(
                    leftVal ?: throw IllegalArgumentException("Invalid expression $left"),
                    rightVal ?: throw IllegalArgumentException("Invalid expression $right")
                )
            }
        }

        is Mul -> {
            val leftVal = left.eval(state)
            val rightVal = right.eval(state)
            if (leftVal is Const && rightVal is Const) {
                Const(leftVal.value * rightVal.value)
            } else {
                Mul(
                    leftVal ?: throw IllegalArgumentException("Invalid expression $left"),
                    rightVal ?: throw IllegalArgumentException("Invalid expression $right")
                )
            }
        }

        is SymVal -> SymVal(name)
        is Assert -> Assert(
            constraint.eval(state) ?: throw IllegalArgumentException("Invalid assertion $constraint")
        )
    }