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
import java.util.logging.Logger;

import asmlib.lex.symbols.*;
import asmlib.token.tokens.*;

/**
 * A {@code Lexer} takes an ordered list of tokens and groups them into meaningful {@link Symbol}s
 * according to information from the user.
 * <p>
 * Converts {@link NameToken} into {@link MnemonicSymbol}, {@link RegisterSymbol}, {@link DirectiveSymbol}, or {@link SizeSymbol}
 * according to reserved words, or {@link LabelSymbol} or {@link NameSymbol} based on context.
 * </p><p>
 * {@link SpecialToken} with parentheses and square brackets respectively are used to group {@link ExpressionSymbol}
 * and {@link MemorySymbol}
 * </p><p>
 * {@link StringToken}, {@link NumberToken}, {@link LineToken}, {@link CommentToken}, and {@link WhitespaceToken}
 * are converted to their respective {@link Symbol}
 * </p>
 * 
 * @author Mechafinch
 */
public class Lexer {
    
    private static Logger LOG = Logger.getLogger(Lexer.class.getName());
    
    // seperators are necessary for lexing but not necessarily for parsing
    private boolean INCLUDE_SEPERATORS;
    
    private int lineNumber;
    
    private String lastOuterLabel,
                   labelPrefix;
    
    private HashSet<String> mnemonics,
                            registers,
                            directives,
                            sizes,
                            expressive,
                            errors; // what types of errors occurred
    
    private LinkedList<Token> tokens;
    
