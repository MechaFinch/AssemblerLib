package asmlib.util.relocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
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
    
    private HashMap<String, Long> relocatedReferences,
                                  objectLocations,
                                  fixedOrigins;
    
    private TreeMap<Long, String> referenceNameMap;
    
    private boolean hasFixed = false;
    
    /**
     * Initializes a relocator
     */
    public Relocator() {
        this.objects = new ArrayList<>();
        this.relocatedReferences = new HashMap<>();
        this.objectLocations = new HashMap<>();
        this.fixedOrigins = new HashMap<>();
        this.referenceNameMap = new TreeMap<>();
    }
    
    /**
     * Add an object
     * 
     * @param o
     */
    public void add(RelocatableObject o) {
        this.objects.add(o);
        
        if(o.outgoingReferences.containsKey("ORIGIN")) {
            this.fixedOrigins.put(o.name, Integer.toUnsignedLong(o.outgoingReferences.get("ORIGIN")));
            this.hasFixed = true;
        }
    }
    
    /**
     * Relocates all loaded objects
     * 
     * @param startPosition Physical start address
     * @return
     */
    public byte[] relocate(long startPosition) {
        String libs = "";
        for(RelocatableObject obj : objects) libs += ", " + obj.name;
        
        LOG.fine("Relocating libraries: " + libs.substring(2));
        
        long index = startPosition,
             totalCodeSize = 0;
        
        // sort by object code length
        this.objects.sort((a, b) -> {
            return a.objectCodeSize - b.objectCodeSize;
        });
        
        // Stuff for placing libraries optimally
        TreeSet<Long> objectStartAddresses = new TreeSet<>();
        HashMap<Long, Long> objectEndAddresses = new HashMap<>();
        long minimumStartAddress = 0;
        
        // handle fixed locations
        for(RelocatableObject obj : objects) {
            if(this.fixedOrigins.containsKey(obj.name)) {
                long o = this.fixedOrigins.get(obj.name) - startPosition,
                     l = obj.objectCodeSize;
                
                LOG.finest(obj.name + " has fixed location " + (o + startPosition));
                
                if(o < 0l) {
                    throw new IllegalArgumentException("Fixed location " + (o + startPosition) + " is before the start position");
                }
                
                this.objectLocations.put(obj.name, o);
                
                // check overlap
                if(objectStartAddresses.size() != 0) {
                    Long closestStart = objectStartAddresses.floor(o + l - 1);
                    
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
                this.objectLocations.put(obj.name, index - startPosition);
                totalCodeSize += obj.objectCodeSize;
                index += obj.objectCodeSize;
            } else {
                if(this.objectLocations.containsKey(obj.name)) { 
                    // it's already been located by its fixed position
                    long i = this.objectLocations.get(obj.name),
                         l = obj.objectCodeSize;
                    
                    if(i + l > totalCodeSize) totalCodeSize = i + l;
                } else {
                    // i is the address we intend to start at
                    long i = minimumStartAddress,
                         l = obj.objectCodeSize,
                         lastStart = 0;
                    
                    while(true) {
                        // until the full necessary range is clear, search the address space
                        Long closestStart = objectStartAddresses.floor(i + l - 1);
                        
                        if(closestStart != null) {
                            long closestEnd = objectEndAddresses.get(closestStart);
                            
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
                    if(lastStart == 0) objectStartAddresses.add(0l);
                    
                    // extend entry
                    objectEndAddresses.put(lastStart, i + l - 1);
                    this.objectLocations.put(obj.name, i);
                    
                    if(i + l > totalCodeSize) totalCodeSize = i + l; // code size is the end of the last segment
                }
            }
        }
        
        // copy object code
        byte[] code = new byte[(int) totalCodeSize];
        
        for(RelocatableObject obj : objects) {
            index = this.objectLocations.get(obj.name);
            
            LOG.finest(obj.name + " placed at " + index);
            
            for(int i = 0; i < obj.objectCodeSize; i++) {
                code[(int)(index++)] = obj.objectCode[i];
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
            long offset = this.objectLocations.get(obj.name);
            
            for(String s : obj.outgoingReferences.keySet()) {
                long addr = obj.outgoingReferences.get(s) + offset + startPosition;
                String dName = obj.name + "." + s;
                this.relocatedReferences.put(dName, addr);
                this.referenceNameMap.put(addr, dName);
            }
        }
        
        LOG.finer("Relocated outgoing references:");
        for(String s : this.relocatedReferences.keySet()) {
            LOG.finer(String.format("%s: %08X", s, this.relocatedReferences.get(s)));
        }
        
        // relocate incoming references
        LOG.finer("Relocating incoming references");
        for(RelocatableObject obj : objects) {
            long offset = this.objectLocations.get(obj.name);
            
            for(String s : obj.incomingReferences.keySet()) {
                LOG.finer("Relocating " + s + " in " + obj.name);
                
                long addrSize = obj.incomingReferenceWidths.get(s),
                     addr;
                
                try {
                    addr = this.relocatedReferences.get(s);
                } catch(NullPointerException e) {
                    LOG.severe("Reference not found: " + s + " in " + obj.name);
                    throw e;
                }
                
                for(int i : obj.incomingReferences.get(s)) {
                    LOG.finest(String.format("Placed %08X at %08X", addr, i + offset + startPosition));
                    
                    for(int a = 0; a < addrSize; a++) {
                        byte b = (byte)((addr >> (a * 8)) & 0xFF);
                        
                        if(obj.objectEndianness == Endianness.LITTLE) {
                            code[(int)(i + a + offset)] = b;
                        } else {
                            code[(int)(i + (addrSize - a - 1) + offset)] = b;
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
    public long getReference(String name) {
        return this.relocatedReferences.get(name);
    }
    
    /**
     * Returns true if the reference exists
     * 
     * @param name Reference to check
     * @param fullname true = name includes object name, false = name does not include object name
     * @return
     */
    public boolean hasReference(String name, boolean fullname) {
        if(fullname) {
            String objName = name.substring(0, name.indexOf('.')),
                   refName = name.substring(name.indexOf('.'));
            
            for(RelocatableObject ro : this.objects) {
                if(ro.getName().equals(objName) && ro.outgoingReferences.containsKey(refName)) return true;
            }
            
            return false;
        } else {
            for(RelocatableObject ro : this.objects) {
                if(ro.outgoingReferences.containsKey(name)) return true;
            }
            
            return false;
        }
    }
    
    /**
     * Gets the name of the given address if it has one, otherwise an empty string
     * 
     * @return
     */
    public String getAddressName(long addr) {
        return this.referenceNameMap.getOrDefault(addr, "");
    }
    
    /**
     * Gets the name of the symbol nearest to the given address
     * 
     * @param addr
     * @return
     */
    public String getNearest(long addr) { 
        Long above = this.referenceNameMap.ceilingKey(addr),
             below = this.referenceNameMap.floorKey(addr);
        
        if(above != null && below != null) {
            if((above - addr) < (addr - below)) {
                return getAddressName(above);
            } else {
                return getAddressName(below);
            }
        } else if(above != null) {
            return getAddressName(above);
        } else if(below != null) {
            return getAddressName(below);
        } else {
            return "";
        }
    }
    
    /**
     * Gets the name of the symbol nearest to the given address, whose address is greater than or equal to addr
     * 
     * @param addr
     * @return
     */
    public String getNearestAbove(long addr) {
        Long above = this.referenceNameMap.ceilingKey(addr);
        
        if(above != null) {
            return getAddressName(above);
        } else {
            return "";
        }
    }
    
    /**
     * Gets the name of the symbol nearest to the given address, whose address is less than or equal to addr
     * @param addr
     * @return
     */
    public String getNearestBelow(long addr) {
        Long below = this.referenceNameMap.floorKey(addr);
        
        if(below != null) {
            return getAddressName(below);
        } else {
            return "";
        }
    }
}
