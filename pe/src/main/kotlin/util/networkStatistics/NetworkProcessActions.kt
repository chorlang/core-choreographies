package util.networkStatistics

import ast.sp.interfaces.Behaviour
import ast.sp.interfaces.SPVisitor
import ast.sp.nodes.*
import javax.naming.OperationNotSupportedException

class NetworkProcessActions: SPVisitor<Int> {
    override fun visit(n: ConditionSP): Int {
        return n.elseBehaviour.accept(this) + n.thenBehaviour.accept(this)
    }

    override fun visit(n: Network): Int {
        throw OperationNotSupportedException()
    }

    override fun visit(n: OfferingSP): Int {
        return n.branches.values.sumBy { behaviour -> behaviour.accept(this) } + 1
    }

    override fun visit(n: ParallelNetworks): Int {
        throw OperationNotSupportedException()
    }

    override fun visit(n: ProcedureInvocationSP): Int {
        return 0
    }

    override fun visit(n: ProcessTerm): Int {
        return n.procedures.values.toList().foldRight(0) { procedure, next -> procedure.accept(this) + next } + n.main.accept(this)
    }

    override fun visit(n: ReceiveSP): Int {
        return n.continuation.accept(this) +1
    }

    override fun visit(n: SelectionSP): Int {
        return n.continuation.accept(this) + 1
    }

    override fun visit(n: SendSP): Int {
        return n.continuation.accept(this) + 1
    }

    override fun visit(n: TerminationSP): Int {
        return 0
    }

    fun visit(b: Behaviour): Int {
        return b.accept(this)
    }
}