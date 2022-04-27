package asmlib.util.relocation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads an executable object set and returns its entry address
 * 
 * @author Mechafinch
 */
public class ExecLoader {
    
    /**
     * Loads the contents of a Relocator into an array
     * 
     * @param rel Relocator
     * @param entry Entry symbol
     * @param mem Memory
     * @param start Start address
     * @return Address of the entry symbol
     */
    public static int loadRelocator(Relocator rel, String entry, byte[] mem, int start) {
        byte[] relocatedCode = rel.relocate(start);
        
        for(int i = 0; i < relocatedCode.length; i++) {
            mem[start + i] = relocatedCode[i];
        }
        
        return rel.getReference(entry);
    }
    
    /**
     * Loads the contents of an "exec" file into an array
     * 
     * @param f File
     * @param mem Memory
     * @param start Start address
     * @return Entry symbol address
     * @throws IOException
     */
    public static int loadExecFile(File f, byte[] mem, int start) throws IOException {
        List<String> lines;
        
        try(BufferedReader br = new BufferedReader(new FileReader(f))) {
            lines = br.lines().collect(Collectors.toList());
        }
        
        String entryName = "",
               directory = f.getParent() + "\\";
        Relocator rel = new Relocator();
        
        for(String s : lines) {
            if(s.startsWith("#entry")) {
                entryName = s.split(" ")[1];
            } else {
                File f2 = new File(s);
                
                if(!f2.isAbsolute()) {
                    f2 = new File(directory + s);
                }
                
                rel.add(new RelocatableObject(f2));
            }
        }
        
        if(entryName.equals("")) {
            throw new IllegalArgumentException("missing entry symbol");
        }
        
        return loadRelocator(rel, entryName, mem, start);
    }
}
