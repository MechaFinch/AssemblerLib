package asmlib.util.relocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The contents of a Relocatable Object File as a class
 * 
 * @author Mechafinch
 */
public class RelocatableObject {
    
    private static Logger LOG = Logger.getLogger(RelocatableObject.class.getName());
    
    public enum Endianness { BIG, LITTLE }
    
    protected int sectionSizeWidth,
                  objectCodeSize,
                  tableCount,
                  nameLength;
    
    protected boolean loadedFromFile;
    
    protected String name;
    
    protected Endianness fileEndianness,
                         objectEndianness;
    
    protected HashMap<String, Integer> outgoingReferences,
                                       outgoingReferenceWidths,
                                       incomingReferenceWidths;
    
    protected HashMap<String, List<Integer>> incomingReferences;
    
    protected byte[] objectCode;
    
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
     */
    public RelocatableObject(Endianness objectEndianness, String name, int sectionSizeWidth, HashMap<String, List<Integer>> incomingReferences, HashMap<String, Integer> outgoingReferences, HashMap<String, Integer> incomingReferenceWidths, HashMap<String, Integer> outgoingReferenceWidths, byte[] objectCode, boolean loadedFromFile) {
        this.objectEndianness = objectEndianness;
        this.name = name;
        this.sectionSizeWidth = sectionSizeWidth;
        this.incomingReferences = incomingReferences;
        this.outgoingReferences = outgoingReferences;
        this.incomingReferenceWidths = incomingReferenceWidths;
        this.outgoingReferenceWidths = outgoingReferenceWidths;
        this.objectCode = objectCode;
        this.loadedFromFile = loadedFromFile;
        
        this.objectCodeSize = this.objectCode.length;
        this.tableCount = -1;
        this.nameLength = this.name.length();
        this.fileEndianness = Endianness.LITTLE;
    }
    
    /**
     * Reads the contents of a byte array into this object
     * 
     * @param bytes
     */
    public RelocatableObject(byte[] bytes) {
        this.loadedFromFile = false;
        
        read(bytes);
    }
    
    /**
     * Reads a file into a relocatable object
     * 
     * @param f file to read
     */
    public RelocatableObject(File f) throws IOException {
        LOG.fine("Loading relocatable object from " + f);
        
        this.loadedFromFile = true;
        
        // get the bytes of the file
        FileInputStream ins = new FileInputStream(f);
        
        read(ins.readAllBytes());
        
        ins.close();
    }
    
