package asmlib.lex.symbols;

/**
 * A {@link Symbol} marking a new line
 * 
 * @param lineNumber
 * @author Mechafinch
 */
public record LineMarker(int lineNumber) implements Symbol {
    
}
