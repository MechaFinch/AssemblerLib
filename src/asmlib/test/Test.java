package asmlib.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import asmlib.token.Tokenizer;
import asmlib.token.tokens.Token;

/**
 * A place to test
 * 
 * @author Mechafinch
 */
public class Test {
    public static void main(String[] args) throws IOException {
        String file = "C:\\Users\\wetca\\Desktop\\silly  code\\architecture\\NotSoTiny\\programming\\bcd_integer.asm";
        ArrayList<Token> tokens = new ArrayList<>();;
        
        Tokenizer.setIncludeComments(false);
        Tokenizer.setIncludeWhitespace(false);
        
        try(BufferedReader br = new BufferedReader(new FileReader(file))) {
            tokens = new ArrayList<>(Tokenizer.tokenize(br.lines().toList()));
        }
        
        tokens.forEach(System.out::println);
    }
}
