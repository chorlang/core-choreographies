// Generated from CommonLexerRules.g4 by ANTLR 4.7.1
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link CommonLexerRulesParser}.
 */
public interface CommonLexerRulesListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link CommonLexerRulesParser#process}.
	 * @param ctx the parse tree
	 */
	void enterProcess(CommonLexerRulesParser.ProcessContext ctx);
	/**
	 * Exit a parse tree produced by {@link CommonLexerRulesParser#process}.
	 * @param ctx the parse tree
	 */
	void exitProcess(CommonLexerRulesParser.ProcessContext ctx);
	/**
	 * Enter a parse tree produced by {@link CommonLexerRulesParser#procedure}.
	 * @param ctx the parse tree
	 */
	void enterProcedure(CommonLexerRulesParser.ProcedureContext ctx);
	/**
	 * Exit a parse tree produced by {@link CommonLexerRulesParser#procedure}.
	 * @param ctx the parse tree
	 */
	void exitProcedure(CommonLexerRulesParser.ProcedureContext ctx);
	/**
	 * Enter a parse tree produced by {@link CommonLexerRulesParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(CommonLexerRulesParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link CommonLexerRulesParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(CommonLexerRulesParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link CommonLexerRulesParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(CommonLexerRulesParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link CommonLexerRulesParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(CommonLexerRulesParser.ValueContext ctx);
}