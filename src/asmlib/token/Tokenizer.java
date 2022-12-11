package asmlib.token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
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
    
    private static Logger LOG = Logger.getLogger(Tokenizer.class.getName());
    
    private static boolean INCLUDE_COMMENTS = false,
                           INCLUDE_WHITESPACE = false,
                           HANDLE_STRINGS = true,
                           HANDLE_DEFINITIONS = true;
    
    private static char COMMENT_MARKER = ';';
    
    private static String DEFINITION_MARKER = "%define";
    
    // pattern for detecting numbers
    private static Pattern numberPattern = Pattern.compile("0x[0-9a-fA-F_]*|(0d)?[0-9_]*|0o[0-7_]+|0b[01_]*");
    
    private static HashSet<Character> specialCharacters = new HashSet<Character>("()[],:+-*/<>~&|^$@".chars()
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
        LOG.fine("Begin tokenizing");
        lines = new ArrayList<>(lines); // ensure mutability
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
        
        String line = "";
        
        StringBuilder currentToken = new StringBuilder();
        
        StringState stringState = StringState.NONE;
        
        if(HANDLE_DEFINITIONS) {
            // Scan file to record definitions
            Map<String, String> defs = new HashMap<>();
            List<String> keys = new ArrayList<>();
            
            LOG.fine("Discovering definitions");
            for(int i = 0; i < lines.size(); i++) {
                String s = lines.get(i);
                
                if(s.toLowerCase().startsWith(DEFINITION_MARKER)) {
                    String[] def = s.split(" "); // no ez iterator because line numbers
                    
                    if(def.length < 3) {
                        LOG.warning(String.format("Malformed definition on line %s. Ignoring.", i + 1));
                        continue;
                    }
                    
                    // marker, key, value
                    String k = def[1],
                           v = "";
                    
                    if(keys.contains(k)) {
                        LOG.warning(String.format("Duplicate definition on line %s. Ignoring.", i + 1));
                        continue;
                    }
                    
                    for(int j = 2; j < def.length; j++) v += def[j] + " ";
                    v = v.substring(0, v.length() - 1); // trailing space
                    
                    LOG.finer("Definition recorded: " + k + " = " + v);
                    
                    defs.put(k, v);
                    keys.add(k);
                    lines.set(i, ""); // clear line in main file
                }
            }
            
            Collections.sort(keys, (a, b) -> (b.length() - a.length()));
            
            // Apply definitions to themselves
            LOG.fine("Applying nested definitions");
            boolean modified = true;
            
            while(modified) {
                // each key applied to each other until there's no change
                modified = false;
                
                for(int i = 0; i < keys.size(); i++) {
                    String k = keys.get(i),
                           v = defs.get(k);
                    
                    LOG.finer("Replacing " + k + " with " + v);
                    
                    for(int j = 0; j < keys.size(); j++) {
                        if(i == j) continue;
                        
                        String dk = keys.get(j),
                               dv = defs.get(dk);
                        
                        if(dv.contains(k)) {
                            LOG.finest("Instance on line " + (i + 1));
                            
                            modified = true;
                            defs.put(dk, dv.replace(k, v));
                        }
                    }
                }
            }
            
            // Apply definitions to the rest of the file
            for(String k : keys) {
                String v = defs.get(k);
                
                LOG.finer("Replacing " + k + " with " + v);
                
                for(int i = 0; i < lines.size(); i++) {
                    String ln = lines.get(i);
                    
                    if(ln.contains(k)) {
                        LOG.finest("Instance on line " + (i + 1));
                        
                        lines.set(i, ln.replace(k, v));
                    }
                }
            }
        }
        
        while(true) {            
            // next line?
            if(lineIndex >= line.length() || lineIndex < 0) {
                // do we have a token from the end of the previous line
                if(currentToken.length() > 0) {
                    // did someone forget to close their string
                    if(stringState != StringState.NONE) {
                        // warn, reset current, and try to ignore the quote
                        LOG.warning(String.format("Unclosed string on line %s: %s", lineNumber, line.substring(stringStartIndex)));
                        LOG.finest("Retokenizing from start of string");
                        
                        currentToken = new StringBuilder();
                        lineIndex = stringStartIndex + 1;
                        stringState = StringState.NONE;
                        
                        continue;
                    }
                    
                    tokens.add(convertToToken(currentToken, lineNumber));
                }
                
                LOG.finest("Ending line");
                
                index++;
                lineNumber++;
                
                // eof?
                if(index >= lines.size()) break;
                
                // next line
                // if the last line was empty, remove its LineToken
                if(tokens.size() > 0 && tokens.get(tokens.size() - 1) instanceof LineToken) {
                    LOG.finest("Removed empty line");
                    tokens.remove(tokens.size() - 1);
                }
                
                LOG.finest("Added line marker " + lineNumber);
                tokens.add(new LineToken(lineNumber));
                
                currentToken = new StringBuilder();
                lineIndex = 0;
                
                line = lines.get(index);
                
                if(!INCLUDE_WHITESPACE) {
                    line = line.strip();
                }
                
                LOG.finer("Tokenizing \"" + line + "\"");
                
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
                    
                    LOG.finest("Starting double-quoted string");
                    stringState = StringState.DOUBLE;
                    stringStartIndex = lineIndex;
                } else {
                    // end string
                    LOG.finest("Ending double-quoted string \"" + currentToken.toString() + "\"");
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
                    
                    LOG.finest("Starting single-quoted string");
                    stringState = StringState.SINGLE;
                    stringStartIndex = lineIndex;
                } else {
                    // end string
                    LOG.finest("Ending single-quoted string '" + currentToken.toString() + "'");
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
                    LOG.finest("Added comment token " + line.substring(lineIndex + 1));
                    tokens.add(new CommentToken(line.substring(lineIndex + 1)));
                }
                
                lineIndex = -1;
            } else { // anything else
                currentToken.append(nextChar);
                lineIndex++;
            }
        }
        
        LOG.fine("Completed successfully");
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
        String s = word.toLowerCase().replace("_", "");
        if(numberPattern.matcher(s).matches()) {
            NumberToken t;
            
            try {
                // decimal
                if(s.length() < 3) {
                    t = new NumberToken(Integer.parseInt(s));
                } else {
                    // can have base identifier
                    String bid = s.substring(0, 2),
                           sv = s.substring(2);
                    
                    t = new NumberToken(switch(bid) {
                        case "0b"   -> Long.parseLong(sv, 2);
                        case "0o"   -> Long.parseLong(sv, 8);
                        case "0d"   -> Long.parseLong(sv, 10);
                        case "0x"   -> Long.parseLong(sv, 16);
                        default     -> Long.parseLong(s);
                    });
                }
                
                LOG.finest("Converted " + word + " to number " + t.value());
                return t;
            } catch(NumberFormatException e) {
                LOG.warning(String.format("Malformed constant on line %s: \"%s\"", ln, word));
            }
        }
        
        LOG.finest("Converted " + word + " to name");
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
     * Set whether the {@link Tokenizer} includes comments as tokens
     * 
     * @param b {@code true} to include comments, {@code false} to discard. Defaults to {@code false}
     */
    public static void setIncludeComments(boolean b) {
        INCLUDE_COMMENTS = b;
    }
    
    /**
     * Set whether the {@link Tokenizer} includes whitespace as tokens
     * 
     * @param b {@code true} to include whitespace, {@code false} to discard. Defaults to {@code false}
     */
    public static void setIncludeWhitespace(boolean b) {
        INCLUDE_WHITESPACE = b;
    }
    
    /**
     * Sets whether the {@link Tokenizer} attempts to handle strings. Strings will be converted into single
     * {@link StringToken} tokens if handled, otherwise quotes are handled like any other special character.
     * 
     * @param b {@code true} to handle strings, {@code false} to not. Defaults to {@code true}
     */
    public static void setHandleStrings(boolean b) {
        HANDLE_STRINGS = b;
    }
    
    /**
     * Sets whether the {@link Tokenizer} handles definition statements. Lines beginning with the definition
     * marker (case sensitive, default value {@code %define}) will be interpreted as definitions. The
     * file is first scanned for definitions, which are first applied to each other until they don't
     * change, and then to the rest of the file. When applied, definitions are processed in order of
     * key length such that definitions with the same prefix are properly applied. 
     * 
     * @param b {@code true} to handle definitions, {@code false} to not. Defaults to {@code true}
     */
    public static void setHandleDefinitions(boolean b) {
        HANDLE_DEFINITIONS = b;
    }
    
    /**
     * Sets the marker for definition lines. Marker must not include whitespace and is not case sensitive.
     * 
     * @param s String marker to use. Default value {@code %define}
     */
    public static void setDefinitionMarker(String s) {
        DEFINITION_MARKER = s.toLowerCase();
    }
    
    /**
     * Sets the marker the {@link Tokenizer} uses for the start of line-end comments
     * 
     * @param c Character to use. Default value {@code ;}
     */
    public static void setCommentMarker(char c) {
        COMMENT_MARKER = c;
    }
}
