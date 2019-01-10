package ast.sp.labels

import ast.sp.labels.interfaces.ConditionLabel

data class ThenLabel(override val process: String, override val expression: String) : ConditionLabel(process, expression, false) {
    override fun copy() = ThenLabel(process, expression)

    override fun toString() = "if $process.$expression then "

    override fun equals(other: Any?): Boolean {
        return (this === other)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + process.hashCode()
        result = 31 * result + expression.hashCode()
        return result
    }
}
