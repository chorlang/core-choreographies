package extraction;

import ast.cc.interfaces.CCNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ChoreographyExtractionTest extends Assert {

    private ChoreographyExtraction np;

    @Before
    public void setUp() throws Exception {
        np = new ChoreographyExtraction();
    }

    @DataPoints
    public static Object[][] data = new Object[][]{
            {
                    "p { main {q!<e>; q?; stop}} " +
                            "| q { main {p?; p!<u>; stop}} " +
                            "| r { main {stop}}", "p.e->q; q.u->p; stop"
            }

    };

    @Theory
    public void testProject(final Object... testData) throws Exception {
        System.out.println("\n" + "Test: " + testData[0]);
        CCNode program = np.extractChoreography((String) testData[0]);
        System.out.println(program.toString());

        //assertEquals(testData[1], graph.toString());
    }
}