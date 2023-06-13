package asmlib.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A utility for locating files.
 * The object tracks a number of files, those that have been consumed and those that have not.
 * 
 * @author Mechafinch
 */
public class FileLocator {
    
    private Path workingDirectory,
                 libraryDirectory;
    
    private List<Path> knownFiles;
    
    private Deque<Path> unconsumedFiles;
    
    private boolean hasStandard;
    
    List<String> extensions;
    
    /**
     * Full constructor
     * 
     * @param workingDirectory Directory of the current file
     * @param libraryDirectory Directory of the standard library
     * @param extensions List of extensions to add to files without extensions. These should contain the . 
     */
    public FileLocator(Path workingDirectory, Path libraryDirectory, List<String> extensions) {
        this.workingDirectory = workingDirectory;
        this.libraryDirectory = libraryDirectory.toAbsolutePath();
        this.extensions = extensions;
        
        this.unconsumedFiles = new ArrayDeque<>();
        this.knownFiles = new ArrayList<>();
        this.hasStandard = true;
    }
    
    /**
     * Constructor without standard library path
     * 
     * @param workingDirectory
     * @param extensions
     */
    public FileLocator(Path workingDirectory, List<String> extensions) {
        this(workingDirectory, null, extensions);
        this.hasStandard = false;
    }
    
    /**
     * Sets the current working directory.
     * 
     * @param workingDir
     */
    public void setWorkingDirectory(Path workingDir) {
        if(Files.isDirectory(workingDir)) {
            this.workingDirectory = workingDir;
        } else {
            this.workingDirectory = workingDir.getParent();
        }
    }
    
    /**
     * Returns the full file associated with the given file
     * 
     * @param file
     * @return full file or null
     */
    public Path getSourceFile(Path file) {
        Path p;
        
        if(file.getFileName().toString().contains(".")) {
            // file has an extension, search
            if(file.isAbsolute()) return file;
            if(Files.exists(p = this.workingDirectory.resolve(file))) return p;
            if(this.hasStandard && Files.exists(p = this.libraryDirectory.resolve(file))) return p;
        } else {
            // file does not have an extension. do partial searches with each extension
            // working
            for(String extension : this.extensions) {
                Path p2 = file.resolveSibling(file.getFileName() + extension);
                if(Files.exists(p = this.workingDirectory.resolve(p2))) return p;
            }
            
            // library
            if(this.hasStandard) {
                for(String extension : this.extensions) {
                    Path p2 = file.resolveSibling(file.getFileName() + extension);
                    if(Files.exists(p = this.libraryDirectory.resolve(p2))) return p;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Adds a file to the list, if it is not present
     * 
     * @param file
     * @return true if the file was found, false if it was not found
     */
    public boolean addFile(Path file) {
        if(file.getFileName().toString().contains(".")) {
            // file has an extension, do full search
            if(this.hasFile(file)) return true;
            else if(file.isAbsolute()) {
                this.knownFiles.add(file);
                this.unconsumedFiles.add(file);
                return true;
            }
            
            return this.searchFile(file, true, true);
        } else {
            // file does not have an extension. do partial searches with each extension
            // working
            for(String extension : this.extensions) {
                Path p = file.resolveSibling(file.getFileName() + extension);
                if(this.searchFile(p, true, false)) return true;
            }
            
            // library
            if(this.hasStandard) {
                for(String extension : this.extensions) {
                    Path p = file.resolveSibling(file.getFileName() + extension);
                    if(this.searchFile(p, false, true)) return true;
                }
            }
            
            return false;
        }
    }
    
    /**
     * Determines if the file is known
     *  
     * @param file
     * @return
     */
    private boolean hasFile(Path file) {
        for(Path p : knownFiles) {
            if(p.endsWith(file)) return true;
        }
        
        return false;
    }
    
    /**
     * Searches for a file
     * 
     * @param file
     * @param working
     * @param library
     * @return
     */
    private boolean searchFile(Path file, boolean working, boolean library) {
        Path p;
        if(working) {
            if(Files.exists(p = this.workingDirectory.resolve(file))) {
                this.knownFiles.add(p);
                this.unconsumedFiles.add(p);
                return true;
            }
        }
        
        if(library && this.hasStandard) {
            if(Files.exists(p = this.libraryDirectory.resolve(file))) {
                this.knownFiles.add(p);
                this.unconsumedFiles.add(p);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * @return true if there are unconsumed files
     */
    public boolean hasUnconsumed() {
        return this.unconsumedFiles.size() != 0;
    }
    
    /**
     * Consumes a file
     * 
     * @return the file or null if there are non to consume
     */
    public Path consume() {
        return this.unconsumedFiles.poll();
    }
}
