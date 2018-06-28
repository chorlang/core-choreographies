package ast.cc.nodes

import ast.cc.CCVisitor
import ast.cc.interfaces.CCNode
import ast.cc.interfaces.Behaviour

data class Condition(val process: String, val expression: String, val thenChoreography: CCNode, val elseChoreograpy: CCNode) : Behaviour {
    override fun <T> accept(visitor: CCVisitor<T>): T = visitor.visit(this)

    override fun toString(): String {
        val sb = StringBuilder()
        sb
                .append("if ")
                .append(process)
                .append(".")
                .append(expression)
                .append(" then ")
                .append(thenChoreography.toString())
                .append(" else ")
                .append(elseChoreograpy.toString())

        return sb.toString()
    }
}
