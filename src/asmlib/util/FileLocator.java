package asmlib.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;

/**
 * A utility for locating files.
 * The object tracks a number of files, those that have been consumed and those that have not.
 * 
 * @author Mechafinch
 */
public class FileLocator {
    
    private static Logger LOG = Logger.getLogger(FileLocator.class.getName());
    
    private Path workingDirectory,
                 libraryDirectory;
    
    private List<Path> knownFiles;
    
    private Deque<Path> unconsumedFiles;
    
    private boolean hasStandard;
    
    List<String> extensions,
                 headerExtensions;
    
    /**
     * Full constructor
     * 
     * @param workingDirectory Directory of the current file
     * @param libraryDirectory Directory of the standard library
     * @param extensions List of extensions to add to files without extensions. These should contain the .
     * @param headerExtensions List of extensions to use when looking for corresponding header files 
     */
    public FileLocator(Path workingDirectory, Path libraryDirectory, List<String> extensions, List<String> headerExtensions) {
        this.workingDirectory = workingDirectory;
        this.libraryDirectory = libraryDirectory.toAbsolutePath();
        this.extensions = extensions;
        this.headerExtensions = headerExtensions;
        
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
    public FileLocator(Path workingDirectory, List<String> extensions, List<String> headerExtensions) {
        this(workingDirectory, null, extensions, headerExtensions);
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
        
        LOG.finer(() -> "Working directory is now " + this.workingDirectory);
    }
    
    /**
     * Returns the header file associsated with the given file
     * 
     * @param file
     * @return header file or null
     */
    public Path getHeaderFile(Path file) {
        LOG.fine("Getting header for " + file);
        
        Path p;
        
        if(file.getFileName().toString().contains(".")) {
            // has extension - remove
            String fn = file.getFileName().toString();
            file = file.resolveSibling(fn.substring(fn.lastIndexOf('.')));
        }
        
        // working
        for(String extension : this.headerExtensions) {
            Path p2 = file.resolveSibling(file.getFileName() + extension);
            if(Files.exists(p = this.workingDirectory.resolve(p2))) {
                LOG.finest("Found file in working directory: " + p);
                return p;
            }
        }
        
        // library
        if(this.hasStandard) {
            for(String extension : this.headerExtensions) {
                Path p2 = file.resolveSibling(file.getFileName() + extension);
                if(Files.exists(p = this.libraryDirectory.resolve(p2))) {
                    LOG.finest("Found file in standard library: " + p);
                    return p;
                }
            }
        }
        
        LOG.fine("Could not find file.");
        return null;
    }
    
    /**
     * Returns the full file associated with the given file
     * 
     * @param file
     * @return full file or null
     */
    public Path getSourceFile(Path file) {
        LOG.fine(() -> "Getting source for " + file);
        
        Path p;
        
        if(file.getFileName().toString().contains(".")) {
            // file has an extension, search
            if(file.isAbsolute()) {
                LOG.finest("File was absolute.");
                return file;
            }
            
            if(Files.exists(p = this.workingDirectory.resolve(file))) {
                LOG.finest("Found file in working directory: " + p);
                return p;
            }
            
            if(this.hasStandard && Files.exists(p = this.libraryDirectory.resolve(file))) {
                LOG.finest("Found file in standard library: " + p);
                return p;
            }
        } else {
            // file does not have an extension. do partial searches with each extension
            // working
            for(String extension : this.extensions) {
                Path p2 = file.resolveSibling(file.getFileName() + extension);
                if(Files.exists(p = this.workingDirectory.resolve(p2))) {
                    LOG.finest("Found file in working directory: " + p);
                    return p;
                }
            }
            
            // library
            if(this.hasStandard) {
                for(String extension : this.extensions) {
                    Path p2 = file.resolveSibling(file.getFileName() + extension);
                    if(Files.exists(p = this.libraryDirectory.resolve(p2))) {
                        LOG.finest("Found file in standard library: " + p);
                        return p;
                    }
                }
            }
        }
        
        LOG.fine("Could not find file.");
        return null;
    }
    
    /**
     * Adds a file to the list, if it is not present
     * 
     * @param file
     * @return true if the file was found, false if it was not found
     */
    public boolean addFile(Path file) {
        LOG.fine(() -> "Adding file " + file);
        
        // do we already have it
        if(this.hasFile(file)) {
            LOG.finer("File already added");
            return true;
        }
        
        if(file.getFileName().toString().contains(".")) {
            // file has an extension, do full search
            if(file.isAbsolute()) {
                LOG.finer(() -> "Added absolute file " + file);
                this.knownFiles.add(file);
                this.unconsumedFiles.add(file);
                return true;
            }
            
            if(this.searchFile(file, true, true)) {
                return true;
            } else {
                LOG.fine("Could not find file.");
                return false;
            }
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
            
            LOG.fine("Could not find file.");
            return false;
        }
    }
    
    /**
     * Determines if the file is known
     *  
     * @param file
     * @return
     */
    public boolean hasFile(Path file) {
        // check extensions if not provided
        if(file.getFileName().toString().contains(".")) {
            // has extension, search
            for(Path p : knownFiles) {
                if(p.endsWith(file)) return true;
            }
            
            return false;
        } else {
            // no extension, try them
            for(String extension : this.extensions) {
                Path p = file.resolveSibling(file.getFileName() + extension);
                
                if(this.hasFile(p)) return true;
            }
            
            return false;
        }
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
                LOG.finer("Added file from working directory: " + p);
                this.knownFiles.add(p);
                this.unconsumedFiles.add(p);
                return true;
            }
        }
        
        if(library && this.hasStandard) {
            if(Files.exists(p = this.libraryDirectory.resolve(file))) {
                LOG.finer("Added file from standard library: " + p);
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
