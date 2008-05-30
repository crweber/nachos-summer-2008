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
        
        int zeroTotal = 0;
        
        // iterate over all elements
        for (int i = 0, n = elements.size(); i < n; i++) {
            InstrumentationElement element = (InstrumentationElement)elements.get(i);
            if (element.cpuTicks != 0) {
                avgTotalTime += (element.ticksAtExit - element.ticksAtCreation);
                avgCpuTime += element.cpuTicks;
            }
            else {
                // account for the zeroes
                zeroTotal++;
            }

            // we can display info for this element
            printElementInfo(element);
        }
        
        // get the averages
        if (elements.size() > 0) {
            avgTotalTime /= (elements.size() - zeroTotal);
            avgCpuTime /= (elements.size() - zeroTotal);
        }
        
        // we can now display the information
        Debug.printf('x', "[ThreadInstrumentation] Average total time = %f\n", new Double(avgTotalTime));
        Debug.printf('x', "[ThreadInstrumentation] Average cpu time = %f\n", new Double(avgCpuTime));
    }
    
    public static void printThreadInfo(NachosThread nachosThread) {
        printGenericInfo(
                nachosThread.getName(), 
                nachosThread.getTicksAtCreation(), 
                nachosThread.getTicksAtExit(), 
                nachosThread.getCpuTicks());
    }
    
    public static void printElementInfo(InstrumentationElement element) {
        printGenericInfo(
                element.threadName,
                element.ticksAtCreation,
                element.ticksAtExit,
                element.cpuTicks);
    }
    
    public static void printGenericInfo(String name, int ticksAtCreation, int ticksAtExit, int cpuTicks) {
        StringBuffer buff = new StringBuffer("Thread [");
        buff.append(name);
        buff.append("] was created at [Ticks:");
        buff.append(ticksAtCreation);
        buff.append("], finished at [Ticks:");
        buff.append(ticksAtExit);
        buff.append("], lived for [Ticks:");
        buff.append(ticksAtExit - ticksAtCreation);
        buff.append("], and got [Ticks:");
        buff.append(cpuTicks);
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
