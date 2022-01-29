package asmlib.token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import asmlib.token.tokens.*;

/**
 * The {@code Tokenizer} converts a file into generic {@link Token}s with minimal information
 * Parses numbers, discards comments, and condenses whitespace
 * 
 * @author Mechafinch
 */
public class Tokenizer {
    
    private static boolean INCLUDE_COMMENTS = false,
                           INCLUDE_WHITESPACE = false,
                           HANDLE_STRINGS = true,
                           VERBOSE = false;
    
    private static char COMMENT_MARKER = ';';
    
    // pattern for detecting numbers
    private static Pattern numberPattern = Pattern.compile("0x[0-9a-f]+|(0d)?[0-9]+|0o[0-7]+|0b[01]+");
    
    private static HashSet<Character> specialCharacters = new HashSet<Character>("()[],:+-*/".chars()
                                                                                             .mapToObj(c -> (char) c)
                                                                                             .collect(Collectors.toList())); // yeah
    
    private enum StringState {
        NONE,
        SINGLE,
        DOUBLE
    }
    
    /**
     * Tokenizes a set of lines. The line counter matches the indicies of the lines
     * 
     * @param lines
     */
    public static List<Token> tokenize(List<String> lines) {
        ArrayList<Token> tokens = new ArrayList<>(lines.size() * 2);
        
        /*
         * Go line by line, character by character, building words. When whitespace or special characters
         * are found, end the word and make it a NameToken or NumberToken. When a special character is encountered, make
         * it its respective Token or handle it appropriately. When whitespace is encountered, make it a
         * WhitespaceToken or ignore it. For each line, start with a LineToken with the line number
         */
        int index = -1,
            lineNumber = 0,
            lineIndex = -1, // any value that'll make the first operation get a new line
            stringStartIndex = -1;
        
        String rawLine = "",
               line = "";
        
        StringBuilder currentToken = new StringBuilder();
        
        StringState stringState = StringState.NONE;
        
        while(true) {
            // next line?
            if(lineIndex >= line.length() || lineIndex < 0) {
                // do we have a token from the end of the previous line
                if(currentToken.length() > 0) {
                    // did someone forget to close their string
                    if(stringState != StringState.NONE) {
                        // warn, reset current, and try to ignore the quote
                        System.err.println(String.format("[WARN] [Tokenizer] Unclosed string on line %s: %s", lineNumber, rawLine.substring(stringStartIndex)));
                        
                        currentToken = new StringBuilder();
                        lineIndex = stringStartIndex + 1;
                        stringState = StringState.NONE;
                        
                        continue;
                    }
                    
                    tokens.add(convertToToken(currentToken, lineNumber));
                }
                
                index++;
                lineNumber++;
                
                // eof?
                if(index >= lines.size()) break;
                
                // next line
                // if the last line was empty, remove its LineToken
                if(tokens.size() > 0 && tokens.get(tokens.size() - 1) instanceof LineToken) {
                    tokens.remove(tokens.size() - 1);
                }
                
                tokens.add(new LineToken(lineNumber));
                
                currentToken = new StringBuilder();
                lineIndex = 0;
                
                rawLine = lines.get(index);
                line = rawLine.toLowerCase();
                
                if(!INCLUDE_WHITESPACE) {
                    line = line.strip();
                    rawLine = rawLine.strip(); // indicies need to match
                }
                
                if(VERBOSE) System.out.println("[INFO] [Tokenizer] tokenizing " + rawLine);
                
                // empty line?
                if(line.equals("")) continue;
            }
            
            // what we workin with
            char nextChar = line.charAt(lineIndex);
            
            if(HANDLE_STRINGS && nextChar == '"' && stringState != StringState.SINGLE) { // double quoted string
                // start/end string
                if(stringState == StringState.NONE) {
                    // end previous token
                    if(currentToken.length() > 0) {
                        tokens.add(convertToToken(currentToken, lineNumber));
                    }
                    
                    stringState = StringState.DOUBLE;
                    stringStartIndex = lineIndex;
                } else {
                    // end string
                    tokens.add(new StringToken(currentToken.toString()));
                    
                    stringState = StringState.NONE;
                }
                
                currentToken = new StringBuilder();
                lineIndex++;
            } else if(HANDLE_STRINGS && nextChar == '\'' && stringState != StringState.DOUBLE) { // single quoted string
                // start/end
                if(stringState == StringState.NONE) {
                    // end previous token
                    if(currentToken.length() > 0) {
                        tokens.add(convertToToken(currentToken, lineNumber));
                    }
                    
                    stringState = StringState.SINGLE;
                    stringStartIndex = lineIndex;
                } else {
                    // end string
                    tokens.add(new StringToken(currentToken.toString()));
                    
                    stringState = StringState.NONE;
                }
                
                currentToken = new StringBuilder();
                lineIndex++;
            } else if(stringState != StringState.NONE) {
                currentToken.append(nextChar);
                lineIndex++;
            } else if(Character.isWhitespace(nextChar)) { // whitespace
                // end previous token
                if(currentToken.length() > 0) {
                    tokens.add(convertToToken(currentToken, lineNumber));
                    currentToken = new StringBuilder();
                }
                
                // take all consecutive whitespace and put a whitespace token
                while(Character.isWhitespace(line.charAt(++lineIndex))); // preincrement go brrr
                if(INCLUDE_WHITESPACE) tokens.add(new WhitespaceToken());
            } else if(specialCharacters.contains(nextChar)) { // special characters
                // end previous token
                if(currentToken.length() > 0) {
                    tokens.add(convertToToken(currentToken, lineNumber));                    
                    currentToken = new StringBuilder();
                }
                
                // add this one
                tokens.add(new SpecialToken(nextChar));
                lineIndex++;
            } else if(nextChar == COMMENT_MARKER) {
                // end token and discard the rest of the line
                if(currentToken.length() > 0) {
                    tokens.add(convertToToken(currentToken, lineNumber));
                    currentToken = new StringBuilder();
                }
                
                if(INCLUDE_COMMENTS) {
                    tokens.add(new CommentToken(rawLine.substring(lineIndex + 1)));
                }
                
                lineIndex = -1;
            } else { // anything else
                currentToken.append(nextChar);
                lineIndex++;
            }
        }
        
        return tokens;
    }
    