    /**
     * Create a {@code Lexer} with the given reserved words
     * 
     * @param mnemonics instruction names
     * @param registers register names
     * @param directives directive names
     * @param sizes size markers
     * @param expressive reserved words which are allowed to be part of an {@link ExpressionSymbol}
     * @param reservedWordCase case used by the reserved words
     * @param labelPrefix string to prefix all labels with
     * @param serperators whether to include seperators as tokens
     */
    public Lexer(Collection<String> mnemonics, Collection<String> registers, Collection<String> directives, Collection<String> sizes, Collection<String> expressive, String labelPrefix, boolean seperators) {
        this.mnemonics = new HashSet<>(mnemonics);
        this.registers = new HashSet<>(registers);
        this.directives = new HashSet<>(directives);
        this.sizes = new HashSet<>(sizes);
        this.expressive = new HashSet<>(expressive);
        
        this.labelPrefix = labelPrefix;
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
     * The file should have sections for each type of reserved word marked by {@code ::mnemonics::},
     * {@code ::registers::}, and {@code ::directives::}
     * </p>
     * 
     * @param f File
     * @param labelPrefix string to prefix all labels in
     * @param serperators whether to include seperators as symbols
     */
    public Lexer(File f, String labelPrefix, boolean seperators) throws IOException {
        this.labelPrefix = labelPrefix;
        this.INCLUDE_SEPERATORS = seperators;
        
        this.mnemonics = new HashSet<>();
        this.registers = new HashSet<>();
        this.directives = new HashSet<>();
        this.sizes = new HashSet<>();
        this.expressive = new HashSet<>();
        
        // read that file
        try(BufferedReader br = new BufferedReader(new FileReader(f))) {
            LOG.fine("Loading reserved words from file " + f);
                        
            enum Category {
                MNEMONICS,
                REGISTERS,
                DIRECTIVES,
                SIZES,
                EXPRESSIVE
            }
            
            // first category
            Category cat = switch(br.readLine().toLowerCase()) {
                case "::mnemonics::"    -> Category.MNEMONICS;
                case "::registers::"    -> Category.REGISTERS;
                case "::directives::"   -> Category.DIRECTIVES;
                case "::sizes::"        -> Category.SIZES;
                case "::expressive::"   -> Category.EXPRESSIVE;
                default                 -> {
                    LOG.severe("Missing first category. Reserved word file must have a category after its header.");
                    throw new IllegalArgumentException("Missing first category");
                }
            };
            
            // the rest
            String line = "";
            while((line = br.readLine()) != null) {
                LOG.finest(line);
                if(line.equals("::mnemonics::")) {
                    cat = Category.MNEMONICS;
                    
                    LOG.finest("Loading mnemonics category");
                } else if(line.equals("::registers::")) {
                    cat = Category.REGISTERS;
                    
                    LOG.finest("Loading registers category");
                } else if(line.equals("::directives::")) {
                    cat = Category.DIRECTIVES;
                    
                    LOG.finest("Loading directories category");
                } else if(line.equals("::sizes::")) {
                    cat = Category.SIZES;
                    
                    LOG.finest("Loading directories category");
                } else if(line.equals("::expressive::")) {
                    cat = Category.EXPRESSIVE;
                    
                    LOG.finest("Loading expressive category");
                } else {
                    line = line.toUpperCase();
                    
                    LOG.finer("Adding " + cat + " value " + line);
                    
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
                            
                        case EXPRESSIVE:
                            this.expressive.add(line);
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
        LOG.fine("Validating reserved words");
        
        // check duplicates
        this.mnemonics.forEach(s -> {
            if(this.registers.contains(s) || this.directives.contains(s) || this.sizes.contains(s)) {
                LOG.warning(String.format("Duplicate reserved word \"%s\" will be treated as a mnemonic due to check order", s));
            }
        });
        
        this.registers.forEach(s -> {
            if(this.directives.contains(s) || this.sizes.contains(s)) {
                LOG.warning(String.format("Duplicate reserved word \"%s\" will be treated as a register due to check order", s));
            }
        });
        
        this.directives.forEach(s -> {
            if(this.sizes.contains(s)) {
                LOG.warning(String.format("Duplicate reserved word \"%s\" will be treated as a directive due to check order", s));
            }
        });
        
        // check that expressive contains only reserved words
        this.expressive.forEach(s -> {
            if(!(this.mnemonics.contains(s) || this.registers.contains(s) || this.directives.contains(s) || this.sizes.contains(s))) {
                LOG.warning(String.format("Word \"%s\" is marked as expressive but is not reserevd", s));
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
        LOG.fine("Begin lexing");
        
        // reset state
        this.lineNumber = 0;
        this.lastOuterLabel = "";
        this.tokens = new LinkedList<>(ts);
        
        ArrayList<Symbol> symbols = new ArrayList<>(tokens.size() / 2);
        
        // consume all tokens
        while(hasNext()) {
            Symbol s = lexNextToken(false);
            
            if(s != null) symbols.add(s);
        }
        
        LOG.fine("Finished consuming symbols. " + this.errors.size() + " errors encountered");
        
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
    private Symbol lexNextToken(boolean inExpression) {
        LOG.finer("Lexing token " + this.tokens.peek());
        
        Symbol s = switch(this.tokens.poll()) {
            // direct conversions
            case StringToken stt    -> new StringSymbol(stt.str());
            case NumberToken nut    -> new ConstantSymbol(nut.value());
            case CommentToken ct    -> new CommentSymbol(ct.comment());
            case WhitespaceToken wt -> new WhitespaceSymbol();
            case LineToken lt       -> {
                this.lineNumber = lt.lineNumber();
                yield new LineMarkerSymbol(lt.lineNumber());
            }
            
            // call respective functions
            case NameToken nat      -> lexNameToken(nat);
            case SpecialToken spt   -> lexSpecialToken(spt);
            
            // invalid
            case Token t            -> {
                LOG.severe(String.format("Unknown token %s on line %s", t, this.lineNumber));
                this.errors.add("unknown tokens");
                yield null;
            }
        };
        
        if(s == null) return null;
        
        // if we find a symbol that might be part of an expression, start one
        if(!inExpression) {
            boolean isExpressive = switch(s) {
                case ExpressionSymbol e -> true;
                case ConstantSymbol c   -> true;
                case NameSymbol n       -> true;
                case StringSymbol ss    -> true;
                
                case MnemonicSymbol m   -> this.expressive.contains(m.name());
                case RegisterSymbol r   -> this.expressive.contains(r.name());
                case DirectiveSymbol d  -> this.expressive.contains(d.name());
                case SizeSymbol s2      -> this.expressive.contains(s2.name());
                
                default                 -> false;
            };
            
            if(isExpressive) {
                ExpressionSymbol e = lexExpression(s, false);
                
                // don't wrap single symbols
                if(e.symbols().size() == 1) return e.symbols().get(0);
                
                return e;
            }
        }
        
        return s;
    }
    
    /**
     * Lexes a {@linkplain NameToken name token} First tries to convert reserved words, then applies
     * outer labels if prefixed with a .
     * 
     * @param nt name token
     * @return 
     */
    private Symbol lexNameToken(NameToken nt) {
        String lt = nt.text().toUpperCase();
        
        // is it a reserved word
        if(this.mnemonics.contains(lt)) {
            LOG.finest(nt + " was mnemonic");
            
            return new MnemonicSymbol(lt);
        } else if(this.registers.contains(lt)) {
            LOG.finest(nt + " was register");
            
            return new RegisterSymbol(lt);
        } else if(this.directives.contains(lt)) {
            LOG.finest(nt + " was directive");
            
            return new DirectiveSymbol(lt);
        } else if(this.sizes.contains(lt)) {
            LOG.finest(nt + " was size");
            
            return new SizeSymbol(lt);
        }
        
        // apply outer label
        String name = this.labelPrefix + (nt.text().startsWith(".") ? this.lastOuterLabel + nt.text() : nt.text());
        
        // is this a label or does it reference one
        if(hasNext() && this.tokens.peek() instanceof SpecialToken st && st.character() == ':') { // pattern matching is pretty cool
            if(!nt.text().startsWith(".")) this.lastOuterLabel = nt.text();
            
            // consume the label marker too
            this.tokens.poll();
            
            LOG.finest(nt + " was label");
            return new LabelSymbol(name);
        } else {
            return new NameSymbol(name);
        }
    }
    
    /**
     * Either convert a {@link SpecialToken} directly to a {@link SpecialCharacterSymbol} or does something
     * based off what character it is 
     * 
     * @param st special token
     * @return
     */
    private Symbol lexSpecialToken(SpecialToken st) {
        return switch(st.character()) {
            // groups
            case '('    -> lexExpression(null, true);
            case '['    -> lexMemory();
            
            // conversions
            case ','    -> this.INCLUDE_SEPERATORS ? new SeparatorSymbol() : null;
            
            // errors
            case ')'    -> {
                LOG.severe(String.format("Unmatched closing parenthesis on line %s", this.lineNumber));
                this.errors.add("unmatched parentheses");
                yield null;
            }
            case ']'    -> {
                LOG.severe(String.format("Unmatched closing bracket on line %s", this.lineNumber));
                this.errors.add("unmatched brackets");
                yield null;
            }
            
            // default
            default     -> new SpecialCharacterSymbol(st.character());
        };
    }
    
    /**
     * Lexes symbols in parentheses and groups them into an {@link ExpressionSymbol}
     * 
     * @return
     */
    private ExpressionSymbol lexExpression(Symbol firstSymbol, boolean parenthesized) {
        ArrayList<Symbol> symbols = new ArrayList<>();
        
        if(firstSymbol != null) {
            symbols.add(firstSymbol);
            LOG.finer("Lexing expression starting with " + firstSymbol);
        } else {
            LOG.finer("Lexing expression");
        }
        
        // lex away
        if(parenthesized) {
            while(hasNext() && !(this.tokens.peek() instanceof SpecialToken st && st.character() == ')')) {
                symbols.add(lexNextToken(true));
            }
            
            // consume closing parentheses
            this.tokens.poll();
        } else {
            while(hasNext() && !((this.tokens.peek() instanceof SpecialToken st && st.character() == ',') || (this.tokens.peek() instanceof LineToken))) {
                symbols.add(lexNextToken(true));
            }
        }
        
        LOG.finer("Expression finshed");
        return new ExpressionSymbol(symbols);
    }
    
    /**
     * Lexes symbols and groups them into a {@link MemorySymbol} as best it can
     * 
     * @return
     */
    private MemorySymbol lexMemory() {
        LOG.finer("Lexing memory");
        ArrayList<Symbol> symbols = new ArrayList<>();
        
        // lex away
        while(!(this.tokens.peek() instanceof SpecialToken st && st.character() == ']')) {
            symbols.add(lexNextToken(true));
        }
        
        // consume closing bracket
        this.tokens.poll();
        
        LOG.finer("Memory finished");
        return new MemorySymbol(symbols);
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
     * Sets the string to prefix labels with
     * 
     * @param s
     */
    public void setLabelPrefix(String s) {
        this.labelPrefix = s;
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
