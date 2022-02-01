package asmlib.lex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import asmlib.lex.symbols.*;
import asmlib.token.tokens.*;

/**
 * A {@code Lexer} takes an ordered list of tokens and groups them into meaningful {@link Symbol}s
 * according to information from the user.
 * <p>
 * Converts {@link NameToken} into {@link Mnemonic}, {@link Register}, {@link Directive}, or {@link Size}
 * according to reserved words, or {@link Label} or {@link Name} based on context.
 * </p><p>
 * {@link SpecialToken} with parentheses and square brackets respectively are used to group {@link Expression}
 * and {@link Memory}
 * </p><p>
 * {@link StringToken}, {@link NumberToken}, {@link LineToken}, {@link CommentToken}, and {@link WhitespaceToken}
 * are converted to their respective {@link Symbol}
 * </p>
 * 
 * @author Mechafinch
 */
public class Lexer {
    
    enum Case {
        UPPERCASE,
        LOWERCASE
    }
    
    // seperators are necessary for lexing but not necessarily for parsing
    private boolean INCLUDE_SEPERATORS;
    
    private int lineNumber;
    
    private String lastOuterLabel;
    
    private HashSet<String> mnemonics,
                            registers,
                            directives,
                            sizes,
                            errors; // what types of errors occurred
    
    private LinkedList<Token> tokens;
    
    private final Case RESERVED_WORD_CASE;
    
    /**
     * Create a {@code Lexer} with the given reserved words
     * 
     * @param mnemonics instruction names
     * @param registers register names
     * @param directives directive names
     * @param sizes size markers
     * @param reservedWordCase case used by the reserved words
     * @param serperators whether to include seperators as tokens
     */
    public Lexer(Collection<String> mnemonics, Collection<String> registers, Collection<String> directives, Collection<String> sizes, Case reservedWordCase, boolean seperators) {
        this.mnemonics = new HashSet<>(mnemonics);
        this.registers = new HashSet<>(registers);
        this.directives = new HashSet<>(directives);
        this.sizes = new HashSet<>(sizes);
        
        this.RESERVED_WORD_CASE = reservedWordCase;
        this.INCLUDE_SEPERATORS = seperators;
        
        this.lineNumber = 0;
        this.lastOuterLabel = "";
        this.tokens = new LinkedList<>();
        this.errors = new HashSet<>();
        
        // a little validation
        validateReservedWords();
    }
    
    /**
     * Create a {@code Lexer} with the resreved words given in a file
     * <p>
     * The file should first have the case ("uppercase" or "lowercase"), then sections for each
     * type of reserved word marked by {@code ::mnemonics::}, {@code ::registers::}, and {@code ::directives::}
     * </p>
     * 
     * @param f File
     * @param serperators whether to include seperators as symbols
     */
    public Lexer(File f, boolean seperators) throws IOException {
        this.INCLUDE_SEPERATORS = seperators;
        
        this.mnemonics = new HashSet<>();
        this.registers = new HashSet<>();
        this.directives = new HashSet<>();
        this.sizes = new HashSet<>();
        
        // read that file
        try(BufferedReader br = new BufferedReader(new FileReader(f))) {
            // header
            this.RESERVED_WORD_CASE = switch(br.readLine().toLowerCase()) {
                case "uppercase"    -> Case.UPPERCASE;
                case "lowercase"    -> Case.LOWERCASE;
                default             -> {
                    System.err.println("[ERROR] [Lexer] Invalid case header. Reserved word file header bust be \"uppercase\" or \"lowercase\"");
                    throw new IllegalArgumentException("Invalid case header"); // we can't lex anything without this info so crash immediatelys
                }
            };
            
            enum Category {
                MNEMONICS,
                REGISTERS,
                DIRECTIVES,
                SIZES
            }
            
            // first category
            Category cat = switch(br.readLine().toLowerCase()) {
                case "::mnemonics::"    -> Category.MNEMONICS;
                case "::registers::"    -> Category.REGISTERS;
                case "::directives::"   -> Category.DIRECTIVES;
                case "::sizes::"        -> Category.SIZES;
                default                 -> {
                    System.err.println("[ERROR] [Lexer] Missing first category. Reserved word file must have a category after its header.");
                    throw new IllegalArgumentException("Missing first category");
                }
            };
            
            // the rest
            String line = "";
            while((line = br.readLine()) != null) {
                if(line.equals("::mnemonics::")) {
                    cat = Category.MNEMONICS;
                } else if(line.equals("::registers::")) {
                    cat = Category.REGISTERS;
                } else if(line.equals("::directives::")) {
                    cat = Category.DIRECTIVES;
                } else if(line.equals("::sizes::")) {
                    cat = Category.SIZES;
                } else {
                    if(this.RESERVED_WORD_CASE == Case.UPPERCASE) line = line.toUpperCase();
                    
                    switch(cat) {
                        case DIRECTIVES:
                            this.directives.add(line);
                            break;
                            
                        case MNEMONICS:
                            this.mnemonics.add(line);
                            break;
                            
                        case REGISTERS:
                            this.registers.add(line);
                            break;
                        
                        case SIZES:
                            this.sizes.add(line);
                            break;
                    }
                }
            }
        }
        
        this.lineNumber = 0;
        this.lastOuterLabel = "";
        this.tokens = new LinkedList<>();
        this.errors = new HashSet<>();
        
        validateReservedWords();
    }
    
