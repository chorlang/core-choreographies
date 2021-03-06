package lcf;

import java.util.HashSet;

/*
 * Class for computing procedures used in a eta, using the visitor pattern.
 */
public class UsedProcedures implements CNVisitor {

    private HashSet<String> calls;

    public UsedProcedures() {
        calls = new HashSet<String>();
    }

    /*
     * Only name calls change the value of the attribute.
     * For the remaining eta types, we either recur or combine the results of the recursive calls.
     */
    public void visit(TerminationNode n) {}

    public void visit(CommunicationNode n) {
        n.getNextAction().accept(this);
    }

    public void visit(SelectionNode n) {
        n.getNextAction().accept(this);
    }

    public void visit(ConditionalNode n) {
        n.getThenAction().accept(this);
        n.getElseAction().accept(this);
    }

    public void visit(CallNode n) {
        calls.add(n.getProcedure());
    }

    /*
     * Method for running the algorithm.
     */
    public static HashSet<String> run(ChoreographyNode n) {
        UsedProcedures runObject = new UsedProcedures();
        n.accept(runObject);
        return runObject.calls;
    }

}
