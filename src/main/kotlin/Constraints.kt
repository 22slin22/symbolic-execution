import org.sosy_lab.common.ShutdownManager
import org.sosy_lab.common.configuration.Configuration
import org.sosy_lab.common.log.BasicLogManager
import org.sosy_lab.common.log.LogManager
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.api.*
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula
import org.sosy_lab.java_smt.example.SolverOverviewTable


fun Expr.smtFormula(bmgr: BooleanFormulaManager, imgr: IntegerFormulaManager,variables: Map<String, IntegerFormula>) : Formula =
    when (this) {
        is Var -> throw IllegalArgumentException("Expression should only contain symbolic variables but contains the regular variable $name")
        is Const -> imgr.makeNumber(value.toLong())
        is Block -> TODO()
        is Let -> TODO()
        is Eq -> imgr.equal(left.smtFormula(bmgr, imgr, variables) as IntegerFormula,
            right.smtFormula(bmgr, imgr, variables) as IntegerFormula
        )
        is NEq -> bmgr.not(imgr.equal(left.smtFormula(bmgr, imgr, variables) as IntegerFormula,
            right.smtFormula(bmgr, imgr, variables) as IntegerFormula
        ))
        is If -> TODO()
        is Plus -> imgr.add(left.smtFormula(bmgr, imgr, variables) as IntegerFormula,
            right.smtFormula(bmgr, imgr, variables) as IntegerFormula
        )
        is Minus -> imgr.subtract(left.smtFormula(bmgr, imgr, variables) as IntegerFormula,
            right.smtFormula(bmgr, imgr, variables) as IntegerFormula
        )
        is Mul -> imgr.multiply(left.smtFormula(bmgr, imgr, variables) as IntegerFormula,
            right.smtFormula(bmgr, imgr, variables) as IntegerFormula
        )
        is SymVal -> variables[name] ?: throw IllegalArgumentException("Unknown variable $name")
        is Assert -> throw IllegalArgumentException("An assertion cannot be converted to a formula")
    }

class Constraints(val assertion: Assert, val pathConstraints: Set<Expr>) {

    fun constraintExprs(): Set<Expr> {
        return setOf(assertion.constraint.negate()) + pathConstraints
    }
    fun constraintVars(): Set<String> {
        val symVars = constraintExprs().flatMap { it.symbolicVariables() }
        return symVars.map { it.name }.toSet()
    }

    fun smtConstraints(context: SolverContext): Set<BooleanFormula> {
        val bmgr = context.formulaManager.booleanFormulaManager
        val imgr = context.formulaManager.integerFormulaManager

        val vars = constraintVars().associateWith { imgr.makeVariable(it) }
        return constraintExprs().map { it.smtFormula(bmgr, imgr, vars) as BooleanFormula }.toSet()
    }
}

object ConstraintSolver {
    val config: Configuration = Configuration.defaultConfiguration()
    val logger: LogManager = BasicLogManager.create(config)
    val shutdownManager: ShutdownManager = ShutdownManager.create()

    val context = SolverContextFactory.createSolverContext(config, logger, shutdownManager.notifier, SolverContextFactory.Solvers.SMTINTERPOL)

    fun solveConstraint(constraint: Constraints): Model? {
        with(context.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)) {
            constraint.smtConstraints(context).forEach(::addConstraint)
            return if (isUnsat) null else model
        }
    }
}

fun main() {
    val infos: MutableList<SolverOverviewTable.SolverInfo> = mutableListOf()
    for (s in SolverContextFactory.Solvers.values()) {
        val info: SolverOverviewTable.SolverInfo? = SolverOverviewTable().getSolverInformation(s)
        if (info != null) {
            infos.add(info)
        }
    }

    infos.sortBy { it.getName() } // alphabetical ordering

    val rowBuilder = SolverOverviewTable.RowBuilder()
    for (info in infos) {
        rowBuilder.addSolver(info)
    }
    println(rowBuilder)
}