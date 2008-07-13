// DirectoryEntry.java
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// All rights reserved.  See the COPYRIGHT file for copyright notice and 
// limitation of liability and disclaimer of warranty provisions.

// The following class defines a "directory entry", representing a file
// in the directory.  Each entry gives the name of the file, and where
// the file's header is to be found on disk.
//
// Internal data structures kept public so that Directory operations can
// access them directly.

class DirectoryEntry {
    // flat representation:
    // 0 - 3:           nameLength
    // 4 - 7:           sector
    // 8 - (up to) 16:  name
    // files will have at most 9 characters
    public static final int MAX_FILE_NAME_LENGTH = 9;
    // max size of an entry
    public static final int MAX_ENTRY_SIZE = MAX_FILE_NAME_LENGTH + 4 + 4;
    // size without the name (sector + nameLen)
    public static final int METADATA_SIZE = 4 + 4;
    // in which sector we can find the file
    public int sector; 
    // how big is the name of the file 
    public int nameLen;
    // the name of the file itself
    public String name;

    public DirectoryEntry() {
        name = "";
    }

    // the following methods deal with conversion between the on-disk and
    // the in-memory representation of a DirectoryEnry.
    // Note: these methods must be modified if any instance variables 
    // are added!!

    // return size of flat (on disk) representation
    public int sizeOf() {
        // name.length + sizeof(sector) + sizeof(nameleng)
        return (name.length() + 4 + 4);
    }

    // initialize from a flat (on disk) representation
    public void fromDiskFormat(byte[] buffer, int pos) {
            nameLen = Disk.intInt(buffer, pos);
            pos += 4;
            sector = Disk.intInt(buffer, pos);
            pos += 4;
            StringBuilder builder = new StringBuilder(nameLen);
            for (int i = 0; i < nameLen; i++) {
                builder.append((char)buffer[i + pos]);
            }
            name = builder.toString();
    }

    // externalize to a flat (on disk) representation
    // returns how many bytes advanced in the buffer
    public int toDiskFormat(byte[] buffer, int pos) {
        Disk.extInt(nameLen, buffer, pos);
        pos += 4;
        Disk.extInt(sector, buffer, pos);
        pos += 4;
        byte[] nameBytes = name.getBytes();
        for (int i = 0; i < nameLen; i++) {
            buffer[pos + i] = nameBytes[i];
        }
        return (4 + 4 + nameLen);
    }
    
    // returns an entry based on a buffer containing flat (disk format) info
    public static DirectoryEntry createFromDiskFormat(byte[] buffer, int pos) {
        DirectoryEntry entry = new DirectoryEntry();
        entry.fromDiskFormat(buffer, pos);
        return entry;
    }
    
    public boolean equals(Object obj){
        if (obj == null || !(obj instanceof DirectoryEntry)) {
            return false;
        }
        DirectoryEntry entry = (DirectoryEntry)obj;
        return (entry.name.equals(this.name) && entry.sector == this.sector);
    }

}


