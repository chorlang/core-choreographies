package ast.cc.nodes

import ast.cc.CCVisitor
import ast.cc.interfaces.CCNode
import ast.cc.interfaces.Choreography

data class Condition(val process: String, val expression: String, val thenChoreography: CCNode, val elseChoreograpy: CCNode) : Choreography {
    override fun <T> accept(visitor: CCVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun toString(): String {
        return "if " + expression + " then " + thenChoreography.toString() + " else " + elseChoreograpy.toString()
    }
}