    /**
     * Reads the contents of a file into this object
     * 
     * @param contents
     */
    public void read(byte[] contents) {
        LOG.fine("Reading contents (" + contents.length + " bytes)");
        
        Logger logp = LOG;
        if(LOG.getLevel() == null) while((logp = logp.getParent()).getLevel() == null);
        
        boolean logFinest = logp.getLevel() == Level.FINEST,
                logFiner = logp.getLevel() == Level.FINER || logFinest;
        
        if(logFinest) {
            LOG.finest("Contents:");
            for(int i = 0; i < contents.length; i += 16) {
                String s = "";
                
                for(int j = 0; j < 16 && (i + j) < contents.length; j++) {
                    s += String.format("%02X ", contents[i + j]);
                    if(j % 8 == 7) s += " ";
                }
                
                LOG.finest(s);
            }
        }
        
        // init
        this.incomingReferences = new HashMap<>();
        this.incomingReferenceWidths = new HashMap<>();
        this.outgoingReferences = new HashMap<>();
        this.outgoingReferenceWidths = new HashMap<>();
        
        // verify magic number
        int magic = (contents[0] << 24) | (contents[1] << 16) | (contents[2] << 8) | (contents[3] << 0);
        if(magic != 0x69420413) {
            LOG.severe("Magic number check failed");
            throw new IllegalArgumentException("Failed magic number check");
        }
        
        // read header
        int fileIndex = 4;
        
        // file endianness
        if(contents[fileIndex++] == 0) {
            this.fileEndianness = Endianness.LITTLE;
        } else {
            this.fileEndianness = Endianness.BIG;
        }
        
        // object endianness
        if(contents[fileIndex++] == 0) {
            this.objectEndianness = Endianness.LITTLE;
        } else {
            this.objectEndianness = Endianness.BIG;
        }
        
        // section size
        this.sectionSizeWidth = contents[fileIndex++] & 0xFF;
        if(this.sectionSizeWidth > 4) throw new IllegalArgumentException("too large for java uwu");
        
        // object code size
        this.objectCodeSize = readInteger(contents, fileIndex, this.sectionSizeWidth);
        fileIndex += this.sectionSizeWidth;
        
        // table count
        this.tableCount = contents[fileIndex++] & 0xFF;
        
        // name length
        this.nameLength = contents[fileIndex++] & 0xFF;
        
        // name
        this.name = readString(contents, fileIndex, this.nameLength);
        fileIndex += this.nameLength;
        
        if(logFiner) { 
            LOG.finer("-- Header --");
            LOG.finer("File Endianness " + this.fileEndianness);
            LOG.finer("Object Endianness " + this.objectEndianness);
            LOG.finer("Size Width " + this.sectionSizeWidth);
            LOG.finer("Code Size " + this.objectCodeSize);
            LOG.finer("Table Count " + this.tableCount);
            LOG.finer("Name Length " + this.nameLength);
            LOG.finer("Name " + this.name);
        }
        
        // read tables
        for(int t = 0; t < this.tableCount; t++) {
            // direction
            boolean outgoing = contents[fileIndex++] == 0;
            
            // address width
            int entryWidth = contents[fileIndex++] & 0xFF;
            
            // entry count
            int entryCount = readInteger(contents, fileIndex, this.sectionSizeWidth);
            fileIndex += this.sectionSizeWidth;
            
            if(logFiner) {
                LOG.finer("-- Table --");
                LOG.finer("Direction " + (outgoing ? "outgoing" : "incoming"));
                LOG.finer("Width " + entryWidth);
                LOG.finer("Count " + entryCount);
            }
            
            // entries
            for(int e = 0; e < entryCount; e++) {
                // entry name length
                int len = contents[fileIndex++] & 0xFF;
                
                // entry name
                String name = readString(contents, fileIndex, len);
                fileIndex += len;
                
                if(outgoing) {
                    // value
                    int val = readInteger(contents, fileIndex, entryWidth);
                    fileIndex += entryWidth;
                    
                    this.outgoingReferences.put(name, val);
                    this.outgoingReferenceWidths.put(name, entryWidth);
                    
                    if(logFiner) {
                        LOG.finer("-- Outgoing Entry --");
                        LOG.finer("Name Length " + len);
                        LOG.finer("Name " + name);
                        LOG.finer("Value " + val);
                    }
                } else {
                    // substitute "this"
                    if(name.startsWith("this.")) {
                        name = this.name + name.substring(name.indexOf('.'));
                    }
                    
                    // number of values
                    int num = readInteger(contents, fileIndex, this.sectionSizeWidth);
                    fileIndex += this.sectionSizeWidth;
                    
                    ArrayList<Integer> vals = new ArrayList<>(num);
                    
                    for(int v = 0; v < num; v++) {
                        int val = readInteger(contents, fileIndex, entryWidth);
                        fileIndex += entryWidth;
                        
                        vals.add(val);
                    }
                    
                    this.incomingReferences.put(name, vals);
                    this.incomingReferenceWidths.put(name, entryWidth);
                    
                    if(logFiner) {
                        LOG.finer("-- Incoming Entry --");
                        LOG.finer("Name Length " + len);
                        LOG.finer("Name " + name);
                        LOG.finer("Value Count " + num);
                        
                        for(int i : vals) {
                            LOG.finer("Value " + i);
                        }
                    }
                }
            }
        }
        
        // object code
        this.objectCode = new byte[this.objectCodeSize];
        
        for(int i = 0; i < this.objectCodeSize; i++) {
            this.objectCode[i] = contents[fileIndex++];
        }
        
        if(logFinest) {
            LOG.finest("Object Code:");
            for(int i = 0; i < this.objectCodeSize; i += 16) {
                String s = "";
                
                for(int j = 0; j < 16 && (i + j) < this.objectCodeSize; j++) {
                    s += String.format("%02X ", this.objectCode[i + j]);
                    if(j % 8 == 7) s += " ";
                }
                
                LOG.finest(s);
            }
        }
        
        LOG.fine("Read successfully (" + this.objectCodeSize + " code bytes)");
    }
    
    /**
     * Reads data with the right endianness
     * 
     * @param data
     * @param index
     * @return
     */
    private int readInteger(byte[] data, int index, int length) {
        if(length > 4) throw new IllegalArgumentException("too large for java uwu");
        int value = 0;
        
        // loop forwards or backwards depending on endianness
        for(int i = (fileEndianness == Endianness.LITTLE ? 0 : length - 1), j = 0;
            fileEndianness == Endianness.LITTLE ? (i < length) : (i >= 0);
            i += (fileEndianness == Endianness.LITTLE ? 1 : -1), j += 8) {
            value |= (data[index + i] & 0xFF) << j;
        }
        
        return value;
    }
    
    /**
     * Reads a string
     * 
     * @param data
     * @param index
     * @param length
     * @return
     */
    private String readString(byte[] data, int index, int length) {
        String s = "";
        
        for(int i = 0; i < length; i++) {
            s += (char) (data[index + i] & 0xFF);
        }
        
        return s;
    }
    
