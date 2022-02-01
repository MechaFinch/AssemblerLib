package asmlib.lex.symbols;

/**
 * A {@link Symbol} containing a comment
 * 
 * @param comment
 * @author Mechafinch
 */
public record Comment(String comment) implements Symbol {
    
}
