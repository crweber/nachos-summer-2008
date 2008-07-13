// Directory.java
//	Class to manage a directory of file names.
//
//	The directory is a table of fixed length entries; each
//	entry represents a single file, and contains the file name,
//	and the location of the file header on disk.  The fixed size
//	of each directory entry means that we have the restriction
//	of a fixed maximum size for file names.
//
//	The constructor initializes an empty directory of a certain size;
//	we use ReadFrom/WriteBack to fetch the contents of the directory
//	from disk, and to write back any modifications back to disk.
//
//	Also, this implementation has the restriction that the size
//	of the directory cannot expand.  In other words, once all the
//	entries in the directory are used, no more files can be created.
//	Fixing this is one of the parts to the assignment.
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// All rights reserved.  See the COPYRIGHT file for copyright notice and 
// limitation of liability and disclaimer of warranty provisions.

// 
//      A directory is a table of pairs: <file name, sector #>,
//	giving the name of each file in the directory, and 
//	where to find its file header (the data structure describing
//	where to find the file's data blocks) on disk.
//
//      We assume mutual exclusion is provided by the caller.
//



// The following class defines a UNIX-like "directory".  Each entry in
// the directory describes a file, and where to find it on disk.
//
// The directory data structure can be stored in memory, or on disk.
// When it is on disk, it is stored as a regular Nachos file.
//
// The constructor initializes a directory structure in memory; the
// FetchFrom/WriteBack operations shuffle the directory information
// from/to disk. 

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class Directory {
    // flat representation of a directory:
    // 0-3: number of entries in this directory
    // 4-X: entries
    // entries in this folder
    private List entries;
    
    //----------------------------------------------------------------------
    // Directory
    // 	Initialize a directory; initially, the directory is completely
    //	empty.  If the disk is being formatted, an empty directory
    //	is all we need, but otherwise, we need to call FetchFrom in order
    //	to initialize it from disk.
    //
    //	"size" is the number of entries in the directory
    //----------------------------------------------------------------------
    public Directory()
    {
        entries = new LinkedList();
    }



    //----------------------------------------------------------------------
    // fetchFrom
    // 	Read the contents of the directory from disk.
    //
    //	"file" -- file containing the directory contents
    //----------------------------------------------------------------------
    public void fetchFrom(OpenFile file)
    {
        // first read how many entries we expect
        byte buffer[] = new byte[DirectoryEntry.MAX_ENTRY_SIZE];
        int filePosition = 0;
        file.readAt(buffer, 0, 4, filePosition);
        filePosition += 4;
        int numEntries = Disk.intInt(buffer, 0);
        
        // read the entries
        for (int i = 0; i < numEntries; i++) {
            // read the size of the name in the entry, this way, we will know
            // how many more bytes we need to read
            file.readAt(buffer, 0, 4, filePosition);
            filePosition += 4;
            
            // convert to an integer
            int nameLength = Disk.intInt(buffer, 0);
            
            // read the remaining bytes (we read the name length, we need to read the sector and the name)
            // since we also read the nameLenght, we need to start at position 4 in the array
            file.readAt(buffer, 4, (4 + nameLength), filePosition);
            filePosition += (4 + nameLength);
            
            // we have the data in the array, create a directory entry
            entries.add(DirectoryEntry.createFromDiskFormat(buffer, 0));
        }
                
    }

    //----------------------------------------------------------------------
    // writeBack
    // 	Write any modifications to the directory back to disk
    //
    //	"file" -- file to contain the new directory contents
    //----------------------------------------------------------------------
    public void writeBack(OpenFile file) {
        byte buffer[] = new byte[DirectoryEntry.MAX_ENTRY_SIZE];
        int filePosition = 0;
        // start by writing how many entries we have
        Disk.extInt(entries.size(), buffer, 0);
        file.writeAt(buffer, 0, 4, filePosition);
        filePosition += 4;
        
        // write each entry
        for (Iterator it = entries.iterator(); it.hasNext(); /* empty */) {
            // convert the entry to flat format
            DirectoryEntry entry = (DirectoryEntry)it.next();
            int entrySize = entry.toDiskFormat(buffer, 0);
            
            // and write into the file
            file.writeAt(buffer, 0, entrySize, filePosition);
            filePosition += entrySize;
        }
    }


    //----------------------------------------------------------------------
    // findIndex
    // 	Look up file name in directory, and return its location in the table of
    //	directory entries.  Return null if the name isn't in the directory.
    //
    //	"name" -- the file name to look up
    //----------------------------------------------------------------------
    public DirectoryEntry findEntry(String name) {
        DirectoryEntry result = null;
        for (Iterator it = entries.iterator(); it.hasNext(); /* empty */) {
            DirectoryEntry entry = (DirectoryEntry)it.next();
            if (name.equals(entry.name)) {
                result = entry;
                break;
            }
        }
        return result;
    }


    //----------------------------------------------------------------------
    // add
    // 	Add a file into the directory.  Return TRUE if successful;
    //	return FALSE if the file name is already in the directory, or if
    //	the directory is completely full, and has no more space for
    //	additional file names.
    //
    //	"name" -- the name of the file being added
    //	"newSector" -- the disk sector containing the added file's header
    //----------------------------------------------------------------------
    public boolean add(String name, int newSector) {
        // check if the entry already exists
        if (findEntry(name) != null) {
            return false;
        }
        
        // does not exist, just add it to the entries
        DirectoryEntry newEntry = new DirectoryEntry();
        newEntry.name = name;
        newEntry.nameLen = name.length();
        newEntry.sector = newSector;
        entries.add(newEntry);

        return true;
    }

    //----------------------------------------------------------------------
    // remove
    // 	Remove a file name from the directory.  Return TRUE if successful;
    //	return FALSE if the file isn't in the directory. 
    //
    //	"name" -- the file name to be removed
    //----------------------------------------------------------------------
    public boolean remove(String name) { 
        DirectoryEntry entry = findEntry(name);

        // if we didn't find it, return false
        if (entry == null) {
            return false;
        }
        
        // we found it, remove it
        entries.remove(entry);
        return true;	
    }

    //----------------------------------------------------------------------
    // list
    // 	List all the file names in the directory. 
    //----------------------------------------------------------------------
    public void list() {
        if (!Debug.isEnabled('f')) {
            return;
        }
        Debug.println('f', "[Directory.list] Contents (name, sector) of the folder:");
        for (Iterator it = entries.iterator(); it.hasNext(); ) {
            DirectoryEntry entry = (DirectoryEntry)it.next();
            Debug.printf('f', "> (%s, %d)\n", entry.name, new Integer(entry.sector));
        }
    }

    //----------------------------------------------------------------------
    // print
    // 	List all the file names in the directory, their FileHeader locations,
    //	and the contents of each file.  For debugging.
    //----------------------------------------------------------------------
    public void print() {
        if (!Debug.isEnabled('f')) {
            return;
        }
        FileHeader hdr = new FileHeader();

        Debug.println('f', "[Directory.list] Contents (name, sector, file contents) of the folder:");
        for (Iterator it = entries.iterator(); it.hasNext(); ) {
            DirectoryEntry entry = (DirectoryEntry)it.next();
            Debug.printf('f', "> (%s, %d)\n", entry.name, new Integer(entry.sector));
            // get the header from the sector
            hdr.fetchFrom(entry.sector);
            hdr.print();
        }
        
    }


}