    /**
     * Converts this object into an obj file. Current implementation assumes all references are of the same address width
     * 
     * @return This object as an .obj file
     */
    public byte[] asObjectFile() {
        LOG.fine("Converting " + this.name + " to writable object file (" + this.objectCodeSize + " code bytes)");
        LOG.finer("Calculating file size");
        
        Logger logp = LOG;
        if(LOG.getLevel() == null) while((logp = logp.getParent()).getLevel() == null);
        
        boolean logFinest = logp.getLevel() == Level.FINEST,
                logFiner = logp.getLevel() == Level.FINER || logFinest;
        
        // header size
        int totalSize = 9 + this.sectionSizeWidth + this.nameLength,
            maxTableSize = (int) Math.pow(2, this.sectionSizeWidth * 8),
            incomingTableCount = 0,
            outgoingTableCount = 0,
            incomingReferenceWidth = 0,
            outgoingReferenceWidth = 0;
        
        // how many tables do we need - are there more references than the section size width can measure        
        if(this.incomingReferences.size() >= maxTableSize) {
            incomingTableCount = (int) Math.ceil(this.incomingReferences.size() / Math.pow(2, this.sectionSizeWidth * 8));
        } else if(this.incomingReferences.size() != 0) {
            incomingTableCount = 1;
        }
        
        totalSize += incomingTableCount * (2 + this.sectionSizeWidth); // table headers
        
        if(this.outgoingReferences.size() >= maxTableSize) {
            outgoingTableCount = (int) Math.ceil(this.outgoingReferences.size() / Math.pow(2, this.sectionSizeWidth * 8));
        } else if(this.outgoingReferences.size() != 0) {
            outgoingTableCount = 1;
        }
        
        totalSize += outgoingTableCount  * (2 + this.sectionSizeWidth);
        
        // table sizes - incoming
        for(String incomingReferenceName : this.incomingReferences.keySet()) {
            List<Integer> references = this.incomingReferences.get(incomingReferenceName);
            
            incomingReferenceWidth = this.incomingReferenceWidths.get(incomingReferenceName);
            
            totalSize += 1 + incomingReferenceName.length() + this.sectionSizeWidth + (references.size() * incomingReferenceWidth);
        }
        
        // table sizes - outgoing
        for(String outgoingReferenceName : this.outgoingReferences.keySet()) {
            outgoingReferenceWidth = this.outgoingReferenceWidths.get(outgoingReferenceName);
            totalSize += 1 + outgoingReferenceName.length() + outgoingReferenceWidth;
        }
        
        LOG.finest("Relocation size is " + totalSize + " bytes");
        
        // object code
        totalSize += this.objectCodeSize;
        LOG.finest("File size is " + totalSize + " bytes");
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        
        // convert
        // -- header --
        LOG.finer("Writing header");
        
        // magic number
        LOG.finest("Output magic number 0x69420413");
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0x69420413);
        
        // endianness
        buffer.put((byte)((this.fileEndianness == Endianness.BIG) ? 1 : 0));
        buffer.put((byte)((this.objectEndianness == Endianness.BIG) ? 1 : 0));
        if(logFinest) LOG.finest(String.format("File endiannesses are %s and %s. Output %02X and %02X", this.fileEndianness, this.objectEndianness, getFrom(buffer, 2), getFrom(buffer, 1)));
        
        // sizes
        buffer.put((byte)(this.sectionSizeWidth & 0xFF));
        if(logFinest) LOG.finest(String.format("Section size width %02X", getFrom(buffer, 1)));
        
        String s = putBytes(buffer, this.sectionSizeWidth, this.objectCodeSize, logFinest);
        LOG.finest("Object code size " + s);
        
        buffer.put((byte)(incomingTableCount + outgoingTableCount));
        if(logFinest) LOG.finest(String.format("Table count %02X", getFrom(buffer, 1)));
        
        // name
        buffer.put((byte)(this.nameLength & 0xFF));
        if(logFinest) LOG.finest(String.format("Name length %02X", getFrom(buffer, 1)));
        
        buffer.put(this.name.getBytes());
        LOG.finest("Output name " + this.name);
        
        // -- outgoing references --
        LOG.finer("Writing outgoing tables");
        
        Iterator<String> referenceIterator = this.outgoingReferences.keySet().iterator();
        
