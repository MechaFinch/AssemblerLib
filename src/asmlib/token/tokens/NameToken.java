package asmlib.token.tokens;

/**
 * A {@link Token} which holds a name of some kind
 * 
 * @param text The name
 * @author Mechafinch
 */
public record NameToken(String text) implements Token {
    
}
