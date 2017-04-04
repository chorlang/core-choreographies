/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package epp;

import ast.cc.interfaces.CCNode;
import ast.cc.CCVisitor;
import ast.cc.nodes.*;
import ast.sp.interfaces.SPNode;

/**
 *
 * @author Fabrizio Montesi <famontesi@gmail.com>
 */
public class BehaviourProjection implements CCVisitor< SPNode >
{
    public SPNode getSPAST(CCNode node){
        return node.accept(this);
    }

    public SPNode visit(Selection n )
    {
        // Visit n.continuation() first, getting the projection of the continuation
        // Create the return Sending with the continuation you got and the information for the head here.
        //return Sending();
        return null;
    }

    @Override
    public SPNode visit(Communication n) {
        return null;
    }

    @Override
    public SPNode visit(Condition n) {
        return null;
    }

    @Override
    public SPNode visit(Termination n) {
        return null;
    }

    @Override
    public SPNode visit(ProcedureDefinition n) {
        return null;
    }

    @Override
    public SPNode visit(ProcedureInvocation n) {
        return null;
    }
}
