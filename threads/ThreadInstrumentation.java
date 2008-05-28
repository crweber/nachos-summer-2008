import java.util.List;
import java.util.ArrayList;

/**
 * Class that helps on thread instrumentation
 * 
 * @author luis
 *
 */
public class ThreadInstrumentation {

    private static List elements = new ArrayList();
    
    public static void addElement(NachosThread nachosThread) {
        addElement(new InstrumentationElement(nachosThread));
    }
    
    public static void addElement(InstrumentationElement element) {
        elements.add(element);
    }
    
    public static void displayInformation() {
        // first, compute the average times
        double avgTotalTime = 0;
        double avgCpuTime = 0;
        
        // iterate over all elements
        for (int i = 0, n = elements.size(); i < n; i++) {
            InstrumentationElement element = (InstrumentationElement)elements.get(i);
            avgTotalTime += (element.ticksAtExit - element.ticksAtCreation);
            avgCpuTime += element.cpuTicks;
        }
        
        // get the averages
        if (elements.size() > 0) {
            avgTotalTime /= elements.size();
            avgCpuTime /= elements.size();
        }
        
        // we can now display the information
        Debug.printf('x', "[ThreadInstrumentation] Average total time = %f", new Double(avgTotalTime));
        Debug.printf('x', "[ThreadInstrumentation] Average cpu time = %f", new Double(avgCpuTime));
    }
    
    public static void printThreadInfo(NachosThread nachosThread) {
        StringBuffer buff = new StringBuffer("Thread [");
        buff.append(nachosThread.getName());
        buff.append("] was created at [Ticks:");
        buff.append(nachosThread.getTicksAtCreation());
        buff.append("], finished at [Ticks:");
        buff.append(nachosThread.getTicksAtExit());
        buff.append("], and got [Ticks:");
        buff.append(nachosThread.getCpuTicks());
        buff.append("] active ticks on the cpu.");
        Debug.println('x', buff.toString());
    }
    
    public static class InstrumentationElement {
        private String threadName;
        private int ticksAtCreation;
        private int ticksAtExit;
        private int cpuTicks;
        
        public InstrumentationElement(NachosThread nachosThread) {
            this.ticksAtCreation = nachosThread.getTicksAtCreation();
            this.ticksAtExit = nachosThread.getTicksAtExit();
            this.cpuTicks = nachosThread.getCpuTicks();
            this.threadName = nachosThread.getName();
        }
    }
}