    /**
     * issues warnings for duplicate reserved words
     */
    private void validateReservedWords() {
        this.mnemonics.forEach(s -> {
            if(this.registers.contains(s) || this.directives.contains(s) || this.sizes.contains(s)) {
                System.err.println(String.format("[WARN] [Lexer] Duplicate reserved word \"%s\" will be treated as a mnemonic due to check order", s));
            }
        });
        
        this.registers.forEach(s -> {
            if(this.directives.contains(s) || this.sizes.contains(s)) {
                System.err.println(String.format("[WARN] [Lexer] Duplicate reserved word \"%s\" will be treated as a register due to check order", s));
            }
        });
        
        this.directives.forEach(s -> {
            if(this.sizes.contains(s)) {
                System.err.println(String.format("[WARN] [Lexer] Duplicate reserved word \"%s\" will be treated as a directive due to check order", s));
            }
        });
    }
    
    /**
     * Converts an ordered list of {@link Token} into an ordered list of {@link Symbol}
     * 
     * @param ts tokens
     * @return List of symbols
     */
    public List<Symbol> lex(List<Token> ts) {
        // reset state
        this.lineNumber = 0;
        this.lastOuterLabel = "";
        this.tokens = new LinkedList<>(ts);
        
        ArrayList<Symbol> symbols = new ArrayList<>(tokens.size() / 2);
        
        // consume all tokens
        while(hasNext()) {
            Symbol s = lexNextToken();
            
            if(s != null) symbols.add(s);
        }
        
        // if we had any errors, bundle them into an exception
        if(this.errors.size() != 0) {
            String msg = "";
            
            for(String s : errors) {
                msg += s + ", ";
            }
            
            throw new IllegalArgumentException(msg.substring(0, msg.length() - 2));
        }
        
        return symbols;
    }
    
    /**
     * Lexes the next {@link Token} in the list
     * 
     * @return resulting {@link Symbol}
     */
    private Symbol lexNextToken() {
        return switch(this.tokens.poll()) {
            // direct conversions
            case StringToken stt    -> new StringSymbol(stt.str());
            case NumberToken nut    -> new Constant(nut.value());
            case CommentToken ct    -> new Comment(ct.comment());
            case WhitespaceToken wt -> new Whitespace();
            case LineToken lt       -> {
                this.lineNumber = lt.lineNumber();
                yield new LineMarker(lt.lineNumber());
            }
            
            // call respective functions
            case NameToken nat      -> lexNameToken(nat);
            case SpecialToken spt   -> lexSpecialToken(spt);
            
            // invalid
            case Token t            -> {
                System.out.println(String.format("[ERROR] [Lexer] Unknown token %s on line %s", t, this.lineNumber));
                this.errors.add("unknown tokens");
                yield null;
            }
        };
    }
    
