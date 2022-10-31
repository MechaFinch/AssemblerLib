package asmlib.util.relocation;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A RelocatableObject with information on how to correct library names
 * 
 * @author Mechafinch
 */
public class RenameableRelocatableObject extends RelocatableObject {
    
    HashMap<File, String> libaryMap;
    
    /**
     * Direct creation constructor
     * 
     * @param objectEndianness
     * @param name
     * @param sectionSizeWidth
     * @param incomingReferences
     * @param outgoingReferences
     * @param incomingReferenceWidths
     * @param outgoingReferenceWidths
     * @param objectCode
     * @param loadedFromFile
     * @param libaryMap A map from library files to the name used in the object
     */
    public RenameableRelocatableObject(Endianness objectEndianness, String name, int sectionSizeWidth, HashMap<String, List<Integer>> incomingReferences, HashMap<String, Integer> outgoingReferences, HashMap<String, Integer> incomingReferenceWidths, HashMap<String, Integer> outgoingReferenceWidths, byte[] objectCode, boolean loadedFromFile, HashMap<File, String> libraryMap) {
        super(objectEndianness, name, sectionSizeWidth, incomingReferences, outgoingReferences, incomingReferenceWidths, outgoingReferenceWidths, objectCode, loadedFromFile);
        
        this.libaryMap = libraryMap;
        
        if(this.libaryMap == null) this.libaryMap = new HashMap<>();
    }
    
    /**
     * Reads the contents of a byte array into this object
     * 
     * @param bytes
     * @param libraryMap A map from library files to the name used in the object
     */
    public RenameableRelocatableObject(byte[] bytes, HashMap<File, String> libraryMap) {
        super(bytes);
        
        this.libaryMap = libraryMap;
        
        if(this.libaryMap == null) this.libaryMap = new HashMap<>();
    }

    /**
     * Reads a file into a relocatable object
     * 
     * @param f file to read
     * @param libraryMap A map from library files to the name used in the object
     * @throws IOException
     */
    public RenameableRelocatableObject(File f, HashMap<File, String> libraryMap) throws IOException {
        super(f);
        
        this.libaryMap = libraryMap;
        
        if(this.libaryMap == null) this.libaryMap = new HashMap<>();
    }
    
    public Set<String> getOutgoingReferenceNames() { return this.outgoingReferences.keySet(); }
    
    /**
     * Renames incoming references associated with the given file
     * 
     * @param f
     * @param name
     */
    public void renameLibrary(File f, String name) {
        if(!this.libaryMap.containsKey(f)) return;
        
        String key = this.libaryMap.get(f);
        if(key.equals(name)) return; // if renaming would do nothing
        
        // copy so we can modify while iterating
        Set<String> originalKeys = new HashSet<>(this.incomingReferences.keySet());
        
        for(String s : originalKeys) {
            if(s.startsWith(key)) {
                List<Integer> refs = this.incomingReferences.remove(s);
                int len = this.incomingReferenceWidths.remove(s);
                
                s = name + s.substring(key.length());
                
                this.incomingReferences.put(s, refs);
                this.incomingReferenceWidths.put(s, len);
            }
        }
    }
    
    /**
     * Renames all references with the given name
     * 
     * @param oldName
     * @param newName
     */
    public void renameGlobal(String oldName, String newName) {
        renameMap(this.incomingReferences, oldName, newName);
        renameMap(this.incomingReferenceWidths, oldName, newName);
        
        if(oldName.startsWith(this.name)) {
            oldName = oldName.substring(this.nameLength + 1);
            newName = newName.substring(this.nameLength + 1);
            renameMap(this.outgoingReferences, oldName, newName);
            renameMap(this.outgoingReferenceWidths, oldName, newName);
        }
    }
    
    /**
     * Renames map keys
     * 
     * @param <T>
     * @param map
     * @param oldName
     * @param newName
     */
    private <T> void renameMap(Map<String, T> map, String oldName, String newName) {
        if(map.containsKey(oldName)) {
            map.put(newName, map.remove(oldName));
        }
    }
}
