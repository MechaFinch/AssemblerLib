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
     * @param startInMemory Start address as used in relocation
     * @param startInArray Start index in the given array
     * @return Address of the entry symbol
     */
    public static int loadRelocator(Relocator rel, String entry, byte[] mem, int startInMemory, int startInArray) {
        byte[] relocatedCode = rel.relocate(startInMemory);
        
        for(int i = 0; i < relocatedCode.length; i++) {
            mem[startInArray + i] = relocatedCode[i];
        }
        
        return rel.getReference(entry);
    }
    
    /**
     * Loads the contents of an exec file into a Relocator
     * 
     * @param f
     * @return Relocator in index 0, entry symbol in index 1
     * @throws IOException
     */
    public static List<Object> loadExecFileToRelocator(File f) throws IOException {
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
        
        return List.of(rel, entryName);
    }
    
    /**
     * Loads the contents of an "exec" file into an array
     * 
     * @param f File
     * @param mem Memory
     * @param startInMemory Start address as used in relocation
     * @param startInArray Start index in the given array
     * @return Entry symbol address
     * @throws IOException
     */
    public static int loadExecFileToArray(File f, byte[] mem, int startInMemory, int startInArray) throws IOException {
        List<Object> pair = loadExecFileToRelocator(f);
        
        return loadRelocator((Relocator) pair.get(0), (String) pair.get(1), mem, startInMemory, startInArray);
    }
}
