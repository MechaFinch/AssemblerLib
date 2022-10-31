package asmlib.util.relocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.logging.Level;
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
                                     objectLocations,
                                     fixedOrigins;
    
    private boolean hasFixed = false;
    
    /**
     * Initializes a relocator
     */
    public Relocator() {
        this.objects = new ArrayList<>();
        this.relocatedReferences = new HashMap<>();
        this.objectLocations = new HashMap<>();
        this.fixedOrigins = new HashMap<>();
    }
    
    /**
     * Add an object
     * 
     * @param o
     */
    public void add(RelocatableObject o) {
        this.objects.add(o);
        
        if(o.outgoingReferences.containsKey("ORIGIN")) {
            this.fixedOrigins.put(o.name, o.outgoingReferences.get("ORIGIN"));
            this.hasFixed = true;
        }
    }
    
    /**
     * Relocates all loaded objects
     * 
     * @param startPosition Physical start address
     * @return
     */
    public byte[] relocate(int startPosition) {
        String libs = "";
        for(RelocatableObject obj : objects) libs += ", " + obj.name;
        
        LOG.fine("Relocating libraries: " + libs.substring(2));
        
        int index = startPosition,
            totalCodeSize = 0;
        
        // sort by object code length
        this.objects.sort((a, b) -> {
            return a.objectCodeSize - b.objectCodeSize;
        });
        
        // Stuff for placing libraries optimally
        TreeSet<Integer> objectStartAddresses = new TreeSet<>();
        HashMap<Integer, Integer> objectEndAddresses = new HashMap<>();
        int minimumStartAddress = 0;
        
        // handle fixed locations
        for(RelocatableObject obj : objects) {
            if(this.fixedOrigins.containsKey(obj.name)) {
                int o = this.fixedOrigins.get(obj.name) - startPosition,
                    l = obj.objectCodeSize;
                
                LOG.finest(obj.name + " has fixed location " + (o + startPosition));
                
                if(o < 0) {
                    throw new IllegalArgumentException("Fixed location " + (o + startPosition) + " is before the start position");
                }
                
                this.objectLocations.put(obj.name, o);
                
                // check overlap
                if(objectStartAddresses.size() != 0) {
                    Integer closestStart = objectStartAddresses.floor(o + l - 1);
                    
                    if(closestStart != null) {
                        if(closestStart > o || objectEndAddresses.get(closestStart) > o) {
                            throw new IllegalArgumentException("Objects with fixed origins must not overlap");
                        }
                    }
                }
                
                objectStartAddresses.add(o);
                objectEndAddresses.put(o, o + l - 1);
                
                if(o <= minimumStartAddress && (o + l) >= minimumStartAddress) {
                    minimumStartAddress = o + l;
                }
            }
        }
        
        // determine locations and count total length
        for(RelocatableObject obj : objects) {
            if(!this.hasFixed) {
                this.objectLocations.put(obj.name, index);
                totalCodeSize += obj.objectCodeSize;
                index += obj.objectCodeSize;
            } else {
                if(this.objectLocations.containsKey(obj.name)) { 
                    // it's already been located by its fixed position
                    int i = this.objectLocations.get(obj.name),
                        l = obj.objectCodeSize;
                    
                    if(i + l > totalCodeSize) totalCodeSize = i + l;
                } else {
                    // i is the address we intend to start at
                    int i = minimumStartAddress,
                        l = obj.objectCodeSize,
                        lastStart = 0;
                    
                    while(true) {
                        // until the full necessary range is clear, search the address space
                        Integer closestStart = objectStartAddresses.floor(i + l - 1);
                        
                        if(closestStart != null) {
                            int closestEnd = objectEndAddresses.get(closestStart);
                            
                            // overlap = search from end of given segment
                            if(closestStart > i || closestEnd > i) {
                                i = closestEnd + 1;
                                lastStart = closestStart;
                                continue;
                            }
                        }
                        
                        break;
                    }
                    
                    // we're either at 0 or the end of a previous segment
                    if(lastStart == 0) objectStartAddresses.add(0);
                    
                    // extend entry
                    objectEndAddresses.put(lastStart, i + l - 1);
                    this.objectLocations.put(obj.name, i);
                    
                    if(i + l > totalCodeSize) totalCodeSize = i + l; // code size is the end of the last segment
                }
            }
        }
        
        // copy object code
        byte[] code = new byte[totalCodeSize];
        
        for(RelocatableObject obj : objects) {
            index = this.objectLocations.get(obj.name);
            
            LOG.finest(obj.name + " placed at " + index);
            
            for(int i = 0; i < obj.objectCodeSize; i++) {
                code[index++] = obj.objectCode[i];
            }
        }
        
        // avoid extra work
        if(LOG.getLevel() == Level.FINEST) {
            LOG.finest("Initial Object Code:");
            for(int i = 0; i < code.length; i += 16) {
                String s = "";
                
                for(int j = 0; j < 16 && (i + j) < code.length; j++) {
                    s += String.format("%02X ", code[i + j]);
                    if(j % 8 == 7) s += " ";
                }
                
                LOG.finest(s);
            }
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
            LOG.finer(String.format("%s: %08X", s, this.relocatedReferences.get(s)));
        }
        
        // relocate incoming references
        LOG.finer("Relocating incoming references");
        for(RelocatableObject obj : objects) {
            int offset = this.objectLocations.get(obj.name);
            
            for(String s : obj.incomingReferences.keySet()) {
                LOG.finer("Relocating " + s + " in " + obj.name);
                
                int addrSize = obj.incomingReferenceWidths.get(s),
                    addr;
                
                try {
                    addr = this.relocatedReferences.get(s);
                } catch(NullPointerException e) {
                    LOG.severe("Reference not found: " + s);
                    throw e;
                }
                
                for(int i : obj.incomingReferences.get(s)) {
                    LOG.finest(String.format("Placed %08X at %08X", addr, i + offset));
                    
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
        
        if(LOG.getLevel() == Level.FINEST) {
            LOG.finest("Final Object Code:");
            LOG.finest("           0  1  2  3  4  5  6  7   8  9  A  B  C  D  E  F");
            for(int i = 0; i < code.length; i += 16) {
                String s = "";
                
                for(int j = 0; j < 16 && (i + j) < code.length; j++) {
                    s += String.format("%02X ", code[i + j]);
                    if(j % 8 == 7) s += " ";
                }
                
                LOG.finest(String.format("%08X: %s", i + startPosition, s));
            }
        }
        
        LOG.fine("Final code size " + code.length + " bytes");
        
        return code;
    }
    
    /**
     * Gets the address of a symbol as of the last relocation
     * 
     * @param name
     * @return
     */
    public int getReference(String name) {
        return this.relocatedReferences.get(name);
    }
    
    /**
     * Gets the name of the given address if it has one
     * 
     * @return
     */
    public String getAddressName(int addr) {
        if(this.relocatedReferences.containsValue(addr)) {
            for(Entry<String, Integer> entry : this.relocatedReferences.entrySet()) {
                if(entry.getValue() == addr) return entry.getKey();
            }
        }
        
        return "";
    }
}
