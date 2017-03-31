package epp;

import antlr4.ChoreographyBaseVisitor;
import antlr4.ChoreographyParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class PreProcessingVisitor extends ChoreographyBaseVisitor {

    private Set<String> processes;
    private HashMap<String,Set<String>> procedures;

    public PreProcessingVisitor(){
        processes = new HashSet();
        procedures = new HashMap();
    }


    @Override
    public Set<String> visitChoreography(ChoreographyParser.ChoreographyContext ctx) {
        visitChildren(ctx);
        return processes;
    }

    @Override
    public Object visitCondition(ChoreographyParser.ConditionContext ctx) {
        return super.visitCondition(ctx);
    }

    @Override
    public Object visitProcedureDefinition(ChoreographyParser.ProcedureDefinitionContext ctx) {
        //procedures.put(ctx.procedure().getText(), visitChoreography());

        PreProcessingVisitor ppp = new PreProcessingVisitor();
        ppp.visit(ctx.internal_choreography());
        procedures.put(ctx.procedure().getText(), ppp.processes);
        return super.visitProcedureDefinition(ctx);
    }

    @Override
    public Object visitProcess(ChoreographyParser.ProcessContext ctx) {
        processes.add(ctx.getText());
        Object p = visitChildren(ctx);
        return "a";
        //return super.visitProcess(ctx);
    }

    public Set<String> getProcesses() {
        return processes;
    }

    public HashMap<String, Set<String>> getProcedures() {
        return procedures;
    }
}
