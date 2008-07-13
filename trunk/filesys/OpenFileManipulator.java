import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This class stores all the properties for an open file. Through this class we
 * can check if the writeLock is acquired, if we can still read and if the file
 * was not deleted.
 * 
 */
class OpenFileDescriptor {

	private ArrayList openers;
	//exclusive lock for writing
	private Lock writeLock;
	//shared lock for reading
	private Semaphore readLock;
	//flag for deleted files; duplicated in FileHeader, also, for easier management
	private boolean toBeDeleted;
	//a deleted file must wait until all the reading/writing is finished
	private Lock deleteLock;
	private Condition deleteCondition;
	private int id;

	OpenFileDescriptor() {
		writeLock = new Lock("writeLock");
		// allow for 10 readers
		readLock = new Semaphore("readLock", 10);
		toBeDeleted = false;
		openers = new ArrayList();
		deleteLock = new Lock("deleteLock");
		deleteCondition = new Condition("deleteFile");
	}

	OpenFileDescriptor(OpenFile of) {
		this();
		this.id = of.getFileId();
		openers.add(of);
	}

	public void addOpenFile(OpenFile of) {
		openers.add(of);
	}

	public void removeOpenFile(OpenFile of) {
		openers.remove(of);
	}

	public int noOfOpeners() {
		return openers.size();
	}

	public void acquireWriteLock() {
		writeLock.acquire();
	}

	public boolean writeLockAcquired() {
		return writeLock.isHeldByCurrentThread();
	}

	public void acquireDeleteLock() {
		deleteLock.acquire();

	}

	public void waitDeleteLock() {

		deleteCondition.wait(deleteLock);

	}

	public void notifyDeleteLock() {
		deleteCondition.broadcast(deleteLock);
	}

	public void addReader() {
		readLock.P();
	}

	public void setToBeDeleted() {
		toBeDeleted = true;
	}

	public void releaseWriteLock() {
		writeLock.release();
	}

	public void releaseDeleteLock() {
		deleteLock.release();
	}

	public void removeReader() {
		readLock.V();
	}

	public boolean isToBeDeleted() {
		return toBeDeleted;
	}
	
	public boolean equals(Object obj) {
	    if (obj == null || !(obj instanceof OpenFileDescriptor)) {
	          return false;
	    }
	    OpenFileDescriptor fileDescriptor = (OpenFileDescriptor)obj;
	    return (this.id == fileDescriptor.id);
	}
	  
	public int hashCode() {
	    return this.id;
	}

}

/**
 * 
 * The class manages the list of open files and coordinates
 * 
 */
public class OpenFileManipulator {

	//identify the list by OpenFile and name
	private static final Map openedFiles = new HashMap();

	//add/renew an OpenFile object to the list
	public static void addOpenFile(OpenFile of) {
	    OpenFileDescriptor ofd = new OpenFileDescriptor(of);
	    openedFiles.put(of, ofd);
	}

	public static OpenFileDescriptor getOpenFile(OpenFile openFile) {
		return (OpenFileDescriptor) openedFiles.get(openFile);
	}

	public static void removeOpenFile(OpenFile openFile) {
		openedFiles.remove(openFile);
	}

}