    /**
     * Converts a string into a {@link NameToken} or {@link NumberToken}
     * 
     * @param word
     * @param ln line number
     * @return A {@link Token} for this string
     */
    private static Token convertToToken(String word, int ln) {
        // try to make a number
        String s = word.toLowerCase();
        if(numberPattern.matcher(s).matches()) {
            try {
                // decimal
                if(s.length() < 3) return new NumberToken(Integer.parseInt(s));
                
                // can have base identifier
                String bid = s.substring(0, 2),
                       sv = s.substring(2);
                
                return new NumberToken(switch(bid) {
                    case "0b"   -> Integer.parseInt(sv, 2);
                    case "0o"   -> Integer.parseInt(sv, 8);
                    case "0d"   -> Integer.parseInt(sv);
                    case "0x"   -> Integer.parseInt(sv, 16);
                    default     -> Integer.parseInt(s);
                });
            } catch(NumberFormatException e) {
                System.err.println(String.format("[WARN] [Tokenizer] Malformed constant on line %s: \"%s\"", ln, word));
            }
        }
        
        return new NameToken(word);
    }
    
    /**
     * Converts a string into a {@link NameToken} or {@link NumberToken}
     * 
     * @param word
     * @param ln line number
     * @return A {@link Token} for this string
     */
    private static Token convertToToken(StringBuilder word, int ln) {
        return convertToToken(word.toString(), ln);
    }
    
    /**
     * Set whether the {@code Tokenizer} includes comments as tokens
     * 
     * @param b {@code true} to include comments, {@code false} to discard. Defaults to {@code false}
     */
    public static void setIncludeComments(boolean b) {
        INCLUDE_COMMENTS = b;
    }
    
    /**
     * Set whether the {@code Tokenizer} includes whitespace as tokens
     * 
     * @param b {@code true} to include whitespace, {@code false} to discard. Defaults to {@code false}
     */
    public static void setIncludeWhitespace(boolean b) {
        INCLUDE_WHITESPACE = b;
    }
    
    /**
     * Sets whether the {@code Tokenizer} attempts to handle strings. Strings will be converted into single
     * {@link StringToken} tokens if handled, otherwise quotes are handled like any other special character.
     * 
     * @param b {@code true} to handle strings, {@code false} to not. Defaults to {@code true}
     */
    public static void setHandleStrings(boolean b) {
        HANDLE_STRINGS = b;
    }
    
    /**
     * Sets the marker the {@code Tokenizer} uses for the start of line-end comments
     * 
     * @param c Character to use. Default value {@code //}
     */
    public static void setCommentMarker(char c) {
        COMMENT_MARKER = c;
    }
    
    /**
     * Sets whether the {@code Tokenizer} uses verbose console output
     * 
     * @param b {@code true} to use verbose console output, {@code false} otherwise
     */
    public static void setVerbose(boolean b) {
        VERBOSE = b;
    }
}
