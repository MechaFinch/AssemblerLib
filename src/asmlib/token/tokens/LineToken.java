package asmlib.token.tokens;

/**
 * A {@link Token} marking a new line
 * 
 * @param lineNumber
 * @author Mechafinch
 */
public record LineToken(int lineNumber) implements Token {
    
}
