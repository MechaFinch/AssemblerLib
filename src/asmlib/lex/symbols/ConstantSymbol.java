package asmlib.lex.symbols;

/**
 * A {@link Symbol} containing a number
 * 
 * @param value
 * @author Mechafinch
 */
public record ConstantSymbol(long value) implements Symbol {
    
}
