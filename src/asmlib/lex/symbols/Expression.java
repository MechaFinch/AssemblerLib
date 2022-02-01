package asmlib.lex.symbols;

import java.util.ArrayList;

/**
 * A {@link Symbol} representing an expression - a group of symbols in parentheses
 * 
 * @author Mechafinch
 */
public record Expression(ArrayList<Symbol> symbols) implements SymbolGroup {
    
}
