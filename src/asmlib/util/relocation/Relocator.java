package asmlib.util.relocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import asmlib.util.relocation.RelocatableObject.Endianness;

/**
 * Relocates Relocatable Object Files
 * 
 * @author Mechafinch
 */
public class Relocator {
    
    private static Logger LOG = Logger.getLogger(Relocator.class.getName());
    
    private ArrayList<RelocatableObject> objects;
    
    private HashMap<String, Integer> relocatedReferences,
                                     objectLocations;
    
    /**
     * Initializes a relocator
     */
    public Relocator() {
        this.objects = new ArrayList<>();
        this.relocatedReferences = new HashMap<>();
        this.objectLocations = new HashMap<>();
    }
    
    /**
     * Add an object
     * 
     * @param o
     */
    public void add(RelocatableObject o) {
        this.objects.add(o);
    }
    
    /**
     * Relocates all loaded objects
     * 
     * @param startPosition
     * @return
     */
    public byte[] relocate(int startPosition) {
        String libs = "";
        for(RelocatableObject obj : objects) libs += ", " + obj.name;
        
        LOG.fine("Relocating libraries: " + libs.substring(2));
        
        int index = startPosition,
            totalCodeSize = 0;
        
        // determine locations and count total length
        for(RelocatableObject obj : objects) {
            this.objectLocations.put(obj.name, index);
            
            totalCodeSize += obj.objectCodeSize;
            index += obj.objectCodeSize;
        }
        
        // copy object code
        byte[] code = new byte[totalCodeSize];
        index = 0;
        
        for(RelocatableObject obj : objects) {
            for(int i = 0; i < obj.objectCodeSize; i++) {
                code[index++] = obj.objectCode[i];
            }
        }
        
        LOG.finest("Initial Object Code:");
        for(int i = 0; i < code.length; i += 16) {
            String s = "";
            
            for(int j = 0; j < 16 && (i + j) < code.length; j++) {
                s += String.format("%02X ", code[i + j]);
                if(j % 8 == 7) s += " ";
            }
            
            LOG.finest(s);
        }
        
        // relocate outgoing references
        for(RelocatableObject obj : objects) {
            int offset = this.objectLocations.get(obj.name);
            
            for(String s : obj.outgoingReferences.keySet()) {
                this.relocatedReferences.put(obj.name + "." + s, obj.outgoingReferences.get(s) + offset);
            }
        }
        
        LOG.finer("Relocated outgoing references:");
        for(String s : this.relocatedReferences.keySet()) {
            LOG.finer(String.format("%s: %04X", s, this.relocatedReferences.get(s)));
        }
        
        // relocate incoming references
        LOG.finer("Relocating incoming references");
        for(RelocatableObject obj : objects) {
            int offset = this.objectLocations.get(obj.name);
            
            for(String s : obj.incomingReferences.keySet()) {
                LOG.finer("Relocating " + s + " in " + obj.name);
                
                for(int i : obj.incomingReferences.get(s)) {
                    int addrSize = obj.incomingReferenceWidths.get(s),
                        addr = this.relocatedReferences.get(s);
                    
                    LOG.finest(String.format("Placed %04X at %04X", addr, i + offset));
                    
                    for(int a = 0; a < addrSize; a++) {
                        byte b = (byte)((addr >> (a * 8)) & 0xFF);
                        
                        if(obj.objectEndianness == Endianness.LITTLE) {
                            code[i + a + offset - startPosition] = b;
                        } else {
                            code[i + (addrSize - a - 1) + offset - startPosition] = b;
                        }
                    }
                }
            }
        }
        
        LOG.finest("Final Object Code:");
        LOG.finest("       0  1  2  3  4  5  6  7   8  9  A  B  C  D  E  F");
        for(int i = 0; i < code.length; i += 16) {
            String s = "";
            
            for(int j = 0; j < 16 && (i + j) < code.length; j++) {
                s += String.format("%02X ", code[i + j]);
                if(j % 8 == 7) s += " ";
            }
            
            LOG.finest(String.format("%04X: %s", i + startPosition, s));
        }
        
        return code;
    }
    
    public int getReference(String name) {
        return this.relocatedReferences.get(name);
    }
}
