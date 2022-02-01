package asmlib.lex.symbols;

import java.util.List;

/**
 * A {@link Symbol} wrapping a group of symbols
 * 
 * @author Mechafinch
 */
public interface SymbolGroup extends Symbol {
    
    /**
     * @return list of symbols in the group
     */
    public List<Symbol> symbols();
}
