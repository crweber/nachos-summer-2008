/**
 * You have been hired by the CS Department to write code to help synchronize a professor and his/her
 * students during office hours. The professor, of course, wants to take a nap if no students are around
 * to ask questions; if there are students who want to ask questions, they must synchronize with each
 * other and with the professor so that (i) only one person is speaking at any one time, (ii) each student
 * question is answered by the professor, and (iii) no student asks another question before the professor
 * is done answering the previous one. You are to write four procedures: AnswerStart(), AnswerDone(),
 * QuestionStart(), and QuestionDone(). Each function should print a message, indicating the action
 * taken and the identity of the student involved.
 * The professor loops running the code: AnswerStart(); give answer; AnswerDone(). AnswerStart doesn't
 * return until a question has been asked. Each student loops running the code: QuestionStart(); ask
 * question; QuestionDone(). QuestionStart() does not return until it is the student's turn to ask a
 * question. Since professors consider it rude for a student not to wait for an answer, QuestionEnd()
 * should not return until the professor has finished answering the question.
 * Make sure your solution can be run using the command line "nachos -prof <numStudents>".
 */
public class ProfessorStudent implements Runnable {
    
    // number of students
    private int numStudents = -1;

    /**
     * Doesn't return until a question has been asked
     */
    public void AnswerStart() {
        
    } // AnswerStart
    
    public void AnswerDone() {
        
    } // AnswerDone
    
    /**
     * Gives a random answer to any question.
     */
    public String GiveAnswer() {
        // simulate some time
        Debug.print('+', "Thinking, please wait");
        for (int i = 0; i <= 5; i++)
        {
            try {
                Thread.sleep(750);
            }
            catch (Exception e) {}
            Debug.print('+', ".");
        }
        
        String answers[] = 
        {
            "42", "I'm not sure.", "Definitively.", "I do not understand your question.",
            "Please, come back on Monday.", "Sorry, I'm retiring tomorrow.", 
            "You need to restate your question.", "If I knew I would tell you.",
            "PI divided by 4.", "0,87 radians."
        };
        
        return (answers[(int)(Math.random() * (answers.length - 1))]);
        
    } // GiveAnswer
    
    /**
     * QuestionStart doesn't return until it is the student's turn to ask
     * a question
     */
    public void QuestionStart() {
        
    } // QuestionStart
    
    /**
     * Should not return
     */
    public void QuestionDone() {
        
    } // QuestionDone
    
    /**
     * Constructor.
     * 
     * @param numStudents Number of students that will be in the queue.
     */
    public void ProfessorStudent(int numStudents) {
        this.numStudents = numStudents;
        
    } // ctor
    
    /**
     * This method will be the one executed by Nachos.
     */
    public void run() {
        // make sure we have a correct number of students
        Debug.ASSERT(numStudents >= 0, "Number of students cannot be negative!");
        
        // build a professor thread
        NachosThread professorThread = new NachosThread("Professor");
        professorThread.fork(new Professor());
        
        // and the students threads
        for (int i = 0; i < numStudents; i++) {
            NachosThread studentThread = new NachosThread("Student_" + i);
            studentThread.fork(new Student(i));
            
        } // for

    } // run
    
    /**
     * The professor loops running the code: AnswerStart(); give answer; AnswerDone(). 
     * AnswerStart doesn't return until a question has been asked. 
     */
    protected class Professor implements Runnable {
        /**
         * This method will be the one executed by Nachos.
         */
        public void run() {

        } // run
        
    } // class
    
    /**
     * Each student loops running the code: QuestionStart(); ask question; QuestionDone(). 
     * QuestionStart() does not return until it is the student's turn to ask a question.
     */
    protected class Student implements Runnable {
        private int id = -1;
        
        /**
         * Constructor.
         * 
         * @param id The id this student will have. Cannot be negative.
         */
        public Student(int id) {
            this.id = id;
            
        } // ctor
        
        /**
         * This method will be the one executed by Nachos.
         */
        public void run() {
            // make sure we have a correct id
            Debug.ASSERT(id >= 0, "Student ID cannot be negative!");
            
        } // run
        
    } // class

} // class
