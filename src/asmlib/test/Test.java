package asmlib.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import asmlib.lex.Lexer;
import asmlib.lex.symbols.Symbol;
import asmlib.token.Tokenizer;
import asmlib.token.tokens.Token;

/**
 * A place to test
 * 
 * @author Mechafinch
 */
public class Test {
    public static void main(String[] args) throws IOException {
        String[] inputFiles = {
                "C:\\Users\\wetca\\Desktop\\silly  code\\architecture\\NotSoTiny\\programming\\bcd_integer.asm",
                "C:\\Users\\wetca\\Desktop\\silly  code\\architecture\\NotSoTiny\\programming\\display.asm"
        };
        
        String[] names = {
                "bcd.",
                "display."
        };
        
        ArrayList<Token> tokens = new ArrayList<>();
        ArrayList<Symbol> symbols = new ArrayList<>();
        
        String file3 = "C:\\java\\eclipse-workspace\\AssemblerLib\\src\\asmlib\\test\\test_reserved_words.txt";
        Lexer lexer = new Lexer(new File(file3), "gaming.", false);
        
        Tokenizer.setIncludeComments(false);
        Tokenizer.setIncludeWhitespace(false);
        
        for(int i = 0; i < inputFiles.length; i++) {
            try(BufferedReader br = new BufferedReader(new FileReader(inputFiles[i]))) {
                lexer.setLabelPrefix(names[i]);
                
                tokens = new ArrayList<>(Tokenizer.tokenize(br.lines().toList()));
                symbols.addAll(lexer.lex(tokens));
            }
        }
        
        symbols.forEach(System.out::println);
    }
}
