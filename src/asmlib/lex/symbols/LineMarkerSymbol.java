package asmlib.lex.symbols;

/**
 * A {@link Symbol} marking a new line
 * 
 * @param lineNumber
 * @author Mechafinch
 */
public record LineMarkerSymbol(int lineNumber) implements Symbol {
    
}
