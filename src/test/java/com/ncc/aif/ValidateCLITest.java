package com.ncc.aif;

import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidateCLITest {
    private static final boolean SHOW_OUTPUT = false;
    PrintStream oldOut;
    PrintStream oldErr;
    ByteArrayOutputStream baos;

    private void expect(String shouldContain, ValidateAIFCli.ReturnCode code, String... args) {
        int result = ValidateAIFCli.execute(args);
        if (SHOW_OUTPUT) {
            printOutput(args);
        }
        assertEquals(code.ordinal(), result, "Wrong error code. Should have returned " + code.ordinal());
        assertTrue(baos.toString().contains(shouldContain), "Output does not contain required string: " + shouldContain);
    }
    private void expectUsageError(String shouldContain, String... args) {
        expect(shouldContain, ValidateAIFCli.ReturnCode.USAGE_ERROR, args);
    }
    private void expectCorrect(String... args) {
        expect(ValidateAIFCli.START_MSG, ValidateAIFCli.ReturnCode.FILE_ERROR, args);
    }
    private void printOutput(String... args) {
        StringBuilder builder = new StringBuilder("Args: ");
        for (String arg : args) {
            builder.append(arg).append(" ");
        }
        builder.setLength(builder.length() - 1);
        oldOut.println(builder.toString());
        oldOut.println(baos.toString());
    }

    @BeforeAll
    void setup() {
        oldOut = System.out;
        oldErr = System.err;
    }

    @BeforeEach
    void createCLI() {
        baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        System.setOut(out);
        System.setErr(out);
    }

    @Nested
    class OntologyArguments {
        @Test
        void missingOntology() {
            expectUsageError(ValidateAIFCli.ERR_MISSING_ONT_FLAG,"--nist", "-f", "tmp.ttl");
        }
        @Test
        void tooManyOntologies() {
            expectUsageError(ValidateAIFCli.ERR_TOO_MANY_ONT_FLAGS,"--ldc", "--program", "--nist", "-f", "tmp.ttl");
        }
        @Test
        void correctLDC() {
            expectCorrect("--ldc", "-f", "tmp.ttl");
        }
        @Test
        void correctProgram() {
            expectCorrect("--program", "-f", "tmp.ttl");
        }
        @Test
        void correctCustom() {
            expectCorrect("--ont",
                    "src/main/resources/com/ncc/aif/ontologies/SeedlingOntology",
                    "src/main/resources/com/ncc/aif/ontologies/LDCOntology",
                    "-f", "tmp.ttl");
        }
    }

    @Nested
    class ThresholdArgument {
        @Test
        void thresholdTooLow() {
            expectUsageError(ValidateAIFCli.ERR_SMALLER_THAN_MIN.replaceAll("%.", ""),
                    "--ldc", "--abort", "1", "-f", "tmp.ttl");
        }
        @Test
        void correctThreshold() {
            expectCorrect("--ldc", "--abort", "4", "-f", "tmp.ttl");
        }
    }

    @Nested
    class ThreadArgument {
        @Test
        void threadsTooLow() {
            expectUsageError(ValidateAIFCli.ERR_SMALLER_THAN_MIN.replaceAll("%.", ""),
                    "--ldc", "-t", "-1", "-f", "tmp.ttl");
        }
        @Test
        void correctThread() {
            expectCorrect("--ldc", "-t", "4", "-f", "tmp.ttl");
        }

    }

    @Nested
    class FileArguments {
        @Test
        void correctMultipleFiles() {
            expectCorrect("--ldc", "-t", "4", "-f", "tmp.ttl", "another.ttl");
        }
        @Test
        void correctDirectory() {
            expectCorrect("--ldc", "-t", "4", "-d", "tmp");
        }
        @Test
        void tooManyFileArguments() {
            expectUsageError(ValidateAIFCli.ERR_TOO_MANY_FILE_FLAGS,"--ldc", "-d", "tmp", "-f", "tmp.ttl");
        }
        @Test
        void missingFileArguments() {
            expectUsageError(ValidateAIFCli.ERR_MISSING_FILE_FLAG,"--ldc");
        }
    }
}
