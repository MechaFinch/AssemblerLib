package asmlib.lex.symbols;

import java.util.ArrayList;

/**
 * A {@link Symbol} which represents a memory access - a group of symbols in square brackets
 * 
 * @author Mechafinch
 */
public record MemorySymbol(ArrayList<Symbol> symbols) implements SymbolGroup {
    
}