    /**
     * Lexes a {@linkplain NameToken name token} First tries to convert reserved words, then applies
     * outer labels if prefixed with a .
     * 
     * @param nt name token
     * @return 
     */
    private Symbol lexNameToken(NameToken nt) {
        String lt = (this.RESERVED_WORD_CASE == Case.UPPERCASE) ? nt.text().toUpperCase() : nt.text().toLowerCase();
        
        // is it a reserved word
        if(this.mnemonics.contains(lt)) {
            return new Mnemonic(lt);
        } else if(this.registers.contains(lt)) {
            return new Register(lt);
        } else if(this.directives.contains(lt)) {
            return new Directive(lt);
        } else if(this.sizes.contains(lt)) {
            return new Size(lt);
        }
        
        // apply outer label
        String name = nt.text().startsWith(".") ? this.lastOuterLabel + nt.text() : nt.text();
        
        // is this a label or does it reference one
        if(hasNext() && this.tokens.peek() instanceof SpecialToken st && st.character() == ':') { // pattern matching is pretty cool
            if(!nt.text().startsWith(".")) this.lastOuterLabel = nt.text();
            
            // consume the label marker too
            this.tokens.poll();
            
            return new Label(name);
        } else {
            return new Name(name);
        }
    }
    
    /**
     * Either convert a {@link SpecialToken} directly to a {@link SpecialCharacter} or does something
     * based off what character it is 
     * 
     * @param st special token
     * @return
     */
    private Symbol lexSpecialToken(SpecialToken st) {
        return switch(st.character()) {
            // groups
            case '('    -> lexExpression();
            case '['    -> lexMemory();
            
            // conversions
            case ','    -> this.INCLUDE_SEPERATORS ? new Separator() : null;
            
            // errors
            case ')'    -> {
                System.err.println(String.format("[ERROR] [Lexer] Unmatched closing parenthesis on line %s", this.lineNumber));
                this.errors.add("unmatched parentheses");
                yield null;
            }
            case ']'    -> {
                System.err.println(String.format("[ERROR] [Lexer] Unmatched closing bracket on line %s", this.lineNumber));
                this.errors.add("unmatched brackets");
                yield null;
            }
            
            // default
            default     -> new SpecialCharacter(st.character());
        };
    }
    
    /**
     * Lexes symbols in parentheses and groups them into an {@link Expression}
     * 
     * @return
     */
    private Symbol lexExpression() {
        ArrayList<Symbol> symbols = new ArrayList<>();
        
        // lex away
        while(!(this.tokens.peek() instanceof SpecialToken st && st.character() == ')')) {
            symbols.add(lexNextToken());
        }
        
        // consume closing parentheses
        this.tokens.poll();
        
        return new Expression(symbols);
    }
    
    /**
     * Lexes symbols and groups them into a {@link Memory} as best it can
     * 
     * @return
     */
    private Symbol lexMemory() {
        ArrayList<Symbol> symbols = new ArrayList<>();
        
        // lex away
        while(!(this.tokens.peek() instanceof SpecialToken st && st.character() == ']')) {
            symbols.add(lexNextToken());
        }
        
        // consume closing bracket
        this.tokens.poll();
        
        return new Memory(symbols);
    }
    
    /**
     * Returns if there are tokens to lex
     * 
     * @return {@code true} if there are tokens to lex
     */
    private boolean hasNext() {
        return !this.tokens.isEmpty();
    }
    
    /**
     * Sets whether to include seperator commas as {@linkplain Symbol}s
     * 
     * @param b {@code true} to include seperators, {@code false} otherwise
     */
    public void setIncludeSeperators(boolean b) {
        this.INCLUDE_SEPERATORS = b;
    }
}
