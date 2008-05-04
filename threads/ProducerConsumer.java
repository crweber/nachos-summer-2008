/*
 * Implement producer/consumer communication through a bounded buffer, using locks and condition
 * variables.
 * The producer places characters from the string ”Hello world” into the buffer one character at a time; it
 * must wait if the buffer is full. The consumer pulls characters out of the buffer one at a time and prints
 * them to the screen; it must wait if the buffer is empty. Test your solution with a multi-character buffer
 * and with multiple producers and consumers. Of course, with multiple producers or consumers, the
 * output display will be gobbledygook. However, note that a correct solution will not produce arbitrary
 * output!
 * Make sure your solution can be run using the command line “nachos -bb <numProducers> <numConsumers>”.
 */
public class ProducerConsumer implements Runnable {

    // size of the shared buffer
    public static final int BUFFER_SIZE = 4;
    
    // shared buffer
    private static char sharedBuffer[];
    
    // variable indicating how many empty slots we have on the buffer
    public static int available = BUFFER_SIZE;
    
    // string that will be "produced"
    public static final char[] RESOURCE = "Hello world".toCharArray();
    private static int resourcePointer = 0;
    
    // number of producers, consumers
    private int numProducers = -1;
    private int numConsumers = -1;
    
    // producer and consumer pointer
    private static  int producerPointer = 0;
    private static int consumerPointer = 0;
    
    // variable to lock the state variables
    private Lock stateVariablesLock = new Lock("StateVariables");
    
    // conditions to synchronize buffer access
    private Condition consumerCondition = new Condition("ConsumerCondition");
    private Condition producerCondition = new Condition("ProducerCondition");
    
    public void run() {
        // check that the producers and consumers were properly set
        Debug.ASSERT(numProducers >= 0 && numConsumers >= 0, "Producers and Consumers cannot be negative!");
        Debug.ASSERT(sharedBuffer != null, "Shared buffer has not been initialized!");
        
        // start all the threads
        for (int i = 0; i < numProducers; i++) {
            NachosThread t = new NachosThread("Producer " + i);
            t.fork(new Producer(i));
            
        } // for
        for (int i = 0; i < numConsumers; i++) {
            NachosThread t = new NachosThread("Consumer " + i);
            t.fork(new Consumer(i));
        }

    } // run
    
    // simple class that will produce items into the buffer
    protected class Producer implements Runnable {
        // debugging index
        private int index;
        
        public Producer(int index) {
            this.index = index;
        }
        
        public void run() {
            while (true) {
                // get the lock, since we will read/modify the state variables
                stateVariablesLock.acquire();
                
                // check if we can produce
                while (available == 0) {
                    // we cannot produce, wait until someone signals us
                    producerCondition.wait(stateVariablesLock);
                }
                
                // we can produce, get one item from the resource
                char product = RESOURCE[resourcePointer];
                sharedBuffer[producerPointer] = product;
                Debug.println('+', ">>> " + index + " PRODUCED: " + product + " <<<");
                
                // modify the variables
                resourcePointer = (resourcePointer + 1) % RESOURCE.length;
                producerPointer = (producerPointer + 1) % BUFFER_SIZE;
                available--;
                
                // notify the consumers that there's something to consume
                consumerCondition.broadcast(stateVariablesLock);
                
                // release the lock
                stateVariablesLock.release();
                
                // let another thread take control
                NachosThread.thisThread().Yield();
                
            } // while
            
        } // run
        
    } // class
    
    protected class Consumer implements Runnable {
        // debugging index
        private int index;
        
        public Consumer(int index) {
            this.index = index;
        }
        
        public void run() {
            while (true) {
                // get the lock for the variables
                stateVariablesLock.acquire();
                
                // check if we can consume
                while (available == BUFFER_SIZE) {
                    // we cannot, wait until the producers signal us
                    consumerCondition.wait(stateVariablesLock);
                }
                
                // we can now consume, get an item from the buffer
                char product = sharedBuffer[consumerPointer];
                Debug.println('+', ">>>                             " + index + " CONSUMED: " + product + " <<<");
                
                // modify the variables
                consumerPointer = (consumerPointer + 1) % BUFFER_SIZE;
                available++;
                
                // notify producers
                producerCondition.broadcast(stateVariablesLock);
                
                // release the lock for the variables
                stateVariablesLock.release();
                
                // let another thread take control
                NachosThread.thisThread().Yield();
                
            } // while
            
        } // run
        
    } // class
    
    /*
     * Initializes this class with the number of producers, consumers and the buffer
     */
    public void initialize(int numProducers, int numConsumers) {
        this.numProducers = numProducers;
        this.numConsumers = numConsumers;
        sharedBuffer = new char[BUFFER_SIZE];
        Debug.printf(
            't', 
            "Starting %d producers and %d consumers working on a buffer of size %d\n", 
            new Object[] {new Long(numProducers), new Long(numConsumers), new Long(BUFFER_SIZE)});
        
    } // initialize

} // class