        for(int t = 0; t < outgoingTableCount; t++) {
            if(logFiner) LOG.finer("Writing outgoing table " + t);
            
            // table header
            buffer.put((byte) 0);
            buffer.put((byte) outgoingReferenceWidth);
            s = putBytes(buffer, this.sectionSizeWidth, (t < outgoingTableCount - 1) ? maxTableSize : (this.outgoingReferences.size() % maxTableSize), logFinest);
            if(logFinest) LOG.finest(String.format("Table header %02X %02X %s", getFrom(buffer, this.sectionSizeWidth + 2), getFrom(buffer, this.sectionSizeWidth + 1), s));
            
            // entries
            for(int i = 0; i < maxTableSize && ((t * maxTableSize) + i) < this.outgoingReferences.size(); i++) {
                // table entry
                String entryName = referenceIterator.next();
                
                // name
                buffer.put((byte) entryName.length());
                buffer.put(entryName.getBytes());
                
                // value
                s = putBytes(buffer, outgoingReferenceWidth, this.outgoingReferences.get(entryName), logFinest);
                
                if(logFinest) LOG.finest(String.format("Table entry %02X %s %s", getFrom(buffer, outgoingReferenceWidth + entryName.length() + 1), entryName, s));
            }
        }
        
        // -- incoming references --
        LOG.finer("Writing incoming tables");
        
        referenceIterator = this.incomingReferences.keySet().iterator();
        
        for(int t = 0; t < incomingTableCount; t++) {
            if(logFiner) LOG.finer("Writing incoming table " + t);
            
            // table header
            buffer.put((byte) 1);
            buffer.put((byte) incomingReferenceWidth);
            s = putBytes(buffer, this.sectionSizeWidth, (t < incomingTableCount - 1) ? maxTableSize : (this.incomingReferences.size() % maxTableSize), logFinest);
            if(logFinest) LOG.finest(String.format("Table header %02X %02X %s", getFrom(buffer, this.sectionSizeWidth + 2), getFrom(buffer, this.sectionSizeWidth + 1), s));
            
            // entries
            for(int i = 0; i < maxTableSize && ((t * maxTableSize) + i) < this.incomingReferences.size(); i++) {
                // entry
                String entryName = referenceIterator.next();
                
                // name
                buffer.put((byte) entryName.length());
                buffer.put(entryName.getBytes());
                
                // values
                List<Integer> values = this.incomingReferences.get(entryName);
                
                s = putBytes(buffer, this.sectionSizeWidth, values.size(), logFinest);
                String s1 = putAllBytes(buffer, incomingReferenceWidth, values, logFinest);
                
                if(logFinest) LOG.finest(String.format("Table entry %02X %s %s %s", getFrom(buffer, (incomingReferenceWidth * values.size()) + entryName.length() + this.sectionSizeWidth + 1), entryName, s, s1));
            }
        }
        
        LOG.finest("Relocation data size was " + buffer.position() + " bytes");
        
        // -- object code --
        buffer.put(this.objectCode);
        LOG.finest("Wrote object code");
        
        if(logFiner) LOG.finer(String.format("Allocated %s bytes. Wrote %s bytes.", buffer.capacity(), buffer.position()));
        
        return buffer.array();
    }
    
    /**
     * Gets the byte offset bytes previous
     * 
     * @param buff
     * @param offset
     * @return
     */
    private byte getFrom(ByteBuffer buff, int offset) {
        return buff.get(buff.position() - offset);
    }
    
    /**
     * Puts num as length bytes in <file endianness> order
     * 
     * @param buff
     * @param num
     * @return logging string
     */
    private String putBytes(ByteBuffer buff, int length, int num, boolean log) {
        String logString = "";
        
        // reverse endianness
        if(this.fileEndianness == Endianness.BIG) {
            int n2 = num;
            num = 0;
            
            for(int i = 0; i < 4; i++) {
                num |= (n2 >> (32 - (8 * (i + 1)))) & 0xFF;
            }
        }
        
        for(int i = 0; i < length; i++) {
            byte b = (byte)(0xFF & (num >> (i * 8)));
            buff.put(b);
            if(log) logString += String.format("%02X", b); 
        }
        
        return logString;
    }
    
    /**
     * Puts all of nums as length bytes each in <file endianness> order
     * 
     * @param buff
     * @param length
     * @param nums
     * @return logging string
     */
    private String putAllBytes(ByteBuffer buff, int length, List<Integer> nums, boolean log) {
        String s = "";
        
        for(int i : nums ) {
            if(log) s += putBytes(buff, length, i, log) + " ";
            else putBytes(buff, length, i, log);
        }
        
        return s;
    }
    
    /**
     * @return The object's name
     */
    public String getName() { return this.name; }
    public HashMap<String, List<Integer>> getIncomingReferences() { return this.incomingReferences; };
    public HashMap<String, Integer> getOutgoingReferences() { return this.outgoingReferences; };
    public HashMap<String, Integer> getIncomingReferenceWidths() { return this.incomingReferenceWidths; };
    public HashMap<String, Integer> getOutgoingReferenceWidths() { return this.outgoingReferenceWidths; };
    public byte[] getObjectCode() { return this.objectCode; }
    
    /**
     * @return True if this object was loaded from an object file
     */
    public boolean isLoadedFromFile() { return this.loadedFromFile; }
}
