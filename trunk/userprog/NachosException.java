/**
 * Generic nachos exception
 * 
 * @author luis
 */
public class NachosException extends Exception {
    public NachosException(String msg) {
        super(msg);
    }
    
    public NachosException() {
        super();
    }
    
    public NachosException(Throwable t) {
        super(t);
    }
    
    public NachosException(String msg, Throwable t) {
        super(msg, t);
    }
}
